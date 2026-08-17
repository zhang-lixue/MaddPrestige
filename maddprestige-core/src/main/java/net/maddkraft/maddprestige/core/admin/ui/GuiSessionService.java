package net.maddkraft.maddprestige.core.admin.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplyKind;

public final class GuiSessionService {
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final GuiActionExecutor executor;
    private final GuiConfigurationAuthority configurationAuthority;
    private final Duration lifetime;
    private final Clock clock;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public GuiSessionService(
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            GuiActionExecutor executor,
            GuiConfigurationAuthority configurationAuthority,
            Duration lifetime,
            Clock clock) {
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.executor = Objects.requireNonNull(executor, "action executor");
        this.configurationAuthority = Objects.requireNonNull(configurationAuthority, "configuration authority");
        this.lifetime = Objects.requireNonNull(lifetime, "session lifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("GUI session lifetime must be positive and at most thirty minutes");
        }
    }

    public GuiSessionView openPlayer(PermissionSubject subject, UUID playerId) {
        boolean self = subject.actor().uuid().filter(playerId::equals).isPresent();
        if (self) {
            subject.require(PhaseSixPermissions.USE);
        } else {
            subject.require(PhaseSixPermissions.PLAYER_VIEW);
        }
        Optional<ConfigRevisionId> revision = activeRevision.get();
        java.util.ArrayList<GuiAction> actions = new java.util.ArrayList<>();
        addIfPermitted(actions, subject, GuiActionKind.VIEW_PROGRESS, "Progress", self ? PhaseSixPermissions.USE
                : PhaseSixPermissions.PLAYER_VIEW, false, revision, playerId, Optional.empty());
        addIfPermitted(actions, subject, GuiActionKind.SIMULATE_RANK_UP, "Simulate rank-up",
                self ? PhaseSixPermissions.RANK_UP : PhaseSixPermissions.SIMULATE, false, revision, playerId,
                Optional.empty());
        addIfPermitted(actions, subject, GuiActionKind.SIMULATE_PRESTIGE, "Simulate Prestige",
                self ? PhaseSixPermissions.PRESTIGE : PhaseSixPermissions.SIMULATE, false, revision, playerId,
                Optional.empty());
        addIfPermitted(actions, subject, GuiActionKind.PREPARE_RANK_UP, "Rank-up confirmation",
                self ? PhaseSixPermissions.RANK_UP : PhaseSixPermissions.EXECUTE, false, revision, playerId,
                Optional.empty());
        addIfPermitted(actions, subject, GuiActionKind.PREPARE_PRESTIGE, "Prestige confirmation",
                self ? PhaseSixPermissions.PRESTIGE : PhaseSixPermissions.EXECUTE, false, revision, playerId,
                Optional.empty());
        return store(subject, GuiAudience.PLAYER, "MaddPrestige Progress", actions);
    }

    public GuiSessionView openStaff(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        java.util.ArrayList<GuiAction> actions = new java.util.ArrayList<>();
        if (subject.has(PhaseSixPermissions.CONFIG_VIEW)) {
            actions.add(action(GuiActionKind.VIEW_CONFIGURATION, "Configuration",
                    PhaseSixPermissions.CONFIG_VIEW, false, revision, null));
        }
        if (subject.has(PhaseSixPermissions.DOCTOR)) {
            actions.add(action(GuiActionKind.VIEW_DOCTOR, "Doctor", PhaseSixPermissions.DOCTOR,
                    false, revision, null));
        }
        return store(subject, GuiAudience.STAFF, "MaddPrestige Control Panel", actions);
    }

