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
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

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
    public CompletionStage<MessageReference> execute(PermissionSubject subject, GuiAction action) {
        return switch (action.kind()) {
            case VIEW_PROGRESS -> playerViews.view(subject, target(action)).thenApply(value ->
                    m("gui.result.progress", "player", value.playerId(),
                            "prestige_status", status(value.prestige())));
            case SIMULATE_RANK_UP, PREPARE_RANK_UP -> throw rankUpCompatibilityOnly();
            case SIMULATE_PRESTIGE -> previews.simulatePrestige(subject, target(action))
                    .thenApply(value -> preview("gui.result.simulate_prestige", value));
            case PREPARE_PRESTIGE -> confirmations.preparePrestige(subject, target(action))
                    .thenApply(value -> m("gui.result.prepare_prestige", "status", status(value.preview()),
                            "confirmation", value.confirmationId(), "expires", value.expiresAt()));
            case CONFIRM_PRESTIGE, BACK_PLAYER, CLOSE_PLAYER,
                    STAFF_OPEN_PLAYERS, STAFF_OPEN_ONLINE_PLAYERS, STAFF_FIND_PLAYER,
                    STAFF_VIEW_CONFIGURATION, STAFF_OPEN_PRESTIGE_LEVELS,
                    STAFF_PRESTIGE_LEVELS_PREVIOUS, STAFF_PRESTIGE_LEVELS_NEXT,
                    STAFF_SELECT_PRESTIGE_LEVEL, STAFF_VIEW_PRESTIGE_REQUIREMENTS,
                    STAFF_EDIT_TOTAL_SKILL_LEVEL, STAFF_REVIEW_TOTAL_SKILL_LEVEL,
                    STAFF_CONFIRM_TOTAL_SKILL_LEVEL, STAFF_EDIT_PRESTIGE_MONEY,
                    STAFF_MONEY_PREVIOUS, STAFF_MONEY_NEXT, STAFF_REVIEW_PRESTIGE_MONEY,
                    STAFF_CONFIRM_PRESTIGE_MONEY, STAFF_EDIT_PRESTIGE_REWARD,
                    STAFF_REWARD_PREVIOUS, STAFF_REWARD_NEXT, STAFF_REVIEW_PRESTIGE_REWARD,
                    STAFF_CONFIRM_PRESTIGE_REWARD, STAFF_VIEW_PRESTIGE_SCALING,
                    STAFF_EDIT_SCALING_LINEAR_BASE, STAFF_REVIEW_SCALING_LINEAR_BASE,
                    STAFF_CONFIRM_SCALING_LINEAR_BASE,
                    STAFF_EDIT_SCALING_LINEAR_INCREMENT, STAFF_REVIEW_SCALING_LINEAR_INCREMENT,
                    STAFF_CONFIRM_SCALING_LINEAR_INCREMENT,
                    STAFF_MANAGE_SCALING_OVERRIDE, STAFF_ADD_SCALING_OVERRIDE, STAFF_EDIT_SCALING_OVERRIDE,
                    STAFF_REVIEW_SCALING_OVERRIDE, STAFF_REMOVE_SCALING_OVERRIDE,
                    STAFF_CONFIRM_SCALING_OVERRIDE_EDIT, STAFF_CONFIRM_SCALING_OVERRIDE_REMOVAL,
                    STAFF_BACK_SCALING_OVERRIDE, STAFF_BACK_CONFIGURATION,
                    STAFF_BACK_PRESTIGE_LEVELS, STAFF_BACK_PRESTIGE_LEVEL, STAFF_BACK_PRESTIGE_REQUIREMENTS,
                    STAFF_BACK_PRESTIGE_SCALING,
                    STAFF_SELECT_PLAYER, STAFF_SELECT_SEARCH_RESULT,
                    STAFF_REFRESH_PLAYER_OVERVIEW, STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW,
                    STAFF_COPY_PLAYER_UUID, STAFF_COPY_SEARCH_PLAYER_UUID,
                    STAFF_VIEW_REQUIREMENTS,
                    STAFF_VIEW_PRESTIGE_PREVIEW, STAFF_REFRESH_PRESTIGE_PREVIEW,
                    STAFF_VIEW_HISTORY, STAFF_HISTORY_PREVIOUS, STAFF_HISTORY_NEXT,
                    STAFF_VIEW_SYSTEM_STATUS, STAFF_REFRESH_SYSTEM_STATUS,
                    STAFF_SYSTEM_STATUS_PREVIOUS, STAFF_SYSTEM_STATUS_NEXT,
                    STAFF_REQUIREMENTS_PREVIOUS, STAFF_REQUIREMENTS_NEXT,
                    STAFF_VIEW_PLAYER_HISTORY, STAFF_PLAYER_HISTORY_PREVIOUS, STAFF_PLAYER_HISTORY_NEXT,
                    STAFF_MANAGE_PLAYER, STAFF_SET_PRESTIGE, STAFF_ADJUST_PRESTIGE_TARGET,
                    STAFF_REVIEW_PRESTIGE_SET, STAFF_REVIEW_PRESTIGE_RESET,
                    STAFF_CONFIRM_PRESTIGE_ADJUSTMENT, STAFF_BACK_MANAGE_PLAYER,
                    STAFF_BACK_PRESTIGE_SELECTOR,
                    STAFF_BACK_DASHBOARD, STAFF_BACK_PLAYER_MANAGEMENT, STAFF_BACK_PLAYER_LIST,
                    STAFF_BACK_PLAYER_SEARCH_RESULTS, STAFF_BACK_PLAYER_OVERVIEW,
                    STAFF_CLOSE -> throw new AdministrationException(
                    "gui.action.player_invalid", "Player navigation must use the Player GUI flow.",
                    "Reopen the intended GUI flow.");
            case VIEW_DOCTOR -> doctor.inspect(subject).thenApply(value -> m("gui.result.doctor",
                    "status", value.status(), "count", value.findings().size()));
            case VIEW_CONFIGURATION -> CompletableFuture.completedFuture(configuration.active(subject)
                    .map(value -> m("gui.result.configuration_active", "revision", value.revisionId().value()))
                    .orElseGet(() -> m("gui.result.configuration_inactive")));
            case EDIT_CONFIGURATION, ADD_CONFIGURATION_VALUE, REMOVE_CONFIGURATION_VALUE,
                    ADD_CONFIGURATION_OBJECT, EDIT_CONFIGURATION_OBJECT, REMOVE_CONFIGURATION_OBJECT,
                    PREVIEW_CONFIGURATION, PREPARE_CONFIGURATION_ACKNOWLEDGEMENT,
                    CONFIRM_CONFIGURATION_ACKNOWLEDGEMENT, APPLY_CONFIGURATION, ROLLBACK_CONFIGURATION ->
                    mutations.execute(subject, action);
            case ADD_STAGE, SELECT_STAGE_REMAP, REMOVE_STAGE_REMAP, DELETE_STAGE ->
                    throw stageCompatibilityOnly();
        };
    }

    private static UUID target(GuiAction action) {
        return action.targetPlayer().orElseThrow(() -> new AdministrationException(
                "gui.target.missing", "The server-owned GUI action has no player target.",
                "Reopen the player view to create a complete server-owned action."));
    }

    private static MessageReference preview(String key, OperationPreview preview) {
        return m(key, "player", preview.playerId(), "status", status(preview),
                "blockers", preview.blockers().size(), "revision", preview.configRevision().value());
    }

    private static String status(OperationPreview preview) {
        return preview.executable() ? "ELIGIBLE" : "BLOCKED";
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private static AdministrationException rankUpCompatibilityOnly() {
        return new AdministrationException("rankup.compatibility_only",
                "Rank-up controls are retained only for compatibility.",
                "Use the numeric Prestige controls instead.");
    }

    private static AdministrationException stageCompatibilityOnly() {
        return new AdministrationException("stage.compatibility_only",
                "Stage mutation controls are retained only for compatibility and recovery evidence.",
                "Configure numeric Prestige requirements, costs, rewards, and scaling instead.");
    }
}
