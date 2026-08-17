package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;

/** Routes visual controls to the same canonical services used by the command surface. */
public final class CanonicalGuiActionExecutor implements GuiActionExecutor {
    private final PlayerProgressViewService playerViews;
    private final OperationPreviewService previews;
    private final OperationConfirmationService confirmations;
    private final DoctorService doctor;
    private final ConfigurationAdministrationService configuration;
    private final GuiMutationExecutor mutations;

    public CanonicalGuiActionExecutor(
            PlayerProgressViewService playerViews,
            OperationPreviewService previews,
            OperationConfirmationService confirmations,
            DoctorService doctor,
            ConfigurationAdministrationService configuration,
            GuiMutationExecutor mutations) {
        this.playerViews = Objects.requireNonNull(playerViews, "player views");
        this.previews = Objects.requireNonNull(previews, "previews");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.doctor = Objects.requireNonNull(doctor, "doctor");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.mutations = Objects.requireNonNull(mutations, "mutations");
    }

    @Override
    public CompletionStage<String> execute(PermissionSubject subject, GuiAction action) {
        return switch (action.kind()) {
            case VIEW_PROGRESS -> playerViews.view(subject, target(action)).thenApply(value ->
                    "Rank-up: " + render(value.rankUp()) + " | Prestige: " + render(value.prestige()));
            case SIMULATE_RANK_UP -> previews.simulateRankUp(subject, target(action))
                    .thenApply(CanonicalGuiActionExecutor::render);
            case SIMULATE_PRESTIGE -> previews.simulatePrestige(subject, target(action))
                    .thenApply(CanonicalGuiActionExecutor::render);
            case PREPARE_RANK_UP -> confirmations.prepareRankUp(subject, target(action))
                    .thenApply(value -> render(value.preview()) + " | Confirmation: " + value.confirmationId());
            case PREPARE_PRESTIGE -> confirmations.preparePrestige(subject, target(action))
                    .thenApply(value -> render(value.preview()) + " | Confirmation: " + value.confirmationId());
            case VIEW_DOCTOR -> doctor.inspect(subject).thenApply(value -> "Doctor: " + value.status()
                    + "; findings=" + value.findings().size());
            case VIEW_CONFIGURATION -> CompletableFuture.completedFuture(configuration.active(subject)
                    .map(value -> "Active configuration: " + value.revisionId().value())
                    .orElse("No active configuration"));
            case EDIT_CONFIGURATION, ADD_CONFIGURATION_VALUE, REMOVE_CONFIGURATION_VALUE, ADD_STAGE,
                    PREVIEW_CONFIGURATION, PREPARE_CONFIGURATION_ACKNOWLEDGEMENT,
                    CONFIRM_CONFIGURATION_ACKNOWLEDGEMENT, APPLY_CONFIGURATION, ROLLBACK_CONFIGURATION,
                    SELECT_STAGE_REMAP, REMOVE_STAGE_REMAP, DELETE_STAGE ->
                    mutations.execute(subject, action);
        };
    }

    private static UUID target(GuiAction action) {
        return action.targetPlayer().orElseThrow(() -> new AdministrationException(
                "gui.target.missing", "The server-owned GUI action has no player target.",
                "Reopen the player view to create a complete server-owned action."));
    }

    private static String render(OperationPreview preview) {
        return preview.kind() + " " + preview.stateChange() + "; blockers="
                + (preview.blockers().isEmpty() ? "none" : String.join("; ", preview.blockers()))
                + "; revision=" + preview.configRevision().value();
    }
}