    public GuiSessionView openScalarEditor(
            PermissionSubject subject,
            UUID draftId,
            String path,
            String value) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        GuiMutationContext context = context(draftId, path, value, null);
        return mutationSession(subject, "Edit: " + path, GuiActionKind.EDIT_CONFIGURATION, "Set value",
                PhaseSixPermissions.CONFIG_EDIT, context, null, null);
    }

    public GuiSessionView openListValueEditor(
            PermissionSubject subject,
            UUID draftId,
            String path,
            String value,
            boolean add) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        GuiMutationContext context = context(draftId, path, value, null);
        return mutationSession(subject, "Edit list: " + path,
                add ? GuiActionKind.ADD_CONFIGURATION_VALUE : GuiActionKind.REMOVE_CONFIGURATION_VALUE,
                add ? "Add value" : "Remove value", PhaseSixPermissions.CONFIG_EDIT, context, null, null);
    }

    public GuiSessionView openStageAdder(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            String displayName,
            Optional<net.maddkraft.maddprestige.api.id.ProviderId> providerId,
            Optional<String> externalGroup) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        GuiMutationContext context = new GuiMutationContext(Optional.of(draftId), Optional.empty(),
                Optional.of(displayName), providerId, externalGroup, Optional.empty(), Optional.empty(),
                Optional.empty());
        return mutationSession(subject, "Add stage", GuiActionKind.ADD_STAGE, "Add stage " + stageId.value(),
                PhaseSixPermissions.CONFIG_EDIT, context, stageId, null);
    }

    public GuiSessionView openDraftPreview(PermissionSubject subject, UUID draftId) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        return mutationSession(subject, "Preview configuration", GuiActionKind.PREVIEW_CONFIGURATION,
                "Validate exact draft", PhaseSixPermissions.CONFIG_VIEW, GuiMutationContext.draft(draftId), null,
                null);
    }

    public GuiSessionView openDraftApply(
            PermissionSubject subject,
            UUID draftId,
            String reason) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        ConfigurationApplyKind kind = configurationAuthority.draftKind(subject, draftId);
        String permission = permission(kind);
        subject.require(permission);
        GuiMutationContext context = context(draftId, null, null, reason);
        boolean rollback = kind == ConfigurationApplyKind.ROLLBACK;
        String title = switch (kind) {
            case NORMAL -> "Apply configuration";
            case ROLLBACK -> "Apply rollback";
            case SETUP -> "Apply setup";
        };
        return mutationSession(subject, title,
                rollback ? GuiActionKind.ROLLBACK_CONFIGURATION : GuiActionKind.APPLY_CONFIGURATION,
                rollback ? "Apply rollback draft" : "Apply previewed " + kind.name().toLowerCase() + " draft",
                permission, context, null, null);
    }

    public GuiSessionView openAcknowledgement(
            PermissionSubject subject,
            UUID draftId) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        ConfigurationApplyKind kind = configurationAuthority.draftKind(subject, draftId);
        String permission = permission(kind);
        subject.require(permission);
        return mutationSession(subject, "Acknowledge configuration",
                GuiActionKind.PREPARE_CONFIGURATION_ACKNOWLEDGEMENT, "Review exact high-risk findings",
                permission, GuiMutationContext.draft(draftId), null, null);
    }

    public GuiSessionView openAcknowledgementConfirmation(
            PermissionSubject subject,
            UUID acknowledgementId,
            String reason) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        ConfigurationApplyKind kind = configurationAuthority.acknowledgementKind(subject, acknowledgementId);
        String permission = permission(kind);
        subject.require(permission);
        GuiMutationContext context = new GuiMutationContext(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(acknowledgementId),
                Optional.of(reason));
        return mutationSession(subject, "Confirm configuration",
                GuiActionKind.CONFIRM_CONFIGURATION_ACKNOWLEDGEMENT, "Confirm exact acknowledged candidate",
                permission, context, null, null);
    }

    public GuiSessionView openRollbackSelector(
            PermissionSubject subject,
            ConfigRevisionId targetRevision) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_ROLLBACK);
        GuiMutationContext context = new GuiMutationContext(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(targetRevision), Optional.empty(), Optional.empty());
        return mutationSession(subject, "Prepare rollback", GuiActionKind.ROLLBACK_CONFIGURATION,
                "Create rollback draft from " + targetRevision.value(), PhaseSixPermissions.CONFIG_ROLLBACK,
                context, null, null);
    }

    public GuiSessionView openStageEditor(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            Optional<net.maddkraft.maddprestige.api.id.StageId> replacement) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        return mutationSession(subject, "Stage: " + stageId.value(), GuiActionKind.DELETE_STAGE,
                "Delete stage " + stageId.value(), PhaseSixPermissions.CONFIG_EDIT,
                GuiMutationContext.draft(draftId), stageId, replacement.orElse(null));
    }

    public GuiSessionView openStageRemapSelector(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId missingStage,
            net.maddkraft.maddprestige.api.id.StageId replacement) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        ConfigurationApplyKind kind = configurationAuthority.draftKind(subject, draftId);
        String permission = permission(kind);
        subject.require(permission);
        return mutationSession(subject, "Remap missing stage", GuiActionKind.SELECT_STAGE_REMAP,
                missingStage.value() + " → " + replacement.value(), permission,
                GuiMutationContext.draft(draftId), missingStage, replacement);
    }

    public GuiSessionView openStageRemapRemoval(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId missingStage) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        ConfigurationApplyKind kind = configurationAuthority.draftKind(subject, draftId);
        String permission = permission(kind);
        subject.require(permission);
        return mutationSession(subject, "Remove stage remap", GuiActionKind.REMOVE_STAGE_REMAP,
                "Remove remap for " + missingStage.value(), permission,
                GuiMutationContext.draft(draftId), missingStage, null);
    }

    public CompletionStage<String> click(
            PermissionSubject subject,
            UUID sessionId,
            UUID actionId) {
        pruneExpired();
        Session session = sessions.get(Objects.requireNonNull(sessionId, "session ID"));
        if (session == null || Instant.now(clock).isAfter(session.expiresAt())) {
            sessions.remove(sessionId);
            throw new AdministrationException("gui.session.expired", "GUI session is absent or expired.",
                    "Reopen the GUI to obtain current server-owned controls.");
        }
        if (!session.actor().equals(subject.actor())) {
            throw new AdministrationException("gui.session.actor_mismatch",
                    "A GUI session cannot be used by another actor.", "Open a separate GUI session.");
        }
        GuiAction action = session.actions().get(actionId);
        if (action == null) {
            throw new AdministrationException("gui.action.forged",
                    "The clicked control is not a server-owned action in this GUI session.",
                    "Close and reopen the GUI; player-supplied items carry no authority.");
        }
        subject.require(action.requiredPermission());
        if (action.mutating() && !activeRevision.get().equals(action.expectedConfigRevision())) {
            throw new AdministrationException("gui.action.stale",
                    "The active configuration changed after this GUI rendered.",
                    "Reopen the GUI and generate a fresh preview before changing state.");
        }
        if (action.kind() == GuiActionKind.DELETE_STAGE && action.replacementStage().isEmpty()) {
            throw new AdministrationException("stage.change.remap_required",
                    "A referenced stage cannot be deleted without an explicit replacement/migration.",
                    "Select a replacement and complete the atomic remap before applying stage deletion.");
        }
        return executor.execute(subject, action);
    }

    private GuiSessionView mutationSession(
            PermissionSubject subject,
            String title,
            GuiActionKind kind,
            String label,
            String permission,
            GuiMutationContext context,
            net.maddkraft.maddprestige.api.id.StageId stage,
            net.maddkraft.maddprestige.api.id.StageId replacement) {
        ConfigRevisionId revision = activeRevision.get().orElseThrow(() -> new AdministrationException(
                "config.active.absent", "No active configuration can own this visual mutation.",
                "Complete setup or reopen the GUI after a configuration is active."));
        GuiAction action = new GuiAction(UUID.randomUUID(), kind, label, permission, true, Optional.of(revision),
                Optional.empty(), Optional.ofNullable(stage), Optional.ofNullable(replacement), Optional.of(context));
        return store(subject, GuiAudience.STAFF, title, List.of(action));
    }

    private GuiSessionView store(
            PermissionSubject subject,
            GuiAudience audience,
            String title,
            List<GuiAction> actions) {
        pruneExpired();
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(lifetime);
        LinkedHashMap<UUID, GuiAction> byId = new LinkedHashMap<>();
        actions.forEach(action -> byId.put(action.actionId(), action));
        sessions.put(id, new Session(subject.actor(), audience, Map.copyOf(byId), expiresAt));
        return new GuiSessionView(id, audience, title, actions, expiresAt);
    }

    private static GuiAction action(
            GuiActionKind kind,
            String label,
            String permission,
            boolean mutating,
            Optional<ConfigRevisionId> revision,
            UUID playerId) {
        return new GuiAction(UUID.randomUUID(), kind, label, permission, mutating, revision,
                Optional.ofNullable(playerId), Optional.empty(), Optional.empty());
    }

    private static void addIfPermitted(
            List<GuiAction> actions,
            PermissionSubject subject,
            GuiActionKind kind,
            String label,
            String permission,
            boolean mutating,
            Optional<ConfigRevisionId> revision,
            UUID playerId,
            Optional<GuiMutationContext> context) {
        if (subject.has(permission)) {
            actions.add(new GuiAction(UUID.randomUUID(), kind, label, permission, mutating, revision,
                    Optional.ofNullable(playerId), Optional.empty(), Optional.empty(), context));
        }
    }

    private static GuiMutationContext context(UUID draftId, String path, String value, String reason) {
        return new GuiMutationContext(Optional.of(draftId), Optional.ofNullable(path), Optional.ofNullable(value),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(reason));
    }

    private static String permission(ConfigurationApplyKind kind) {
        return switch (kind) {
            case NORMAL -> PhaseSixPermissions.CONFIG_APPLY;
            case ROLLBACK -> PhaseSixPermissions.CONFIG_ROLLBACK;
            case SETUP -> PhaseSixPermissions.SETUP;
        };
    }

    private void pruneExpired() {
        Instant now = Instant.now(clock);
        sessions.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record Session(
            net.maddkraft.maddprestige.api.operation.Actor actor,
            GuiAudience audience,
            Map<UUID, GuiAction> actions,
            Instant expiresAt) {
    }
}
