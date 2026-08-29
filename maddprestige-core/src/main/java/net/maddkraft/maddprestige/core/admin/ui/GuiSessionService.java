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
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

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
        addIfPermitted(actions, subject, GuiActionKind.VIEW_PROGRESS, m("gui.action.view_progress"), self ? PhaseSixPermissions.USE
                : PhaseSixPermissions.PLAYER_VIEW, false, revision, playerId, Optional.empty());
        addIfPermitted(actions, subject, GuiActionKind.SIMULATE_PRESTIGE, m("gui.action.simulate_prestige"),
                self ? PhaseSixPermissions.PRESTIGE : PhaseSixPermissions.SIMULATE, false, revision, playerId,
                Optional.empty());
        addIfPermitted(actions, subject, GuiActionKind.PREPARE_PRESTIGE, m("gui.action.prepare_prestige"),
                self ? PhaseSixPermissions.PRESTIGE : PhaseSixPermissions.EXECUTE, false, revision, playerId,
                Optional.empty());
        return store(subject, GuiAudience.PLAYER, m("gui.title.progress"), actions);
    }

    public GuiSessionView openStaff(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        java.util.ArrayList<GuiAction> actions = new java.util.ArrayList<>();
        if (subject.has(PhaseSixPermissions.CONFIG_VIEW)) {
            actions.add(action(GuiActionKind.VIEW_CONFIGURATION, m("gui.action.view_configuration"),
                    PhaseSixPermissions.CONFIG_VIEW, false, revision, null));
        }
        if (subject.has(PhaseSixPermissions.DOCTOR)) {
            actions.add(action(GuiActionKind.VIEW_DOCTOR, m("gui.action.view_doctor"), PhaseSixPermissions.DOCTOR,
                    false, revision, null));
        }
        return store(subject, GuiAudience.STAFF, m("gui.title.control_panel"), actions);
    }

    public GuiSessionView openScalarEditor(
            PermissionSubject subject,
            UUID draftId,
            String path,
            String value) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        GuiMutationContext context = context(draftId, path, value, null);
        return mutationSession(subject, m("gui.title.edit_scalar", "path", path), GuiActionKind.EDIT_CONFIGURATION,
                m("gui.action.set_value"),
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
        return mutationSession(subject, m("gui.title.edit_list", "path", path),
                add ? GuiActionKind.ADD_CONFIGURATION_VALUE : GuiActionKind.REMOVE_CONFIGURATION_VALUE,
                m(add ? "gui.action.add_value" : "gui.action.remove_value"), PhaseSixPermissions.CONFIG_EDIT,
                context, null, null);
    }

    public GuiSessionView openStageAdder(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            String displayName,
            Optional<net.maddkraft.maddprestige.api.id.ProviderId> providerId,
            Optional<String> externalGroup) {
        throw stageCompatibilityOnly();
    }

    public GuiSessionView openDraftPreview(PermissionSubject subject, UUID draftId) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        return mutationSession(subject, m("gui.title.preview_configuration"), GuiActionKind.PREVIEW_CONFIGURATION,
                m("gui.action.validate_draft"), PhaseSixPermissions.CONFIG_VIEW, GuiMutationContext.draft(draftId), null,
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
        MessageReference title = switch (kind) {
            case NORMAL -> m("gui.title.apply_configuration");
            case ROLLBACK -> m("gui.title.apply_rollback");
            case SETUP -> m("gui.title.apply_setup");
        };
        return mutationSession(subject, title,
                rollback ? GuiActionKind.ROLLBACK_CONFIGURATION : GuiActionKind.APPLY_CONFIGURATION,
                rollback ? m("gui.action.apply_rollback")
                        : m("gui.action.apply_draft", "kind", kind.name().toLowerCase(java.util.Locale.ROOT)),
                permission, context, null, null);
    }

    public GuiSessionView openAcknowledgement(
            PermissionSubject subject,
            UUID draftId) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        ConfigurationApplyKind kind = configurationAuthority.draftKind(subject, draftId);
        String permission = permission(kind);
        subject.require(permission);
        return mutationSession(subject, m("gui.title.acknowledge_configuration"),
                GuiActionKind.PREPARE_CONFIGURATION_ACKNOWLEDGEMENT, m("gui.action.review_findings"),
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
        return mutationSession(subject, m("gui.title.confirm_configuration"),
                GuiActionKind.CONFIRM_CONFIGURATION_ACKNOWLEDGEMENT, m("gui.action.confirm_candidate"),
                permission, context, null, null);
    }

    public GuiSessionView openRollbackSelector(
            PermissionSubject subject,
            ConfigRevisionId targetRevision) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        subject.require(PhaseSixPermissions.CONFIG_ROLLBACK);
        GuiMutationContext context = new GuiMutationContext(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(targetRevision), Optional.empty(), Optional.empty());
        return mutationSession(subject, m("gui.title.prepare_rollback"), GuiActionKind.ROLLBACK_CONFIGURATION,
                m("gui.action.create_rollback", "revision", targetRevision.value()), PhaseSixPermissions.CONFIG_ROLLBACK,
                context, null, null);
    }

    public GuiSessionView openStageEditor(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            Optional<net.maddkraft.maddprestige.api.id.StageId> replacement) {
        throw stageCompatibilityOnly();
    }

    public GuiSessionView openStageRemapSelector(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId missingStage,
            net.maddkraft.maddprestige.api.id.StageId replacement) {
        throw stageCompatibilityOnly();
    }

    public GuiSessionView openStageRemapRemoval(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId missingStage) {
        throw stageCompatibilityOnly();
    }

    public CompletionStage<MessageReference> click(
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
            MessageReference title,
            GuiActionKind kind,
            MessageReference label,
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
            MessageReference title,
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
            MessageReference label,
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
            MessageReference label,
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

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private static AdministrationException stageCompatibilityOnly() {
        return new AdministrationException("stage.compatibility_only",
                "Stage mutation controls are retained only for compatibility and recovery evidence.",
                "Configure numeric Prestige requirements, costs, rewards, and scaling instead.");
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
