package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplyKind;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationPreview;

/** Production mutation route for server-owned visual actions. */
public final class CanonicalGuiMutationExecutor implements GuiMutationExecutor, GuiConfigurationAuthority {
    private final ConfigurationAdministrationService configuration;

    public CanonicalGuiMutationExecutor(ConfigurationAdministrationService configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public CompletionStage<String> execute(PermissionSubject subject, GuiAction action) {
        GuiMutationContext context = action.mutationContext().orElseThrow(() -> missing("mutation context"));
        return switch (action.kind()) {
            case EDIT_CONFIGURATION -> completed(configuration.editScalar(subject, draft(context), path(context),
                    value(context)).draftId(), "Draft edited");
            case ADD_CONFIGURATION_VALUE -> completed(configuration.addListValue(subject, draft(context),
                    path(context), value(context)).draftId(), "List value added");
            case REMOVE_CONFIGURATION_VALUE -> completed(configuration.removeListValue(subject, draft(context),
                    path(context), value(context)).draftId(), "List value removed");
            case ADD_STAGE -> completed(configuration.addStage(subject, draft(context),
                    action.targetStage().orElseThrow(() -> missing("stage")), value(context), context.providerId(),
                    context.externalGroup()).draftId(), "Stage added");
            case DELETE_STAGE -> completed(configuration.removeStage(subject, draft(context),
                    action.targetStage().orElseThrow(() -> missing("stage")), action.replacementStage()).draftId(),
                    "Stage removed with sealed replacement");
            case SELECT_STAGE_REMAP -> completed(configuration.selectStageRemap(subject, draft(context),
                    action.targetStage().orElseThrow(() -> missing("missing stage")),
                    action.replacementStage().orElseThrow(() -> missing("replacement stage"))).draftId(),
                    "Stage remap selected");
            case REMOVE_STAGE_REMAP -> completed(configuration.removeStageRemap(subject, draft(context),
                    action.targetStage().orElseThrow(() -> missing("missing stage"))).draftId(),
                    "Stage remap removed");
            case PREVIEW_CONFIGURATION -> configuration.preview(subject, draft(context))
                    .thenApply(CanonicalGuiMutationExecutor::render);
            case PREPARE_CONFIGURATION_ACKNOWLEDGEMENT -> {
                var prepared = configuration.prepareAcknowledgement(subject, draft(context));
                yield CompletableFuture.completedFuture("Acknowledgement " + prepared.acknowledgementId()
                        + "; findings=" + prepared.findings().size() + "; expires=" + prepared.expiresAt());
            }
            case CONFIRM_CONFIGURATION_ACKNOWLEDGEMENT -> configuration.confirmAcknowledgement(subject,
                    context.acknowledgementId().orElseThrow(() -> missing("acknowledgement")), reason(context))
                    .thenApply(revision -> "Applied revision " + revision.id().value());
            case APPLY_CONFIGURATION -> apply(subject, action, context);
            case ROLLBACK_CONFIGURATION -> rollback(subject, action, context);
            default -> throw new AdministrationException("gui.mutation.kind_invalid",
                    "The selected GUI action is not a canonical configuration mutation.",
                    "Reopen the control panel and select a current configuration action.");
        };
    }

    @Override
    public ConfigurationApplyKind draftKind(PermissionSubject subject, UUID draftId) {
        return configuration.requiredApplyKind(subject, draftId);
    }

    @Override
    public ConfigurationApplyKind acknowledgementKind(PermissionSubject subject, UUID acknowledgementId) {
        return configuration.acknowledgementApplyKind(subject, acknowledgementId);
    }

    private CompletionStage<String> apply(
            PermissionSubject subject,
            GuiAction action,
            GuiMutationContext context) {
        return switch (configuration.requiredApplyKind(subject, draft(context))) {
            case NORMAL -> configuration.applyDraft(subject, draft(context), action.expectedConfigRevision(),
                    Set.of(), reason(context)).thenApply(revision -> "Applied revision " + revision.id().value());
            case SETUP -> configuration.applySetup(subject, draft(context), Set.of(), reason(context))
                    .thenApply(revision -> "Applied setup revision " + revision.id().value());
            case ROLLBACK -> throw new AdministrationException("config.apply.kind_mismatch",
                    "Rollback draft cannot execute through a normal GUI apply action.",
                    "Reopen the draft so the server renders rollback authority.");
        };
    }

    private CompletionStage<String> rollback(
            PermissionSubject subject,
            GuiAction action,
            GuiMutationContext context) {
        if (context.draftId().isPresent()) {
            if (configuration.requiredApplyKind(subject, draft(context)) != ConfigurationApplyKind.ROLLBACK) {
                throw new AdministrationException("config.apply.kind_mismatch",
                        "Only a server-owned rollback draft can execute this action.",
                        "Reopen the exact rollback draft from configuration history.");
            }
            return configuration.applyRollback(subject, draft(context), action.expectedConfigRevision(), Set.of(),
                    reason(context)).thenApply(revision -> "Applied rollback revision " + revision.id().value());
        }
        ConfigRevisionId target = context.rollbackRevision().orElseThrow(() -> missing("rollback revision"));
        UUID draftId = configuration.beginRollback(subject, target, "gui");
        return CompletableFuture.completedFuture("Rollback draft " + draftId + " prepared from " + target.value());
    }

    private static CompletionStage<String> completed(UUID draftId, String action) {
        return CompletableFuture.completedFuture(action + " in draft " + draftId);
    }

    private static UUID draft(GuiMutationContext context) {
        return context.draftId().orElseThrow(() -> missing("draft"));
    }

    private static String path(GuiMutationContext context) {
        return context.configPath().orElseThrow(() -> missing("configuration path"));
    }

    private static String value(GuiMutationContext context) {
        return context.value().orElseThrow(() -> missing("value"));
    }

    private static String reason(GuiMutationContext context) {
        return context.reason().orElseThrow(() -> missing("reason"));
    }

    private static AdministrationException missing(String field) {
        return new AdministrationException("gui.mutation.context_missing",
                "The server-owned GUI action is missing its " + field + ".",
                "Reopen the GUI and create a complete action from current state.");
    }

    private static String render(ConfigurationPreview preview) {
        return "Draft " + preview.draftId() + " version " + preview.draftVersion() + "; hash="
                + preview.candidateHash().value() + "; validation="
                + (preview.validation().hasErrors() ? "BLOCKED" : "VALID") + "; findings="
                + preview.validation().findings().size();
    }
}
