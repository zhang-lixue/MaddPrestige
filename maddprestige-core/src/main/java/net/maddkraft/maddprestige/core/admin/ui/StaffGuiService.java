package net.maddkraft.maddprestige.core.admin.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdministrationService;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustmentKind;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustmentReview;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeTargetPage;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.config.GuidedConfigurationAdministration;
import net.maddkraft.maddprestige.core.admin.config.GuidedMoneyConfigurationReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedNumericConfigurationInput;
import net.maddkraft.maddprestige.core.admin.config.GuidedRequirementConfigurationEntry;
import net.maddkraft.maddprestige.core.admin.config.GuidedRequirementConfigurationView;
import net.maddkraft.maddprestige.core.admin.config.GuidedRewardConfigurationReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingConfigurationReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingConfigurationView;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingParameter;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingOverrideReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedTotalSkillLevelReview;
import net.maddkraft.maddprestige.core.admin.config.PrestigeLevelConfigurationView;
import net.maddkraft.maddprestige.core.admin.config.PrestigeLevelPage;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistorySource.Entry;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Component;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.ConfigurationSummary;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Health;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Summary;

/** Staff inventory client over canonical read projections and narrowly scoped administration services. */
public final class StaffGuiService {
    private static final int SIZE = 27;
    private static final int PAGE_SIZE = 7;
    private static final List<Integer> REQUIREMENT_CARD_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final List<Integer> BALANCED_TWO_REQUIREMENT_SLOTS = List.of(11, 15);
    private static final List<Integer> BORDER_SLOTS = List.of(
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 17,
            19, 20, 21, 23, 24, 25);
    private final GuiSessionService sessions;
    private final PlayerProgressViewService progress;
    private final StaffPlayerDirectory players;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final StaffHistorySource history;
    private final StaffSystemStatusSource systemStatus;
    private final Optional<ManualPrestigeAdministrationService> prestigeAdministration;
    private final Optional<GuidedConfigurationAdministration> configurationAdministration;

    public StaffGuiService(
            GuiSessionService sessions,
            PlayerProgressViewService progress,
            StaffPlayerDirectory players,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            StaffHistorySource history,
            StaffSystemStatusSource systemStatus) {
        this(sessions, progress, players, activeRevision, history, systemStatus, null, null);
    }

    public StaffGuiService(
            GuiSessionService sessions,
            PlayerProgressViewService progress,
            StaffPlayerDirectory players,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            StaffHistorySource history,
            StaffSystemStatusSource systemStatus,
            ManualPrestigeAdministrationService prestigeAdministration) {
        this(sessions, progress, players, activeRevision, history, systemStatus, prestigeAdministration, null);
    }

    public StaffGuiService(
            GuiSessionService sessions,
            PlayerProgressViewService progress,
            StaffPlayerDirectory players,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            StaffHistorySource history,
            StaffSystemStatusSource systemStatus,
            ManualPrestigeAdministrationService prestigeAdministration,
            GuidedConfigurationAdministration configurationAdministration) {
        this.sessions = Objects.requireNonNull(sessions, "GUI sessions");
        this.progress = Objects.requireNonNull(progress, "player progress");
        this.players = Objects.requireNonNull(players, "player directory");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.history = Objects.requireNonNull(history, "history source");
        this.systemStatus = Objects.requireNonNull(systemStatus, "system status source");
        this.prestigeAdministration = Optional.ofNullable(prestigeAdministration);
        this.configurationAdministration = Optional.ofNullable(configurationAdministration);
    }

    public GuiSessionView open(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.ADMIN_GUI);
        requirePlayerActor(subject);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        Summary summary = safeSummary();
        items.add(GuiDisplayItem.display(4, GuiItemIcon.STAFF,
                m("gui.item.staff.overview.title"), List.of(
                        m(summary.configurationActive()
                                ? "gui.item.staff.overview.configuration.active"
                                : "gui.item.staff.overview.configuration.inactive"),
                        m("gui.item.staff.overview.providers", "current", summary.availableProviders(),
                                "total", summary.totalProviders()))));
        if (subject.has(PhaseSixPermissions.PLAYER_VIEW)) {
            GuiAction playersAction = action(GuiActionKind.STAFF_OPEN_PLAYERS,
                    "gui.action.staff.players", PhaseSixPermissions.PLAYER_VIEW, revision, null);
            actions.add(playersAction);
            items.add(GuiDisplayItem.action(10, GuiItemIcon.PLAYERS, playersAction.label(),
                    List.of(m("gui.item.staff.players.lore")), playersAction.actionId(), false));
        } else {
            items.add(GuiDisplayItem.display(10, GuiItemIcon.PLAYERS,
                    m("gui.action.staff.players"), List.of(m("gui.item.staff.players.unavailable"))));
        }
        GuiAction configurationAction = action(GuiActionKind.STAFF_VIEW_CONFIGURATION,
                "gui.action.staff.configuration", PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(configurationAction);
        items.add(GuiDisplayItem.action(12, GuiItemIcon.CONFIGURATION, configurationAction.label(),
                List.of(m("gui.item.staff.configuration.lore")), configurationAction.actionId(), false));
        GuiAction historyAction = action(GuiActionKind.STAFF_VIEW_HISTORY,
                "gui.action.staff.history", PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(historyAction);
        items.add(GuiDisplayItem.action(14, GuiItemIcon.HISTORY, historyAction.label(),
                List.of(m("gui.item.staff.history.lore")), historyAction.actionId(), false));
        GuiAction statusAction = action(GuiActionKind.STAFF_VIEW_SYSTEM_STATUS,
                statusActionKey(summary.health()), PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(statusAction);
        items.add(GuiDisplayItem.action(16, GuiItemIcon.SYSTEM_STATUS, statusAction.label(),
                List.of(m("gui.item.staff.system_status.lore", "status", statusText(summary.health()))),
                statusAction.actionId(), false));
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.dashboard"), actions,
                GuiScreenKind.STAFF_DASHBOARD, SIZE, items);
    }

    public CompletionStage<PlayerGuiInteractionResult> click(
            PermissionSubject subject,
            UUID sessionId,
            UUID actionId) {
        GuiAction action = sessions.authorizeStaffClick(subject, sessionId, actionId);
        return switch (action.kind()) {
            case STAFF_OPEN_PLAYERS -> completed(playerManagement(subject));
            case STAFF_OPEN_ONLINE_PLAYERS -> completed(onlinePlayerSelection(subject));
            case STAFF_FIND_PLAYER -> CompletableFuture.completedFuture(PlayerGuiInteractionResult.closed(
                    m("gui.staff.find.prompt")));
            case STAFF_VIEW_CONFIGURATION -> completed(configuration(subject));
            case STAFF_OPEN_PRESTIGE_LEVELS -> completed(prestigeLevels(subject, 0));
            case STAFF_PRESTIGE_LEVELS_PREVIOUS, STAFF_PRESTIGE_LEVELS_NEXT ->
                    completed(prestigeLevels(subject, page(action)));
            case STAFF_SELECT_PRESTIGE_LEVEL -> completed(prestigeLevel(subject, configurationLevel(action)));
            case STAFF_VIEW_PRESTIGE_REQUIREMENTS ->
                    completed(prestigeRequirements(subject, configurationLevel(action)));
            case STAFF_EDIT_TOTAL_SKILL_LEVEL ->
                    completed(totalSkillLevelInput(subject, configurationLevel(action)));
            case STAFF_CONFIRM_TOTAL_SKILL_LEVEL ->
                    confirmTotalSkillLevel(subject, configurationReview(action));
            case STAFF_EDIT_PRESTIGE_MONEY -> completed(moneyInput(subject, configurationLevel(action)));
            case STAFF_CONFIRM_PRESTIGE_MONEY -> confirmMoney(subject, configurationReview(action));
            case STAFF_EDIT_PRESTIGE_REWARD -> completed(rewardInput(subject, configurationLevel(action)));
            case STAFF_CONFIRM_PRESTIGE_REWARD -> confirmReward(subject, configurationReview(action));
            case STAFF_VIEW_PRESTIGE_SCALING -> completed(scaling(subject, configurationLevel(action)));
            case STAFF_EDIT_SCALING_LINEAR_BASE -> completed(scalingInput(
                    subject, configurationLevel(action), GuidedScalingParameter.LINEAR_BASE));
            case STAFF_EDIT_SCALING_LINEAR_INCREMENT -> completed(scalingInput(
                    subject, configurationLevel(action), GuidedScalingParameter.LINEAR_INCREMENT));
            case STAFF_CONFIRM_SCALING_LINEAR_BASE, STAFF_CONFIRM_SCALING_LINEAR_INCREMENT ->
                    confirmScaling(subject, configurationReview(action));
            case STAFF_MANAGE_SCALING_OVERRIDE -> completed(scalingOverride(
                    subject, configurationLevel(action)));
            case STAFF_ADD_SCALING_OVERRIDE, STAFF_EDIT_SCALING_OVERRIDE -> completed(scalingOverrideInput(
                    subject, configurationLevel(action)));
            case STAFF_REMOVE_SCALING_OVERRIDE -> reviewScalingOverrideRemoval(subject, action);
            case STAFF_CONFIRM_SCALING_OVERRIDE_EDIT, STAFF_CONFIRM_SCALING_OVERRIDE_REMOVAL ->
                    confirmScalingOverride(subject, configurationReview(action));
            case STAFF_BACK_SCALING_OVERRIDE -> completed(scalingOverride(
                    subject, configurationLevel(action)));
            case STAFF_BACK_CONFIGURATION -> completed(configuration(subject));
            case STAFF_BACK_PRESTIGE_LEVELS -> completed(prestigeLevels(subject, page(action)));
            case STAFF_BACK_PRESTIGE_LEVEL -> completed(prestigeLevel(subject, configurationLevel(action)));
            case STAFF_BACK_PRESTIGE_REQUIREMENTS ->
                    completed(prestigeRequirements(subject, configurationLevel(action)));
            case STAFF_BACK_PRESTIGE_SCALING -> completed(scaling(subject, configurationLevel(action)));
            case STAFF_VIEW_HISTORY -> history(subject, 0);
            case STAFF_HISTORY_PREVIOUS, STAFF_HISTORY_NEXT -> history(subject, page(action));
            case STAFF_VIEW_SYSTEM_STATUS -> systemStatus(subject, 0);
            case STAFF_REFRESH_SYSTEM_STATUS -> systemStatus(subject, page(action));
            case STAFF_SYSTEM_STATUS_PREVIOUS, STAFF_SYSTEM_STATUS_NEXT ->
                    systemStatus(subject, page(action));
            case STAFF_SELECT_PLAYER -> overview(subject, target(action), false);
            case STAFF_SELECT_SEARCH_RESULT -> overview(subject, target(action), true);
            case STAFF_REFRESH_PLAYER_OVERVIEW -> overview(subject, target(action), false);
            case STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW -> overview(subject, target(action), true);
            case STAFF_COPY_PLAYER_UUID -> copyPlayerUuid(subject, target(action), false);
            case STAFF_COPY_SEARCH_PLAYER_UUID -> copyPlayerUuid(subject, target(action), true);
            case STAFF_VIEW_PRESTIGE_PREVIEW, STAFF_REFRESH_PRESTIGE_PREVIEW ->
                    prestigePreview(subject, target(action));
            case STAFF_VIEW_PLAYER_HISTORY -> playerHistory(subject, target(action), 0);
            case STAFF_PLAYER_HISTORY_PREVIOUS, STAFF_PLAYER_HISTORY_NEXT ->
                    playerHistory(subject, target(action), page(action));
            case STAFF_VIEW_REQUIREMENTS -> requirements(subject, target(action), 0);
            case STAFF_REQUIREMENTS_PREVIOUS, STAFF_REQUIREMENTS_NEXT ->
                    requirements(subject, target(action), page(action));
            case STAFF_MANAGE_PLAYER -> managePlayer(subject, target(action));
            case STAFF_SET_PRESTIGE -> prestigeTargets(subject, target(action), 0);
            case STAFF_ADJUST_PRESTIGE_TARGET -> prestigeTargets(subject, target(action), page(action));
            case STAFF_REVIEW_PRESTIGE_SET -> completed(prestigeReviewView(subject, known(target(action)),
                    prestigeReview(action), page(action)));
            case STAFF_REVIEW_PRESTIGE_RESET -> prestigeResetReview(subject, target(action));
            case STAFF_CONFIRM_PRESTIGE_ADJUSTMENT -> confirmPrestigeAdjustment(subject, prestigeReview(action));
            case STAFF_BACK_MANAGE_PLAYER -> managePlayer(subject, target(action));
            case STAFF_BACK_PRESTIGE_SELECTOR -> prestigeTargets(subject, target(action), page(action));
            case STAFF_BACK_DASHBOARD -> completed(open(subject));
            case STAFF_BACK_PLAYER_MANAGEMENT -> completed(playerManagement(subject));
            case STAFF_BACK_PLAYER_LIST -> completed(onlinePlayerSelection(subject));
            case STAFF_BACK_PLAYER_SEARCH_RESULTS -> completed(searchResults(subject,
                    known(target(action)).name()));
            case STAFF_BACK_PLAYER_OVERVIEW -> overview(subject, target(action));
            case STAFF_CLOSE -> CompletableFuture.completedFuture(PlayerGuiInteractionResult.closed());
            default -> throw invalidStaffAction();
        };
    }

    /**
     * Validates one actor/revision-bound typed value before consuming the input session and preparing its review.
     * Invalid user text leaves the current input authority live so staff can correct it without reopening the flow.
     */
    public CompletionStage<PlayerGuiInteractionResult> submitNumericInput(
            PermissionSubject subject,
            UUID sessionId,
            UUID actionId,
            String input) {
        GuiAction action = sessions.authorizeStaffInput(subject, sessionId, actionId);
        ConfigRevisionId expectedRevision = configurationRevision(action);
        String normalized;
        try {
            normalized = switch (action.kind()) {
                case STAFF_REVIEW_PRESTIGE_MONEY -> configurationAdministration().validateMoneyInput(
                        subject, configurationLevel(action), input, expectedRevision);
                case STAFF_REVIEW_PRESTIGE_REWARD -> configurationAdministration().validateRewardInput(
                        subject, configurationLevel(action), input, expectedRevision);
                case STAFF_REVIEW_TOTAL_SKILL_LEVEL -> configurationAdministration().validateTotalSkillLevelInput(
                        subject, configurationLevel(action), input, expectedRevision);
                case STAFF_REVIEW_SCALING_LINEAR_BASE, STAFF_REVIEW_SCALING_LINEAR_INCREMENT ->
                        configurationAdministration().validateScalingInput(subject, configurationLevel(action),
                                scalingParameter(action.kind()), input, expectedRevision);
                case STAFF_REVIEW_SCALING_OVERRIDE -> configurationAdministration().validateScalingOverrideInput(
                        subject, configurationLevel(action), input, expectedRevision);
                default -> throw invalidStaffAction();
            };
        } catch (AdministrationException exception) {
            if (invalidNumericInput(action.kind(), exception.code())) {
                return CompletableFuture.completedFuture(
                        PlayerGuiInteractionResult.stay(m(scalingInvalidMessage(action.kind()))));
            }
            throw exception;
        }
        GuiAction consumed = sessions.authorizeStaffClick(subject, sessionId, actionId);
        return switch (consumed.kind()) {
            case STAFF_REVIEW_PRESTIGE_MONEY ->
                    reviewMoney(subject, consumed, normalized, expectedRevision);
            case STAFF_REVIEW_PRESTIGE_REWARD ->
                    reviewReward(subject, consumed, normalized, expectedRevision);
            case STAFF_REVIEW_TOTAL_SKILL_LEVEL ->
                    reviewTotalSkillLevel(subject, consumed, normalized, expectedRevision);
            case STAFF_REVIEW_SCALING_LINEAR_BASE, STAFF_REVIEW_SCALING_LINEAR_INCREMENT ->
                    reviewScaling(subject, consumed, normalized, expectedRevision);
            case STAFF_REVIEW_SCALING_OVERRIDE ->
                    reviewScalingOverride(subject, consumed, normalized, expectedRevision);
            default -> throw new IllegalStateException("Validated numeric input changed action kind");
        };
    }

    /**
     * Checks one actor/revision-bound numeric value without consuming its GUI authority.
     * Paper uses this read-only check to expose the native anvil result only while the current text can safely
     * advance to review; submit still performs the same validation again before consuming the session.
     */
    public boolean acceptsNumericInput(
            PermissionSubject subject,
            UUID sessionId,
            UUID actionId,
            String input) {
        try {
            GuiAction action = sessions.authorizeStaffInput(subject, sessionId, actionId);
            ConfigRevisionId expectedRevision = configurationRevision(action);
            switch (action.kind()) {
                case STAFF_REVIEW_PRESTIGE_MONEY -> configurationAdministration().validateMoneyInput(
                        subject, configurationLevel(action), input, expectedRevision);
                case STAFF_REVIEW_PRESTIGE_REWARD -> configurationAdministration().validateRewardInput(
                        subject, configurationLevel(action), input, expectedRevision);
                case STAFF_REVIEW_TOTAL_SKILL_LEVEL -> configurationAdministration().validateTotalSkillLevelInput(
                        subject, configurationLevel(action), input, expectedRevision);
                case STAFF_REVIEW_SCALING_LINEAR_BASE, STAFF_REVIEW_SCALING_LINEAR_INCREMENT ->
                        configurationAdministration().validateScalingInput(subject, configurationLevel(action),
                                scalingParameter(action.kind()), input, expectedRevision);
                case STAFF_REVIEW_SCALING_OVERRIDE -> configurationAdministration().validateScalingOverrideInput(
                        subject, configurationLevel(action), input, expectedRevision);
                default -> throw invalidStaffAction();
            }
            return true;
        } catch (AdministrationException exception) {
            return false;
        }
    }

    public void closeView(UUID sessionId) {
        sessions.invalidate(sessionId);
    }

    public void invalidateStaff(UUID actorId) {
        sessions.invalidateStaff(actorId);
    }

    /** Opens actor-bound, server-owned results for a bounded known-player selector. */
    public GuiSessionView findPlayers(PermissionSubject subject, String selector) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        requirePlayerActor(subject);
        return searchResults(subject, selector);
    }

    private GuiSessionView configuration(PermissionSubject subject) {
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ConfigurationSummary summary = safeConfigurationSummary();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        ArrayList<MessageReference> lore = new ArrayList<>();
        if (!summary.active()) {
            lore.add(m("gui.item.staff.configuration.inactive"));
        } else {
            lore.add(m(summary.usable()
                    ? "gui.item.staff.configuration.usable"
                    : "gui.item.staff.configuration.unavailable"));
            lore.add(m("gui.item.staff.configuration.range", "value", summary.prestigeRange()));
            lore.add(m("gui.item.staff.configuration.requirements", "count", summary.requirements()));
            lore.add(m("gui.item.staff.configuration.rewards", "count", summary.rewards()));
            lore.add(m("gui.item.staff.configuration.scaling", "count", summary.scalingProfiles()));
            lore.add(m("gui.item.staff.configuration.providers", "provider",
                    providerSummary(summary.providers())));
        }
        items.add(GuiDisplayItem.display(4, GuiItemIcon.CONFIGURATION,
                m("gui.item.staff.configuration.detail.title"), lore));
        if (summary.active() && configurationAdministration.isPresent()) {
            GuiAction levels = configurationAction(GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS,
                    "gui.action.staff.prestige_levels", PhaseSixPermissions.CONFIG_VIEW, false, revision,
                    null, null, null);
            actions.add(levels);
            ArrayList<MessageReference> levelLore = new ArrayList<>();
            levelLore.add(m("gui.item.staff.prestige_levels.lore"));
            revision.ifPresent(value -> {
                levelLore.add(m("gui.item.separator"));
                levelLore.add(m("gui.item.staff.configuration.revision", "revision", value.value()));
            });
            items.add(GuiDisplayItem.action(13, GuiItemIcon.PRESTIGE, levels.label(),
                    levelLore, levels.actionId(), false));
        }
        addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.configuration"), actions,
                GuiScreenKind.STAFF_CONFIGURATION, SIZE, items);
    }

    private GuiSessionView prestigeLevels(PermissionSubject subject, int pageIndex) {
        PrestigeLevelPage page = configurationAdministration().prestigeLevels(subject, pageIndex, PAGE_SIZE);
        Optional<ConfigRevisionId> revision = Optional.of(page.revision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        for (int index = 0; index < page.levels().size(); index++) {
            long level = page.levels().get(index);
            GuiAction select = configurationAction(GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL,
                    "gui.action.staff.prestige_level", PhaseSixPermissions.CONFIG_VIEW, false, revision,
                    level, null, null, "level", level);
            actions.add(select);
            items.add(GuiDisplayItem.action(REQUIREMENT_CARD_SLOTS.get(index), GuiItemIcon.PRESTIGE,
                    select.label(), List.of(), select.actionId(), false));
        }
        if (page.hasPrevious()) {
            addConfigurationPage(actions, items, GuiActionKind.STAFF_PRESTIGE_LEVELS_PREVIOUS,
                    "gui.action.staff.previous_page", 19, PhaseSixPermissions.CONFIG_VIEW, revision, null,
                    page.pageIndex() - 1);
        }
        if (page.hasNext()) {
            addConfigurationPage(actions, items, GuiActionKind.STAFF_PRESTIGE_LEVELS_NEXT,
                    "gui.action.staff.next_page", 25, PhaseSixPermissions.CONFIG_VIEW, revision, null,
                    page.pageIndex() + 1);
        }
        addBack(actions, items, GuiActionKind.STAFF_BACK_CONFIGURATION, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.prestige_levels"), actions,
                GuiScreenKind.STAFF_PRESTIGE_LEVELS, SIZE, items);
    }

    private GuiSessionView prestigeLevel(PermissionSubject subject, long prestigeLevel) {
        PrestigeLevelConfigurationView level = configurationAdministration().prestigeLevel(subject, prestigeLevel);
        Optional<ConfigRevisionId> revision = Optional.of(level.revision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(4, level.enabled() ? GuiItemIcon.READY : GuiItemIcon.BLOCKED,
                m("gui.item.staff.level.overview", "level", prestigeLevel), List.of(
                        m(level.enabled() ? "gui.item.staff.level.enabled" : "gui.item.staff.level.disabled"))));
        GuiAction requirements = configurationAction(GuiActionKind.STAFF_VIEW_PRESTIGE_REQUIREMENTS,
                "gui.item.staff.level.requirements", PhaseSixPermissions.CONFIG_VIEW, false, revision,
                prestigeLevel, null, null);
        actions.add(requirements);
        items.add(GuiDisplayItem.action(10, GuiItemIcon.REQUIREMENTS, requirements.label(),
                List.of(m("gui.item.staff.level.requirements.open")), requirements.actionId(), false));
        List<MessageReference> moneyLore = new ArrayList<>();
        if (level.moneyAvailable()) {
            moneyLore.add(m("gui.item.staff.level.money_requirement", "amount", level.moneyRequirement()));
            moneyLore.add(m("gui.item.staff.level.money_cost", "amount", level.moneyCost()));
        }
        boolean canEdit = level.moneyAvailable() && level.moneyEditable()
                && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                && subject.has(PhaseSixPermissions.CONFIG_APPLY);
        if (canEdit) {
            GuiAction money = configurationAction(GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY,
                    "gui.action.staff.level.money", PhaseSixPermissions.CONFIG_EDIT, false, revision,
                    prestigeLevel, null, null);
            actions.add(money);
            moneyLore.add(m("gui.item.staff.level.money.edit"));
            items.add(GuiDisplayItem.action(13, GuiItemIcon.BALANCE, money.label(), moneyLore,
                    money.actionId(), false));
        } else {
            moneyLore.add(m(!level.moneyAvailable() ? "gui.item.staff.level.money.unavailable"
                    : level.moneyEditable() ? "gui.item.staff.level.money.permission"
                            : "gui.item.staff.level.money.read_only"));
            items.add(GuiDisplayItem.display(13, GuiItemIcon.BALANCE,
                    m("gui.action.staff.level.money"), moneyLore));
        }
        List<MessageReference> rewardLore = new ArrayList<>();
        if (level.rewardAvailable()) {
            rewardLore.add(m("gui.item.staff.level.reward_amount", "amount", level.rewardAmount()));
        } else {
            rewardLore.add(m("gui.item.staff.level.reward_count", "count", level.rewards()));
        }
        boolean canEditReward = level.rewardAvailable() && level.rewardEditable()
                && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                && subject.has(PhaseSixPermissions.CONFIG_APPLY);
        if (canEditReward) {
            GuiAction reward = configurationAction(GuiActionKind.STAFF_EDIT_PRESTIGE_REWARD,
                    "gui.action.staff.level.rewards", PhaseSixPermissions.CONFIG_EDIT, false, revision,
                    prestigeLevel, null, null);
            actions.add(reward);
            rewardLore.add(m("gui.item.staff.level.reward.edit"));
            items.add(GuiDisplayItem.action(16, GuiItemIcon.REWARD, reward.label(), rewardLore,
                    reward.actionId(), false));
        } else {
            rewardLore.add(m(!level.rewardAvailable() ? "gui.item.staff.level.reward.unavailable"
                    : level.rewardEditable() ? "gui.item.staff.level.reward.permission"
                            : "gui.item.staff.level.reward.read_only"));
            items.add(GuiDisplayItem.display(16, GuiItemIcon.REWARD,
                    m("gui.item.staff.level.rewards"), rewardLore));
        }
        GuiAction scaling = configurationAction(GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING,
                "gui.item.staff.level.scaling", PhaseSixPermissions.CONFIG_VIEW, false, revision,
                prestigeLevel, null, null);
        actions.add(scaling);
        items.add(GuiDisplayItem.action(22, GuiItemIcon.CONFIGURATION, scaling.label(), List.of(
                m("gui.item.staff.level.scaling_value", "value", level.scaling()),
                m("gui.item.staff.level.scaling.open")), scaling.actionId(), false));
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_LEVELS, revision,
                prestigeLevel, Math.toIntExact((prestigeLevel - 1) / PAGE_SIZE));
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.prestige_level", "level", prestigeLevel),
                actions, GuiScreenKind.STAFF_PRESTIGE_LEVEL_CONFIGURATION, SIZE, items);
    }

    private GuiSessionView prestigeRequirements(PermissionSubject subject, long prestigeLevel) {
        GuidedRequirementConfigurationView view = configurationAdministration().requirements(subject, prestigeLevel);
        Optional<ConfigRevisionId> revision = Optional.of(view.revision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        ArrayList<MessageReference> summary = new ArrayList<>();
        summary.add(m("gui.item.staff.config_requirements.count", "count", view.requirements().size()));
        if (view.complex()) {
            summary.add(m("gui.item.staff.config_requirements.complex"));
        }
        items.add(GuiDisplayItem.display(4, GuiItemIcon.REQUIREMENTS,
                m("gui.item.staff.level.requirements"), summary));
        List<Integer> slots = view.requirements().size() == 2
                ? BALANCED_TWO_REQUIREMENT_SLOTS : REQUIREMENT_CARD_SLOTS;
        int visible = Math.min(slots.size(), view.requirements().size());
        for (int index = 0; index < visible; index++) {
            GuidedRequirementConfigurationEntry requirement = view.requirements().get(index);
            int slot = slots.get(index);
            switch (requirement.kind()) {
                case MONEY -> addMoneyRequirement(
                        subject, prestigeLevel, revision, actions, items, slot, requirement);
                case TOTAL_SKILL_LEVEL -> addTotalSkillLevelRequirement(
                        subject, prestigeLevel, revision, actions, items, slot, requirement);
                case OTHER -> addReadOnlyRequirement(items, slot, GuiItemIcon.REQUIREMENTS,
                        m("gui.item.staff.config_requirements.entry", "label", requirement.displayName()),
                        m("gui.item.staff.config_requirements.value", "value", requirement.currentTarget()));
            }
        }
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL, revision,
                prestigeLevel, 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.prestige_requirements", "level", prestigeLevel), actions,
                GuiScreenKind.STAFF_PRESTIGE_REQUIREMENTS, SIZE, items);
    }

    private static void addMoneyRequirement(
            PermissionSubject subject,
            long prestigeLevel,
            Optional<ConfigRevisionId> revision,
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            int slot,
            GuidedRequirementConfigurationEntry requirement) {
        MessageReference title = m("gui.item.staff.config_requirements.money.title");
        MessageReference current = m("gui.item.staff.config_requirements.money.value",
                "value", requirement.currentTarget());
        boolean canEdit = requirement.editable()
                && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                && subject.has(PhaseSixPermissions.CONFIG_APPLY);
        if (canEdit) {
            GuiAction edit = configurationAction(GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY,
                    "gui.item.staff.config_requirements.money.title",
                    PhaseSixPermissions.CONFIG_EDIT, false, revision, prestigeLevel, null, null);
            actions.add(edit);
            items.add(GuiDisplayItem.action(slot, GuiItemIcon.BALANCE, edit.label(), List.of(current),
                    edit.actionId(), false));
            return;
        }
        if (requirement.editable()) {
            items.add(GuiDisplayItem.display(slot, GuiItemIcon.BALANCE, title,
                    List.of(current, m("gui.item.staff.config_requirements.permission"))));
            return;
        }
        addReadOnlyRequirement(items, slot, GuiItemIcon.BALANCE, title, current);
    }

    private static void addTotalSkillLevelRequirement(
            PermissionSubject subject,
            long prestigeLevel,
            Optional<ConfigRevisionId> revision,
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            int slot,
            GuidedRequirementConfigurationEntry requirement) {
        ArrayList<MessageReference> lore = new ArrayList<>();
        lore.add(m("gui.item.staff.config_requirements.total_skill_level.current",
                "current", requirement.currentTarget()));
        boolean canEdit = requirement.editable()
                && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                && subject.has(PhaseSixPermissions.CONFIG_APPLY);
        if (canEdit) {
            GuiAction edit = configurationAction(GuiActionKind.STAFF_EDIT_TOTAL_SKILL_LEVEL,
                    "gui.item.staff.config_requirements.total_skill_level.title",
                    PhaseSixPermissions.CONFIG_EDIT, false, revision, prestigeLevel, null, null);
            actions.add(edit);
            items.add(GuiDisplayItem.action(slot, GuiItemIcon.REQUIREMENTS, edit.label(), lore,
                    edit.actionId(), false));
            return;
        }
        MessageReference title = m("gui.item.staff.config_requirements.total_skill_level.title");
        if (requirement.editable()) {
            lore.add(m("gui.item.staff.config_requirements.permission"));
            items.add(GuiDisplayItem.display(slot, GuiItemIcon.REQUIREMENTS, title, lore));
            return;
        }
        addReadOnlyRequirement(items, slot, GuiItemIcon.REQUIREMENTS, title, lore.getFirst());
    }

    private static void addReadOnlyRequirement(
            List<GuiDisplayItem> items,
            int slot,
            GuiItemIcon icon,
            MessageReference title,
            MessageReference current) {
        items.add(GuiDisplayItem.display(slot, icon, title, List.of(
                current,
                m("gui.item.staff.config_requirements.read_only"),
                m("gui.item.staff.config_requirements.read_only.edit"))));
    }

    private GuiSessionView totalSkillLevelInput(PermissionSubject subject, long prestigeLevel) {
        return numericInput(subject, configurationAdministration().totalSkillLevelInput(subject, prestigeLevel),
                GuiActionKind.STAFF_REVIEW_TOTAL_SKILL_LEVEL, GuiScreenKind.STAFF_TOTAL_SKILL_LEVEL_EDITOR,
                GuiItemIcon.REQUIREMENTS, "gui.title.staff.total_skill_level",
                GuiActionKind.STAFF_BACK_PRESTIGE_REQUIREMENTS);
    }

    private CompletionStage<PlayerGuiInteractionResult> reviewTotalSkillLevel(
            PermissionSubject subject,
            GuiAction action,
            String target,
            ConfigRevisionId expectedRevision) {
        long prestigeLevel = configurationLevel(action);
        return configurationAdministration().reviewTotalSkillLevel(
                subject, prestigeLevel, target, expectedRevision)
                .thenApply(review -> PlayerGuiInteractionResult.navigate(totalSkillLevelReview(subject, review)));
    }

    private GuiSessionView totalSkillLevelReview(
            PermissionSubject subject,
            GuidedTotalSkillLevelReview review) {
        Optional<ConfigRevisionId> revision = Optional.of(review.baseRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(13, GuiItemIcon.REQUIREMENTS,
                m("gui.item.staff.total_skill_level.review.title"), List.of(
                        m("gui.item.staff.total_skill_level.review.level", "level", review.prestigeLevel()),
                        m("gui.item.staff.total_skill_level.review.change",
                                "before", review.currentTarget(), "after", review.newTarget()))));
        GuiAction confirm = configurationAction(GuiActionKind.STAFF_CONFIRM_TOTAL_SKILL_LEVEL,
                "gui.action.staff.total_skill_level.confirm", PhaseSixPermissions.CONFIG_APPLY, true, revision,
                review.prestigeLevel(), null, review.reviewId());
        actions.add(confirm);
        items.add(GuiDisplayItem.action(22, GuiItemIcon.CONFIRM, confirm.label(), List.of(),
                confirm.actionId(), true));
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_REQUIREMENTS, revision,
                review.prestigeLevel(), 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.total_skill_level_review", "level", review.prestigeLevel()), actions,
                GuiScreenKind.STAFF_TOTAL_SKILL_LEVEL_REVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> confirmTotalSkillLevel(
            PermissionSubject subject,
            UUID reviewId) {
        return configurationAdministration().confirmTotalSkillLevel(subject, reviewId).thenApply(result ->
                PlayerGuiInteractionResult.navigate(prestigeRequirements(subject, result.prestigeLevel()),
                        m("gui.staff.total_skill_level.applied", "level", result.prestigeLevel(),
                                "value", result.newTarget())));
    }

    private GuiSessionView moneyInput(PermissionSubject subject, long prestigeLevel) {
        return numericInput(subject, configurationAdministration().moneyInput(subject, prestigeLevel),
                GuiActionKind.STAFF_REVIEW_PRESTIGE_MONEY, GuiScreenKind.STAFF_PRESTIGE_MONEY_EDITOR,
                GuiItemIcon.BALANCE, "gui.title.staff.money", GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL);
    }

    private CompletionStage<PlayerGuiInteractionResult> reviewMoney(
            PermissionSubject subject,
            GuiAction action,
            String amount,
            ConfigRevisionId expectedRevision) {
        long prestigeLevel = configurationLevel(action);
        return configurationAdministration().reviewMoney(subject, prestigeLevel, amount, expectedRevision)
                .thenApply(review -> PlayerGuiInteractionResult.navigate(moneyReview(subject, review)));
    }

    private GuiSessionView moneyReview(PermissionSubject subject, GuidedMoneyConfigurationReview review) {
        Optional<ConfigRevisionId> revision = Optional.of(review.baseRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(13, GuiItemIcon.BALANCE,
                m("gui.item.staff.money.review.title"), List.of(
                        m("gui.item.staff.money.review.level", "level", review.prestigeLevel()),
                        m("gui.item.staff.money.review.change", "before", review.currentAmount(),
                                "after", review.newAmount()))));
        GuiAction confirm = configurationAction(GuiActionKind.STAFF_CONFIRM_PRESTIGE_MONEY,
                "gui.action.staff.money.confirm", PhaseSixPermissions.CONFIG_APPLY, true, revision,
                review.prestigeLevel(), null, review.reviewId());
        actions.add(confirm);
        items.add(GuiDisplayItem.action(22, GuiItemIcon.CONFIRM, confirm.label(), List.of(),
                confirm.actionId(), true));
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL, revision,
                review.prestigeLevel(), 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.money_review", "level", review.prestigeLevel()),
                actions, GuiScreenKind.STAFF_PRESTIGE_MONEY_REVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> confirmMoney(PermissionSubject subject, UUID reviewId) {
        return configurationAdministration().confirmMoney(subject, reviewId).thenApply(result ->
                PlayerGuiInteractionResult.navigate(configuration(subject),
                        m("gui.staff.money.applied", "level", result.prestigeLevel(),
                                "amount", result.newAmount())));
    }

    private GuiSessionView rewardInput(PermissionSubject subject, long prestigeLevel) {
        return numericInput(subject, configurationAdministration().rewardInput(subject, prestigeLevel),
                GuiActionKind.STAFF_REVIEW_PRESTIGE_REWARD, GuiScreenKind.STAFF_PRESTIGE_REWARD_EDITOR,
                GuiItemIcon.REWARD, "gui.title.staff.reward", GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL);
    }

    private CompletionStage<PlayerGuiInteractionResult> reviewReward(
            PermissionSubject subject,
            GuiAction action,
            String amount,
            ConfigRevisionId expectedRevision) {
        long prestigeLevel = configurationLevel(action);
        return configurationAdministration().reviewReward(subject, prestigeLevel, amount, expectedRevision)
                .thenApply(review -> PlayerGuiInteractionResult.navigate(rewardReview(subject, review)));
    }

    private GuiSessionView rewardReview(PermissionSubject subject, GuidedRewardConfigurationReview review) {
        Optional<ConfigRevisionId> revision = Optional.of(review.baseRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(13, GuiItemIcon.REWARD,
                m("gui.item.staff.reward.review.title"), List.of(
                        m("gui.item.staff.reward.review.level", "level", review.prestigeLevel()),
                        m("gui.item.staff.reward.review.change", "before", review.currentAmount(),
                                "after", review.newAmount()))));
        GuiAction confirm = configurationAction(GuiActionKind.STAFF_CONFIRM_PRESTIGE_REWARD,
                "gui.action.staff.reward.confirm", PhaseSixPermissions.CONFIG_APPLY, true, revision,
                review.prestigeLevel(), null, review.reviewId());
        actions.add(confirm);
        items.add(GuiDisplayItem.action(22, GuiItemIcon.CONFIRM, confirm.label(), List.of(),
                confirm.actionId(), true));
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL, revision,
                review.prestigeLevel(), 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.reward_review", "level", review.prestigeLevel()),
                actions, GuiScreenKind.STAFF_PRESTIGE_REWARD_REVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> confirmReward(PermissionSubject subject, UUID reviewId) {
        return configurationAdministration().confirmReward(subject, reviewId).thenApply(result ->
                PlayerGuiInteractionResult.navigate(configuration(subject),
                        m("gui.staff.reward.applied", "level", result.prestigeLevel(),
                                "amount", result.newAmount())));
    }

    private GuiSessionView scaling(PermissionSubject subject, long prestigeLevel) {
        GuidedScalingConfigurationView scaling = configurationAdministration().scaling(subject, prestigeLevel);
        Optional<ConfigRevisionId> revision = Optional.of(scaling.revision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        ArrayList<MessageReference> summary = new ArrayList<>();
        summary.add(m("gui.item.staff.scaling.type", "mode", humanize(scaling.mode().name())));
        summary.add(m("gui.item.staff.scaling.effective", "level", prestigeLevel,
                "value", scaling.effectiveValue()));
        if (scaling.complex()) {
            summary.add(m("gui.item.separator"));
            summary.add(m("gui.item.staff.scaling.complex"));
            summary.add(m("gui.item.staff.scaling.yaml"));
        }
        items.add(GuiDisplayItem.display(4, GuiItemIcon.CONFIGURATION,
                m("gui.item.staff.scaling.title"), summary));
        List<MessageReference> baseLore = new ArrayList<>();
        baseLore.add(m("gui.item.staff.scaling.value", "value", scaling.base()));
        boolean canEditBase = scaling.editable(GuidedScalingParameter.LINEAR_BASE)
                && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                && subject.has(PhaseSixPermissions.CONFIG_APPLY);
        if (canEditBase) {
            GuiAction editBase = configurationAction(GuiActionKind.STAFF_EDIT_SCALING_LINEAR_BASE,
                    "gui.item.staff.scaling.base.title", PhaseSixPermissions.CONFIG_EDIT,
                    false, revision, prestigeLevel, null, null);
            actions.add(editBase);
            baseLore.add(m("gui.item.staff.scaling.base.edit"));
            items.add(GuiDisplayItem.action(10, GuiItemIcon.CONFIGURATION, editBase.label(), baseLore,
                    editBase.actionId(), false));
        } else {
            baseLore.add(m(scaling.complex() ? "gui.item.staff.scaling.complex_short"
                    : "gui.item.staff.scaling.read_only"));
            items.add(GuiDisplayItem.display(10, GuiItemIcon.CONFIGURATION,
                    m("gui.item.staff.scaling.base.title"), baseLore));
        }
        if (scaling.mode() == net.maddkraft.maddprestige.core.scaling.SegmentScalingMode.LINEAR) {
            List<MessageReference> lore = new ArrayList<>();
            lore.add(m("gui.item.staff.scaling.value", "value", scaling.rate()));
            boolean canEdit = scaling.editable(GuidedScalingParameter.LINEAR_INCREMENT)
                    && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                    && subject.has(PhaseSixPermissions.CONFIG_APPLY);
            if (canEdit) {
                GuiAction edit = configurationAction(GuiActionKind.STAFF_EDIT_SCALING_LINEAR_INCREMENT,
                        "gui.item.staff.scaling.increment.title", PhaseSixPermissions.CONFIG_EDIT,
                        false, revision, prestigeLevel, null, null);
                actions.add(edit);
                lore.add(m("gui.item.staff.scaling.edit"));
                items.add(GuiDisplayItem.action(13, GuiItemIcon.INCREASE, edit.label(), lore,
                        edit.actionId(), false));
            } else {
                lore.add(m(scaling.complex() ? "gui.item.staff.scaling.complex_short"
                        : "gui.item.staff.scaling.read_only"));
                items.add(GuiDisplayItem.display(13, GuiItemIcon.INCREASE,
                        m("gui.item.staff.scaling.increment.title"), lore));
            }
        }
        if (scaling.overrideValue().isPresent() || scaling.overrideManageable()) {
            List<MessageReference> lore = new ArrayList<>();
            scaling.overrideValue().ifPresentOrElse(
                    value -> lore.add(m("gui.item.staff.scaling.value", "value", value)),
                    () -> lore.add(m("gui.item.staff.scaling.override.inherited")));
            boolean canManage = scaling.overrideManageable()
                    && subject.has(PhaseSixPermissions.CONFIG_EDIT)
                    && subject.has(PhaseSixPermissions.CONFIG_APPLY);
            if (canManage) {
                GuiAction manage = configurationAction(GuiActionKind.STAFF_MANAGE_SCALING_OVERRIDE,
                        "gui.item.staff.scaling.override.title", PhaseSixPermissions.CONFIG_EDIT,
                        false, revision, prestigeLevel, null, null);
                actions.add(manage);
                lore.add(m(scaling.overrideValue().isPresent()
                        ? "gui.item.staff.scaling.override.manage"
                        : "gui.item.staff.scaling.override.add"));
                items.add(GuiDisplayItem.action(16, GuiItemIcon.PRESTIGE, manage.label(), lore,
                        manage.actionId(), false));
            } else {
                lore.add(m(scaling.complex() ? "gui.item.staff.scaling.complex_short"
                        : "gui.item.staff.scaling.read_only"));
                items.add(GuiDisplayItem.display(16, GuiItemIcon.PRESTIGE,
                        m("gui.item.staff.scaling.override.title"), lore));
            }
        }
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL, revision,
                prestigeLevel, 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.scaling", "level", prestigeLevel),
                actions, GuiScreenKind.STAFF_PRESTIGE_SCALING, SIZE, items);
    }

    private GuiSessionView scalingOverride(PermissionSubject subject, long prestigeLevel) {
        GuidedScalingConfigurationView scaling = configurationAdministration().scaling(subject, prestigeLevel);

        Optional<ConfigRevisionId> revision = Optional.of(scaling.revision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        MessageReference current = scaling.overrideValue()
                .map(value -> m("gui.item.staff.scaling.override.current", "current", value))
                .orElseGet(() -> m("gui.item.staff.scaling.override.current.inherited"));
        items.add(GuiDisplayItem.display(4, GuiItemIcon.PRESTIGE,
                m("gui.item.staff.scaling.override.current.title"), List.of(current)));
        if (scaling.overrideValue().isPresent()) {
            GuiAction edit = configurationAction(GuiActionKind.STAFF_EDIT_SCALING_OVERRIDE,
                    "gui.action.staff.scaling.override.edit", PhaseSixPermissions.CONFIG_EDIT,
                    false, revision, prestigeLevel, null, null);
            actions.add(edit);
            items.add(GuiDisplayItem.action(11, GuiItemIcon.CONFIGURATION, edit.label(), List.of(),
                    edit.actionId(), false));
            GuiAction remove = configurationAction(GuiActionKind.STAFF_REMOVE_SCALING_OVERRIDE,
                    "gui.action.staff.scaling.override.remove", PhaseSixPermissions.CONFIG_EDIT,
                    false, revision, prestigeLevel, null, null);
            actions.add(remove);
            items.add(GuiDisplayItem.action(15, GuiItemIcon.BLOCKED, remove.label(), List.of(),
                    remove.actionId(), false));
        } else {
            GuiAction add = configurationAction(GuiActionKind.STAFF_ADD_SCALING_OVERRIDE,
                    "gui.action.staff.scaling.override.add", PhaseSixPermissions.CONFIG_EDIT,
                    false, revision, prestigeLevel, null, null);
            actions.add(add);
            items.add(GuiDisplayItem.action(13, GuiItemIcon.CONFIRM, add.label(), List.of(),
                    add.actionId(), false));
        }
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_SCALING, revision,
                prestigeLevel, 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.scaling_override", "level", prestigeLevel), actions,
                GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE, SIZE, items);
    }

    private GuiSessionView scalingOverrideInput(PermissionSubject subject, long prestigeLevel) {
        return numericInput(subject, configurationAdministration().scalingOverrideInput(subject, prestigeLevel),
                GuiActionKind.STAFF_REVIEW_SCALING_OVERRIDE,
                GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE_EDITOR, GuiItemIcon.PRESTIGE,
                "gui.title.staff.scaling_override_input", GuiActionKind.STAFF_BACK_SCALING_OVERRIDE);
    }

    private CompletionStage<PlayerGuiInteractionResult> reviewScalingOverride(
            PermissionSubject subject,
            GuiAction action,
            String value,
            ConfigRevisionId expectedRevision) {
        long prestigeLevel = configurationLevel(action);
        return configurationAdministration().reviewScalingOverride(
                subject, prestigeLevel, value, expectedRevision)
                .thenApply(review -> PlayerGuiInteractionResult.navigate(scalingOverrideReview(subject, review)));
    }

    private CompletionStage<PlayerGuiInteractionResult> reviewScalingOverrideRemoval(
            PermissionSubject subject,
            GuiAction action) {
        long prestigeLevel = configurationLevel(action);
        return configurationAdministration().reviewScalingOverrideRemoval(
                subject, prestigeLevel, configurationRevision(action))
                .thenApply(review -> PlayerGuiInteractionResult.navigate(scalingOverrideReview(subject, review)));
    }

    private GuiSessionView scalingOverrideReview(
            PermissionSubject subject,
            GuidedScalingOverrideReview review) {
        Optional<ConfigRevisionId> revision = Optional.of(review.baseRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        ArrayList<MessageReference> lore = new ArrayList<>();
        lore.add(review.currentOverride()
                .map(value -> m("gui.item.staff.scaling.override.review.current", "current", value))
                .orElseGet(() -> m("gui.item.staff.scaling.override.review.current.inherited")));
        lore.add(review.removal()
                ? m("gui.item.staff.scaling.override.review.inherited")
                : m("gui.item.staff.scaling.override.review.new",
                        "value", review.newOverride().orElseThrow()));
        if (review.currentEffectiveValue().isPresent() && review.newEffectiveValue().isPresent()) {
            lore.add(m("gui.item.staff.scaling.override.review.effective",
                    "before", review.currentEffectiveValue().orElseThrow(),
                    "after", review.newEffectiveValue().orElseThrow()));
        }
        items.add(GuiDisplayItem.display(13, GuiItemIcon.PRESTIGE,
                m(review.addition() ? "gui.item.staff.scaling.override.review.add.title"
                        : "gui.item.staff.scaling.override.review.title"), lore));
        GuiActionKind confirmKind = review.removal()
                ? GuiActionKind.STAFF_CONFIRM_SCALING_OVERRIDE_REMOVAL
                : GuiActionKind.STAFF_CONFIRM_SCALING_OVERRIDE_EDIT;
        String confirmLabel = review.removal()
                ? "gui.action.staff.scaling.override.remove.confirm"
                : "gui.action.staff.scaling.confirm";
        GuiItemIcon confirmIcon = review.removal() ? GuiItemIcon.BLOCKED : GuiItemIcon.CONFIRM;
        GuiAction confirm = configurationAction(confirmKind, confirmLabel,
                PhaseSixPermissions.CONFIG_APPLY, true, revision,
                review.prestigeLevel(), null, review.reviewId());
        actions.add(confirm);
        items.add(GuiDisplayItem.action(22, confirmIcon, confirm.label(), List.of(),
                confirm.actionId(), true));
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_SCALING_OVERRIDE, revision,
                review.prestigeLevel(), 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.scaling_override_review", "level", review.prestigeLevel()),
                actions, GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE_REVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> confirmScalingOverride(
            PermissionSubject subject,
            UUID reviewId) {
        return configurationAdministration().confirmScalingOverride(subject, reviewId).thenApply(result -> {
            String message = result.removal()
                    ? "gui.staff.scaling.override.removed" : "gui.staff.scaling.override.applied";
            Object value = result.removal() ? "inherited" : result.newOverride().orElseThrow();
            return PlayerGuiInteractionResult.navigate(scaling(subject, result.prestigeLevel()),
                    m(message, "level", result.prestigeLevel(), "value", value));
        });
    }

    private GuiSessionView scalingInput(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter) {
        GuiActionKind submitKind = parameter == GuidedScalingParameter.LINEAR_BASE
                ? GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_BASE
                : GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_INCREMENT;
        GuiItemIcon icon = parameter == GuidedScalingParameter.LINEAR_BASE
                ? GuiItemIcon.CONFIGURATION : GuiItemIcon.INCREASE;
        String titleKey = parameter == GuidedScalingParameter.LINEAR_BASE
                ? "gui.title.staff.scaling_base_input" : "gui.title.staff.scaling_input";
        return numericInput(subject, configurationAdministration().scalingInput(subject, prestigeLevel, parameter),
                submitKind, GuiScreenKind.STAFF_PRESTIGE_SCALING_EDITOR, icon, titleKey,
                GuiActionKind.STAFF_BACK_PRESTIGE_SCALING);
    }

    private CompletionStage<PlayerGuiInteractionResult> reviewScaling(
            PermissionSubject subject,
            GuiAction action,
            String value,
            ConfigRevisionId expectedRevision) {
        long prestigeLevel = configurationLevel(action);
        return configurationAdministration().reviewScaling(subject, prestigeLevel,
                scalingParameter(action.kind()), value, expectedRevision)
                .thenApply(review -> PlayerGuiInteractionResult.navigate(scalingReview(subject, review)));
    }

    private GuiSessionView scalingReview(
            PermissionSubject subject,
            GuidedScalingConfigurationReview review) {
        Optional<ConfigRevisionId> revision = Optional.of(review.baseRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        boolean base = review.parameter() == GuidedScalingParameter.LINEAR_BASE;
        GuiItemIcon icon = base ? GuiItemIcon.CONFIGURATION : GuiItemIcon.INCREASE;
        String reviewTitle = base ? "gui.item.staff.scaling.base.review.title"
                : "gui.item.staff.scaling.review.title";
        items.add(GuiDisplayItem.display(13, icon, m(reviewTitle), List.of(
                m("gui.item.staff.scaling.review.level", "level", review.prestigeLevel()),
                m("gui.item.staff.scaling.review.change", "before", review.currentValue(),
                        "after", review.newValue()))));
        GuiActionKind confirmKind = base ? GuiActionKind.STAFF_CONFIRM_SCALING_LINEAR_BASE
                : GuiActionKind.STAFF_CONFIRM_SCALING_LINEAR_INCREMENT;
        GuiAction confirm = configurationAction(confirmKind,
                "gui.action.staff.scaling.confirm", PhaseSixPermissions.CONFIG_APPLY, true, revision,
                review.prestigeLevel(), null, review.reviewId());
        actions.add(confirm);
        items.add(GuiDisplayItem.action(22, GuiItemIcon.CONFIRM, confirm.label(), List.of(),
                confirm.actionId(), true));
        addConfigurationBack(actions, items, GuiActionKind.STAFF_BACK_PRESTIGE_SCALING, revision,
                review.prestigeLevel(), 0);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.scaling_review", "level", review.prestigeLevel()),
                actions, GuiScreenKind.STAFF_PRESTIGE_SCALING_REVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> confirmScaling(
            PermissionSubject subject,
            UUID reviewId) {
        return configurationAdministration().confirmScaling(subject, reviewId).thenApply(result -> {
            String message = result.parameter() == GuidedScalingParameter.LINEAR_BASE
                    ? "gui.staff.scaling.base.applied" : "gui.staff.scaling.applied";
            return PlayerGuiInteractionResult.navigate(scaling(subject, result.prestigeLevel()),
                    m(message, "level", result.prestigeLevel(), "value", result.newValue()));
        });
    }

    private GuiSessionView numericInput(
            PermissionSubject subject,
            GuidedNumericConfigurationInput input,
            GuiActionKind submitKind,
            GuiScreenKind screen,
            GuiItemIcon valueIcon,
            String titleKey,
            GuiActionKind backKind) {
        Optional<ConfigRevisionId> revision = Optional.of(input.revision());
        GuiAction back = configurationAction(backKind,
                "gui.action.back", PhaseSixPermissions.CONFIG_VIEW, false, revision,
                input.prestigeLevel(), null, null, 0);
        GuiAction submit = configurationAction(submitKind, "gui.action.staff.numeric_input.review",
                PhaseSixPermissions.CONFIG_EDIT, false, revision, input.prestigeLevel(), null, null);
        List<GuiAction> actions = List.of(back, submit);
        List<GuiDisplayItem> items = List.of(
                GuiDisplayItem.display(0, valueIcon,
                        m("gui.item.staff.numeric_input.value", "value", input.currentValue()), List.of()),
                GuiDisplayItem.action(1, GuiItemIcon.BACK, back.label(), List.of(), back.actionId(), false),
                GuiDisplayItem.action(2, GuiItemIcon.CONFIRM, submit.label(),
                        List.of(m("gui.item.staff.numeric_input.review")), submit.actionId(), true));
        return sessions.storeStaffTextInput(subject,
                m(titleKey, "level", input.prestigeLevel()), actions, screen, items,
                new GuiTextInput(input.currentValue()));
    }

    private CompletionStage<PlayerGuiInteractionResult> history(
            PermissionSubject subject,
            int pageIndex) {
        int offset = Math.multiplyExact(pageIndex, PAGE_SIZE);
        return history.recent(offset, PAGE_SIZE).thenApply(page -> {
            if (pageIndex > 0 && page.entries().isEmpty()) {
                throw new IllegalStateException("Server-owned History page is outside the current projection");
            }
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m("gui.item.staff.history.summary", "count", page.entries().size()));
            long exceptions = page.entries().stream()
                    .filter(entry -> entry.outcome() != StaffHistorySource.Outcome.COMPLETED)
                    .count();
            summary.add(m("gui.item.staff.history.exceptions", "count", exceptions));
            if (page.hasPrevious() || page.hasNext()) {
                summary.add(m("gui.item.staff.history.page", "current", pageIndex + 1));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.HISTORY,
                    m("gui.item.staff.history.summary.title"), summary));
            if (page.entries().isEmpty()) {
                items.add(GuiDisplayItem.display(13, GuiItemIcon.HISTORY,
                        m("gui.item.staff.history.empty.title"),
                        List.of(m("gui.item.staff.history.empty.lore"))));
            } else {
                int firstSlot = 10 + Math.max(0, (PAGE_SIZE - page.entries().size()) / 2);
                for (int index = 0; index < page.entries().size(); index++) {
                    Entry entry = page.entries().get(index);
                    ArrayList<MessageReference> lore = new ArrayList<>();
                    if (entry.kind() != StaffHistorySource.Kind.NORMAL_PRESTIGE) {
                        lore.add(m(StaffHistoryPresentation.guiKindKey(entry.kind()),
                                "type", StaffHistoryPresentation.kindText(entry.kind())));
                        entry.actorName().ifPresent(actor -> lore.add(
                                m("gui.item.staff.history.actor", "player", actor)));
                    }
                    lore.add(m("gui.item.staff.history.transition", "before", entry.before(),
                            "after", entry.after()));
                    lore.add(m(StaffHistoryPresentation.guiOutcomeKey(entry.outcome()),
                            "status", StaffHistoryPresentation.outcomeText(entry.outcome())));
                    lore.add(m("gui.item.staff.history.time", "value",
                            StaffHistoryPresentation.timestamp(entry.occurredAt())));
                    items.add(GuiDisplayItem.display(firstSlot + index, GuiItemIcon.HISTORY,
                            m(entry.kind() == StaffHistorySource.Kind.NORMAL_PRESTIGE
                                            ? "gui.item.staff.history.entry.title"
                                            : "gui.item.staff.history.admin_entry.title",
                                    "player", entry.player(),
                                    "type", StaffHistoryPresentation.kindText(entry.kind())), lore));
                }
            }
            if (page.hasPrevious()) {
                addPage(actions, items, GuiActionKind.STAFF_HISTORY_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex - 1);
            }
            if (page.hasNext()) {
                addPage(actions, items, GuiActionKind.STAFF_HISTORY_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.history"), actions, GuiScreenKind.STAFF_HISTORY, SIZE, items));
        });
    }

    private CompletionStage<PlayerGuiInteractionResult> systemStatus(
            PermissionSubject subject,
            int pageIndex) {
        return systemStatus.inspect().thenApply(snapshot -> {
            int pageCount = Math.max(1,
                    (snapshot.components().size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IllegalStateException("Server-owned System Status page is outside the current projection");
            }
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m("gui.item.staff.system_status.summary", "status",
                    statusText(snapshot.summary().health())));
            summary.add(m("gui.item.staff.overview.providers",
                    "current", snapshot.summary().availableProviders(),
                    "total", snapshot.summary().totalProviders()));
            if (pageCount > 1) {
                summary.add(m("gui.item.staff.system_status.page", "current", pageIndex + 1,
                        "total", pageCount));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.SYSTEM_STATUS,
                    m(statusSummaryKey(snapshot.summary().health())), summary));
            if (snapshot.components().isEmpty()) {
                items.add(GuiDisplayItem.display(13, GuiItemIcon.SYSTEM_STATUS,
                        m("gui.item.staff.system_status.no_integrations.title"),
                        List.of(m("gui.item.staff.system_status.no_integrations.lore"))));
            } else {
                int first = pageIndex * PAGE_SIZE;
                int last = Math.min(first + PAGE_SIZE, snapshot.components().size());
                int firstSlot = 10 + (PAGE_SIZE - (last - first)) / 2;
                for (int index = first; index < last; index++) {
                    Component component = snapshot.components().get(index);
                    ArrayList<MessageReference> lore = new ArrayList<>();
                    lore.add(m(statusLineKey(component.health()), "status", component.status()));
                    if (!component.detail().isBlank()) {
                        lore.add(m("gui.item.staff.system_status.component.detail",
                                "detail", component.detail()));
                    }
                    items.add(GuiDisplayItem.display(firstSlot + index - first,
                            component.name().equalsIgnoreCase("Configuration")
                                    ? GuiItemIcon.CONFIGURATION : GuiItemIcon.SYSTEM_STATUS,
                            m(statusComponentKey(component.health()), "component", component.name()), lore));
                }
            }
            if (pageIndex > 0) {
                addPage(actions, items, GuiActionKind.STAFF_SYSTEM_STATUS_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex - 1);
            }
            if (pageIndex + 1 < pageCount) {
                addPage(actions, items, GuiActionKind.STAFF_SYSTEM_STATUS_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.ADMIN_GUI,
                        revision, null, pageIndex + 1);
            }
            addRefresh(actions, items, GuiActionKind.STAFF_REFRESH_SYSTEM_STATUS,
                    revision, null, pageIndex);
            addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.system_status"), actions,
                    GuiScreenKind.STAFF_SYSTEM_STATUS, SIZE, items));
        });
    }

    private GuiSessionView playerManagement(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        GuiAction online = action(GuiActionKind.STAFF_OPEN_ONLINE_PLAYERS,
                "gui.action.staff.online_players", PhaseSixPermissions.PLAYER_VIEW, revision, null);
        actions.add(online);
        items.add(GuiDisplayItem.action(11, GuiItemIcon.PLAYERS, online.label(),
                List.of(m("gui.item.staff.online_players.lore")), online.actionId(), false));
        GuiAction find = action(GuiActionKind.STAFF_FIND_PLAYER,
                "gui.action.staff.find_player", PhaseSixPermissions.PLAYER_VIEW, revision, null);
        actions.add(find);
        items.add(GuiDisplayItem.action(15, GuiItemIcon.PLAYER_INFORMATION, find.label(),
                List.of(m("gui.item.staff.find_player.lore")), find.actionId(), false));
        addBack(actions, items, GuiActionKind.STAFF_BACK_DASHBOARD, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.player_management"), actions,
                GuiScreenKind.STAFF_PLAYER_MANAGEMENT, SIZE, items);
    }

    private GuiSessionView onlinePlayerSelection(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        List<StaffPlayerIdentity> online = players.onlinePlayers().stream()
                .sorted(Comparator.comparing(StaffPlayerIdentity::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StaffPlayerIdentity::playerId))
                .limit(7)
                .toList();
        if (online.isEmpty()) {
            items.add(GuiDisplayItem.display(13, GuiItemIcon.PLAYERS,
                    m("gui.item.staff.no_players.title"), List.of(m("gui.item.staff.no_players.lore"))));
        } else {
            int firstSlot = 10 + Math.max(0, (7 - online.size()) / 2);
            for (int index = 0; index < online.size(); index++) {
                StaffPlayerIdentity player = online.get(index);
                GuiAction select = action(GuiActionKind.STAFF_SELECT_PLAYER,
                        "gui.action.staff.select_player", PhaseSixPermissions.PLAYER_VIEW,
                        revision, player.playerId());
                actions.add(select);
                items.add(GuiDisplayItem.profiledAction(firstSlot + index, GuiItemIcon.PLAYERS,
                        m("gui.item.staff.player.title", "player", player.name()),
                        List.of(m("gui.item.staff.player.selection.online"),
                                m("gui.item.staff.player.selection.inspect")),
                        select.actionId(), false, player.playerId()));
            }
        }
        addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_MANAGEMENT, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.online_players"), actions,
                GuiScreenKind.STAFF_PLAYER_SELECTION, SIZE, items);
    }

    private GuiSessionView searchResults(PermissionSubject subject, String selector) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        List<StaffPlayerIdentity> matches = players.searchPlayers(selector).stream().limit(7).toList();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        if (matches.isEmpty()) {
            items.add(GuiDisplayItem.display(13, GuiItemIcon.PLAYER_INFORMATION,
                    m("gui.item.staff.find_player.empty.title"),
                    List.of(m("gui.item.staff.find_player.empty.lore", "player", selector))));
        } else {
            int firstSlot = 10 + Math.max(0, (7 - matches.size()) / 2);
            for (int index = 0; index < matches.size(); index++) {
                StaffPlayerIdentity player = matches.get(index);
                GuiAction select = action(GuiActionKind.STAFF_SELECT_SEARCH_RESULT,
                        "gui.action.staff.select_player", PhaseSixPermissions.PLAYER_VIEW,
                        revision, player.playerId());
                actions.add(select);
                ArrayList<MessageReference> lore = new ArrayList<>();
                lore.add(m(player.online()
                        ? "gui.item.staff.player.selection.online"
                        : "gui.item.staff.player.selection.offline"));
                lore.add(m("gui.item.staff.player.selection.inspect"));
                long sameName = matches.stream().filter(candidate ->
                        candidate.name().equalsIgnoreCase(player.name())).count();
                if (sameName > 1) {
                    lore.add(m("gui.item.staff.player.uuid", "uuid", player.playerId()));
                }
                items.add(GuiDisplayItem.profiledAction(firstSlot + index, GuiItemIcon.PLAYERS,
                        m("gui.item.staff.player.title", "player", player.name()), lore,
                        select.actionId(), false, player.playerId()));
            }
        }
        addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_MANAGEMENT, revision, null);
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.find_player"), actions,
                GuiScreenKind.STAFF_PLAYER_SEARCH_RESULTS, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> overview(
            PermissionSubject subject,
            UUID playerId) {
        StaffPlayerIdentity identity = known(playerId);
        return overview(subject, identity, !identity.online());
    }

    private CompletionStage<PlayerGuiInteractionResult> overview(
            PermissionSubject subject,
            UUID playerId,
            boolean fromSearch) {
        return overview(subject, known(playerId), fromSearch);
    }

    private CompletionStage<PlayerGuiInteractionResult> overview(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            boolean fromSearch) {
        UUID playerId = identity.playerId();
        return progress.inspect(subject, playerId).handle((view, failure) -> {
            if (failure == null) {
                return PlayerGuiInteractionResult.navigate(
                        overviewView(subject, identity, view.prestige(), fromSearch));
            }
            if (!identity.online() && playerPrestigeStateUnavailable(failure)) {
                return PlayerGuiInteractionResult.navigate(
                        unavailableOverviewView(subject, identity, fromSearch));
            }
            throw propagate(failure);
        });
    }

    private GuiSessionView unavailableOverviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            boolean fromSearch) {
        Optional<ConfigRevisionId> revision = activeRevision.get();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        items.add(GuiDisplayItem.display(4, GuiItemIcon.PROGRESS,
                m("gui.item.progress.title"), List.of(m("gui.item.staff.player.state_unavailable"))));
        items.add(GuiDisplayItem.display(10, GuiItemIcon.BALANCE,
                m("gui.item.balance.title"), List.of(
                        m("gui.item.balance.current", "current", "Unavailable"))));
        items.add(GuiDisplayItem.display(13, GuiItemIcon.REQUIREMENTS,
                m("gui.item.requirements.title"), List.of(
                        m("gui.item.change.value", "value", "Unavailable"))));
        items.add(GuiDisplayItem.display(16, GuiItemIcon.REWARD,
                m("gui.item.rewards.title"), List.of(
                        m("gui.item.change.value", "value", "Unavailable"))));
        items.add(GuiDisplayItem.display(20, GuiItemIcon.PRESTIGE,
                m("gui.action.staff.prestige_preview"), List.of(
                        m("gui.item.staff.prestige_preview.unavailable"))));
        addOverviewUtilities(actions, items, revision, identity, fromSearch);
        GuiAction playerHistory = action(GuiActionKind.STAFF_VIEW_PLAYER_HISTORY,
                "gui.action.staff.player_history", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(playerHistory);
        items.add(GuiDisplayItem.action(24, GuiItemIcon.HISTORY, playerHistory.label(),
                List.of(m("gui.item.staff.player_history.lore")), playerHistory.actionId(), false));
        addManagePlayer(subject, actions, items, revision, identity.playerId());
        addBack(actions, items, fromSearch
                        ? GuiActionKind.STAFF_BACK_PLAYER_SEARCH_RESULTS
                        : GuiActionKind.STAFF_BACK_PLAYER_LIST,
                revision, identity.playerId());
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.player", "player", identity.name()),
                actions, GuiScreenKind.STAFF_PLAYER_OVERVIEW, SIZE, items);
    }

    private GuiSessionView overviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            OperationPreview preview,
            boolean fromSearch) {
        ConfigRevisionId revision = preview.configRevision();
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = PlayerGuiService.commonItems(preview, false);
        items.stream().filter(item -> item.slot() == 20 && item.icon() == GuiItemIcon.MILESTONE)
                .findFirst().ifPresent(milestone -> {
                    items.remove(milestone);
                    items.add(GuiDisplayItem.display(21, milestone.icon(), milestone.title(), milestone.lore()));
                });
        GuiDisplayItem requirements = items.stream()
                .filter(item -> item.icon() == GuiItemIcon.REQUIREMENTS)
                .findFirst().orElseThrow();
        items.remove(requirements);
        GuiAction inspect = action(GuiActionKind.STAFF_VIEW_REQUIREMENTS,
                "gui.action.staff.requirements", PhaseSixPermissions.PLAYER_VIEW,
                Optional.of(revision), identity.playerId());
        actions.add(inspect);
        items.add(GuiDisplayItem.action(requirements.slot(), requirements.icon(), requirements.title(),
                requirements.lore(), inspect.actionId(), requirements.highlighted()));
        addOverviewUtilities(actions, items, Optional.of(revision), identity, fromSearch);
        GuiAction playerPreview = action(GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW,
                "gui.action.staff.prestige_preview", PhaseSixPermissions.PLAYER_VIEW,
                Optional.of(revision), identity.playerId());
        actions.add(playerPreview);
        items.add(GuiDisplayItem.action(20, GuiItemIcon.PRESTIGE, playerPreview.label(),
                List.of(m("gui.item.staff.prestige_preview.lore")), playerPreview.actionId(), false));
        GuiAction playerHistory = action(GuiActionKind.STAFF_VIEW_PLAYER_HISTORY,
                "gui.action.staff.player_history", PhaseSixPermissions.PLAYER_VIEW,
                Optional.of(revision), identity.playerId());
        actions.add(playerHistory);
        items.add(GuiDisplayItem.action(24, GuiItemIcon.HISTORY, playerHistory.label(),
                List.of(m("gui.item.staff.player_history.lore")), playerHistory.actionId(), false));
        addManagePlayer(subject, actions, items, Optional.of(revision), identity.playerId());
        addBack(actions, items, fromSearch
                        ? GuiActionKind.STAFF_BACK_PLAYER_SEARCH_RESULTS
                        : GuiActionKind.STAFF_BACK_PLAYER_LIST,
                Optional.of(revision), identity.playerId());
        addClose(actions, items, Optional.of(revision));
        addBorder(items);
        return sessions.storeStaffScreen(subject, m("gui.title.staff.player", "player", identity.name()),
                actions, GuiScreenKind.STAFF_PLAYER_OVERVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> copyPlayerUuid(
            PermissionSubject subject,
            UUID playerId,
            boolean fromSearch) {
        return overview(subject, playerId, fromSearch).thenApply(result ->
                PlayerGuiInteractionResult.navigate(result.nextView().orElseThrow(),
                        m("gui.staff.player.uuid.copy", "uuid", playerId)));
    }

    private static void addOverviewUtilities(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            Optional<ConfigRevisionId> revision,
            StaffPlayerIdentity identity,
            boolean fromSearch) {
        GuiAction refresh = action(fromSearch
                        ? GuiActionKind.STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW
                        : GuiActionKind.STAFF_REFRESH_PLAYER_OVERVIEW,
                "gui.action.staff.refresh", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(refresh);
        items.add(GuiDisplayItem.action(0, GuiItemIcon.REFRESH, refresh.label(),
                List.of(m("gui.item.staff.refresh.lore")), refresh.actionId(), false));
        GuiAction copy = action(fromSearch
                        ? GuiActionKind.STAFF_COPY_SEARCH_PLAYER_UUID
                        : GuiActionKind.STAFF_COPY_PLAYER_UUID,
                "gui.action.staff.copy_uuid", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(copy);
        items.add(GuiDisplayItem.profiledAction(8, GuiItemIcon.PLAYER_INFORMATION,
                m("gui.item.staff.player.info.title"),
                List.of(m(identity.online()
                                ? "gui.item.staff.player.online"
                                : "gui.item.staff.player.offline"),
                        m("gui.item.staff.player.uuid", "uuid", identity.playerId()),
                        m("gui.item.staff.player.info.copy")),
                copy.actionId(), false, identity.playerId()));
    }

    private void addManagePlayer(
            PermissionSubject subject,
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            Optional<ConfigRevisionId> revision,
            UUID playerId) {
        if (prestigeAdministration.isEmpty() || !canManagePrestige(subject)) {
            return;
        }
        String permission = subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_SET)
                ? PhaseSixPermissions.PLAYER_PRESTIGE_SET
                : PhaseSixPermissions.PLAYER_PRESTIGE_RESET;
        GuiAction manage = action(GuiActionKind.STAFF_MANAGE_PLAYER,
                "gui.action.staff.manage_player", permission, revision, playerId);
        actions.add(manage);
        items.add(GuiDisplayItem.action(22, GuiItemIcon.ADMINISTRATION, manage.label(),
                List.of(m("gui.item.staff.manage_player.lore")), manage.actionId(), false));
    }

    private CompletionStage<PlayerGuiInteractionResult> managePlayer(
            PermissionSubject subject,
            UUID playerId) {
        if (!canManagePrestige(subject)) {
            subject.require(PhaseSixPermissions.PLAYER_PRESTIGE_SET);
        }
        StaffPlayerIdentity identity = known(playerId);
        return prestigeAdministration().inspect(subject, playerId).thenApply(state -> {
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            items.add(GuiDisplayItem.profiledDisplay(4, GuiItemIcon.PLAYERS,
                    m("gui.item.staff.manage_player.summary.title", "player", identity.name()),
                    List.of(m("gui.item.staff.manage_player.current", "current", state.currentPrestige())),
                    identity.playerId()));
            if (subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_SET)) {
                GuiAction set = action(GuiActionKind.STAFF_SET_PRESTIGE,
                        "gui.action.staff.set_prestige", PhaseSixPermissions.PLAYER_PRESTIGE_SET,
                        revision, playerId);
                actions.add(set);
                items.add(GuiDisplayItem.action(11, GuiItemIcon.INCREASE, set.label(),
                        List.of(m("gui.item.staff.set_prestige.lore")), set.actionId(), false));
            }
            if (subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_RESET)) {
                GuiAction reset = action(GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET,
                        "gui.action.staff.reset_prestige", PhaseSixPermissions.PLAYER_PRESTIGE_RESET,
                        revision, playerId);
                actions.add(reset);
                items.add(GuiDisplayItem.action(15, GuiItemIcon.DECREASE, reset.label(),
                        List.of(m("gui.item.staff.reset_prestige.lore")), reset.actionId(), false));
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW, revision, playerId);
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.manage_player", "player", identity.name()), actions,
                    GuiScreenKind.STAFF_PLAYER_MANAGEMENT_ACTIONS, SIZE, items));
        });
    }

    private CompletionStage<PlayerGuiInteractionResult> prestigeTargets(
            PermissionSubject subject,
            UUID playerId,
            int pageIndex) {
        StaffPlayerIdentity identity = known(playerId);
        return prestigeAdministration().targets(subject, playerId, pageIndex, PAGE_SIZE).thenApply(page -> {
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            items.add(GuiDisplayItem.display(4, GuiItemIcon.ADMINISTRATION,
                    m("gui.item.staff.set_prestige.summary.title"), List.of(
                            m("gui.item.staff.manage_player.current", "current", page.currentPrestige()),
                            m("gui.item.staff.set_prestige.page", "current", page.page() + 1))));
            int firstSlot = 10 + Math.max(0, (PAGE_SIZE - page.targets().size()) / 2);
            for (int index = 0; index < page.targets().size(); index++) {
                ManualPrestigeAdjustmentReview review = page.targets().get(index);
                GuiAction choose = prestigeAction(GuiActionKind.STAFF_REVIEW_PRESTIGE_SET,
                        "gui.action.staff.prestige_target", PhaseSixPermissions.PLAYER_PRESTIGE_SET,
                        false, Optional.of(review.configRevision()), playerId, review, pageIndex,
                        "target", review.targetPrestige());
                actions.add(choose);
                items.add(GuiDisplayItem.action(firstSlot + index,
                        review.targetPrestige() > review.currentPrestige()
                                ? GuiItemIcon.INCREASE : GuiItemIcon.DECREASE,
                        choose.label(), List.of(),
                        choose.actionId(), false));
            }
            if (page.hasPrevious()) {
                addPage(actions, items, GuiActionKind.STAFF_ADJUST_PRESTIGE_TARGET,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.PLAYER_PRESTIGE_SET,
                        revision, playerId, pageIndex - 1);
            }
            if (page.hasNext()) {
                addPage(actions, items, GuiActionKind.STAFF_ADJUST_PRESTIGE_TARGET,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.PLAYER_PRESTIGE_SET,
                        revision, playerId, pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_MANAGE_PLAYER, revision, playerId);
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.set_prestige", "player", identity.name()), actions,
                    GuiScreenKind.STAFF_PRESTIGE_SELECTOR, SIZE, items));
        });
    }

    private CompletionStage<PlayerGuiInteractionResult> prestigeResetReview(
            PermissionSubject subject,
            UUID playerId) {
        StaffPlayerIdentity identity = known(playerId);
        return prestigeAdministration().reviewReset(subject, playerId).thenApply(review ->
                PlayerGuiInteractionResult.navigate(prestigeReviewView(subject, identity, review, 0)));
    }

    private GuiSessionView prestigeReviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            ManualPrestigeAdjustmentReview review,
            int selectorPage) {
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = new ArrayList<>();
        String kind = review.kind() == ManualPrestigeAdjustmentKind.SET ? "Set Prestige" : "Reset Prestige";
        items.add(GuiDisplayItem.profiledDisplay(4, GuiItemIcon.PLAYERS,
                m("gui.item.staff.prestige_review.player", "player", identity.name()),
                List.of(m("gui.item.staff.player.uuid", "uuid", identity.playerId())), identity.playerId()));
        items.add(GuiDisplayItem.display(13,
                review.kind() == ManualPrestigeAdjustmentKind.SET
                        ? GuiItemIcon.INCREASE : GuiItemIcon.DECREASE,
                m("gui.item.staff.prestige_review.title", "type", kind), List.of(
                        m("gui.item.staff.prestige_review.current", "current", review.currentPrestige()),
                        m("gui.item.staff.prestige_review.target", "target", review.targetPrestige()))));
        boolean reset = review.kind() == ManualPrestigeAdjustmentKind.RESET;
        GuiAction confirm = prestigeAction(GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT,
                reset ? "gui.action.staff.reset_prestige_adjustment"
                        : "gui.action.staff.confirm_prestige_adjustment",
                review.kind().permission(), true,
                Optional.of(review.configRevision()), identity.playerId(), review, selectorPage);
        actions.add(confirm);
        items.add(GuiDisplayItem.action(22, reset ? GuiItemIcon.BLOCKED : GuiItemIcon.CONFIRM,
                confirm.label(), List.of(),
                confirm.actionId(), false));
        GuiActionKind backKind = review.kind() == ManualPrestigeAdjustmentKind.SET
                ? GuiActionKind.STAFF_BACK_PRESTIGE_SELECTOR : GuiActionKind.STAFF_BACK_MANAGE_PLAYER;
        GuiAction back = new GuiAction(UUID.randomUUID(), backKind, m("gui.action.back"),
                review.kind().permission(), false, Optional.of(review.configRevision()),
                Optional.of(identity.playerId()), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(selectorPage));
        actions.add(back);
        items.add(GuiDisplayItem.action(18, GuiItemIcon.BACK, back.label(), List.of(), back.actionId(), false));
        addClose(actions, items, Optional.of(review.configRevision()));
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m(review.kind() == ManualPrestigeAdjustmentKind.SET
                                ? "gui.title.staff.review_set_prestige"
                                : "gui.title.staff.review_reset_prestige",
                        "player", identity.name()), actions, GuiScreenKind.STAFF_PRESTIGE_ADJUSTMENT_REVIEW,
                SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> confirmPrestigeAdjustment(
            PermissionSubject subject,
            ManualPrestigeAdjustmentReview review) {
        StaffPlayerIdentity identity = known(review.playerId());
        String reason = review.kind() == ManualPrestigeAdjustmentKind.SET
                ? "Staff GUI Set Prestige" : "Staff GUI Reset Prestige";
        return prestigeAdministration().confirm(subject, review, "staff-gui", reason)
                .thenCompose(updated -> managePlayer(subject, review.playerId()).thenApply(result ->
                        PlayerGuiInteractionResult.navigate(result.nextView().orElseThrow(),
                                m("gui.staff.prestige_adjusted", "player", identity.name(),
                                        "current", updated.currentPrestige()))));
    }

    private CompletionStage<PlayerGuiInteractionResult> prestigePreview(
            PermissionSubject subject,
            UUID playerId) {
        StaffPlayerIdentity identity = known(playerId);
        return progress.inspect(subject, playerId).thenApply(view -> PlayerGuiInteractionResult.navigate(
                prestigePreviewView(subject, identity, view.prestige())));
    }

    private GuiSessionView prestigePreviewView(
            PermissionSubject subject,
            StaffPlayerIdentity identity,
            OperationPreview preview) {
        Optional<ConfigRevisionId> revision = Optional.of(preview.configRevision());
        ArrayList<GuiAction> actions = new ArrayList<>();
        ArrayList<GuiDisplayItem> items = PlayerGuiService.staffPreviewItems(preview);
        items.add(GuiDisplayItem.display(22,
                preview.executable() ? GuiItemIcon.READY : GuiItemIcon.BLOCKED,
                m(preview.executable()
                        ? "gui.item.staff.prestige_preview.ready"
                        : "gui.item.staff.prestige_preview.not_ready"), List.of()));
        GuiAction refresh = action(GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW,
                "gui.action.staff.refresh", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(refresh);
        items.add(GuiDisplayItem.action(20, GuiItemIcon.PROGRESS, refresh.label(),
                List.of(m("gui.item.staff.refresh.lore")), refresh.actionId(), false));
        items.add(GuiDisplayItem.profiledDisplay(21, GuiItemIcon.PLAYERS,
                m("gui.item.staff.player.info.title"),
                List.of(m(identity.online()
                                ? "gui.item.staff.player.online"
                                : "gui.item.staff.player.offline"),
                        m("gui.item.staff.player.uuid", "uuid", identity.playerId())),
                identity.playerId()));
        GuiAction playerHistory = action(GuiActionKind.STAFF_VIEW_PLAYER_HISTORY,
                "gui.action.staff.player_history", PhaseSixPermissions.PLAYER_VIEW,
                revision, identity.playerId());
        actions.add(playerHistory);
        items.add(GuiDisplayItem.action(24, GuiItemIcon.HISTORY, playerHistory.label(),
                List.of(m("gui.item.staff.player_history.lore")), playerHistory.actionId(), false));
        addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW,
                revision, identity.playerId());
        addClose(actions, items, revision);
        addBorder(items);
        return sessions.storeStaffScreen(subject,
                m("gui.title.staff.prestige_preview", "player", identity.name()), actions,
                GuiScreenKind.STAFF_PRESTIGE_PREVIEW, SIZE, items);
    }

    private CompletionStage<PlayerGuiInteractionResult> playerHistory(
            PermissionSubject subject,
            UUID playerId,
            int pageIndex) {
        StaffPlayerIdentity identity = known(playerId);
        int offset = Math.multiplyExact(pageIndex, PAGE_SIZE);
        return history.forPlayer(playerId, offset, PAGE_SIZE).thenApply(page -> {
            if (pageIndex > 0 && page.entries().isEmpty()) {
                throw new IllegalStateException("Server-owned Player History page is outside the current projection");
            }
            Optional<ConfigRevisionId> revision = activeRevision.get();
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m("gui.item.staff.player_history.summary", "count", page.entries().size()));
            if (page.hasPrevious() || page.hasNext()) {
                summary.add(m("gui.item.staff.player_history.page", "current", pageIndex + 1));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.HISTORY,
                    m("gui.item.staff.player_history.summary.title"), summary));
            if (page.entries().isEmpty()) {
                items.add(GuiDisplayItem.display(13, GuiItemIcon.HISTORY,
                        m("gui.item.staff.player_history.empty.title"),
                        List.of(m("gui.item.staff.player_history.empty.lore"))));
            } else {
                int firstSlot = 10 + Math.max(0, (PAGE_SIZE - page.entries().size()) / 2);
                for (int index = 0; index < page.entries().size(); index++) {
                    Entry entry = page.entries().get(index);
                    ArrayList<MessageReference> lore = new ArrayList<>();
                    if (entry.kind() != StaffHistorySource.Kind.NORMAL_PRESTIGE) {
                        lore.add(m(StaffHistoryPresentation.guiKindKey(entry.kind()),
                                "type", StaffHistoryPresentation.kindText(entry.kind())));
                        entry.actorName().ifPresent(actor -> lore.add(
                                m("gui.item.staff.history.actor", "player", actor)));
                    }
                    lore.add(m(StaffHistoryPresentation.guiOutcomeKey(entry.outcome()),
                            "status", StaffHistoryPresentation.outcomeText(entry.outcome())));
                    if (entry.kind() == StaffHistorySource.Kind.NORMAL_PRESTIGE) {
                        StaffHistoryPresentation.FinancialOutcome financial =
                                StaffHistoryPresentation.financialOutcome(entry);
                        financial.balance().ifPresent(balance -> lore.add(m("gui.item.staff.history.balance",
                                "before", balance.before(), "after", balance.after())));
                        financial.fallbackCost().ifPresent(value ->
                                lore.add(m("gui.item.staff.history.cost", "value", value)));
                        financial.reward().ifPresent(value ->
                                lore.add(m("gui.item.staff.history.reward", "value", value)));
                        if (financial.balance().isEmpty() && financial.fallbackCost().isEmpty()
                                && financial.reward().isEmpty()) {
                            lore.add(m(StaffHistoryPresentation.transactionKey(entry)));
                        }
                    }
                    lore.add(m("gui.item.staff.history.time", "value",
                            StaffHistoryPresentation.timestamp(entry.occurredAt())));
                    items.add(GuiDisplayItem.display(firstSlot + index, GuiItemIcon.HISTORY,
                            m(entry.kind() == StaffHistorySource.Kind.NORMAL_PRESTIGE
                                            ? "gui.item.staff.player_history.entry.title"
                                            : "gui.item.staff.player_history.admin_entry.title",
                                    "before", entry.before(), "after", entry.after(),
                                    "type", StaffHistoryPresentation.kindText(entry.kind())), lore));
                }
            }
            if (page.hasPrevious()) {
                addPage(actions, items, GuiActionKind.STAFF_PLAYER_HISTORY_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex - 1);
            }
            if (page.hasNext()) {
                addPage(actions, items, GuiActionKind.STAFF_PLAYER_HISTORY_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW,
                    revision, identity.playerId());
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.player_history", "player", identity.name()), actions,
                    GuiScreenKind.STAFF_PLAYER_HISTORY, SIZE, items));
        });
    }

    private CompletionStage<PlayerGuiInteractionResult> requirements(
            PermissionSubject subject,
            UUID playerId,
            int pageIndex) {
        StaffPlayerIdentity identity = known(playerId);
        return progress.inspect(subject, playerId).thenApply(view -> {
            OperationPreview preview = view.prestige();
            List<MessageReference> entries = preview.requirements()
                    .map(SemanticPresentation::requirementLeaves)
                    .orElseGet(List::of).stream()
                    .map(PlayerGuiService::requirementLine)
                    .toList();
            int pageCount = Math.max(1,
                    (entries.size() + REQUIREMENT_CARD_SLOTS.size() - 1) / REQUIREMENT_CARD_SLOTS.size());
            if (pageIndex < 0 || pageIndex >= pageCount) {
                throw new IllegalStateException("Server-owned Requirements page is outside the current projection");
            }
            ArrayList<GuiAction> actions = new ArrayList<>();
            ArrayList<GuiDisplayItem> items = new ArrayList<>();
            Optional<ConfigRevisionId> revision = Optional.of(preview.configRevision());
            long satisfied = entries.stream().filter(StaffGuiService::requirementMet).count();
            String summaryKey = satisfied == entries.size()
                    ? "gui.item.requirements.progress.complete"
                    : "gui.item.requirements.progress.incomplete";
            ArrayList<MessageReference> summary = new ArrayList<>();
            summary.add(m(summaryKey, "progress", satisfied, "total", entries.size()));
            if (pageCount > 1) {
                summary.add(m("gui.item.staff.requirements.page", "current", pageIndex + 1,
                        "total", pageCount));
            }
            items.add(GuiDisplayItem.display(4, GuiItemIcon.REQUIREMENTS,
                    m("gui.item.staff.requirements.summary.title"), summary));
            int first = pageIndex * REQUIREMENT_CARD_SLOTS.size();
            int last = Math.min(first + REQUIREMENT_CARD_SLOTS.size(), entries.size());
            List<Integer> cardSlots = entries.size() == 2
                    ? BALANCED_TWO_REQUIREMENT_SLOTS
                    : REQUIREMENT_CARD_SLOTS;
            int slotOffset = (cardSlots.size() - (last - first)) / 2;
            for (int index = first; index < last; index++) {
                MessageReference entry = entries.get(index);
                String label = entry.argument("label").orElse("Requirement");
                items.add(GuiDisplayItem.display(cardSlots.get(slotOffset + index - first),
                        requirementIcon(label), requirementTitle(label), List.of(
                                m("gui.item.staff.requirement.value",
                                        "current", entry.argument("current").orElse("Unavailable"),
                                        "target", entry.argument("target").orElse("Unavailable")),
                                m(requirementMet(entry)
                                        ? "gui.item.staff.requirement.met"
                                        : "gui.item.staff.requirement.not_met"))));
            }
            if (pageIndex > 0) {
                addPage(actions, items, GuiActionKind.STAFF_REQUIREMENTS_PREVIOUS,
                        "gui.action.staff.previous_page", 19, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex - 1);
            }
            if (pageIndex + 1 < pageCount) {
                addPage(actions, items, GuiActionKind.STAFF_REQUIREMENTS_NEXT,
                        "gui.action.staff.next_page", 25, PhaseSixPermissions.PLAYER_VIEW,
                        revision, identity.playerId(), pageIndex + 1);
            }
            addBack(actions, items, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW,
                    revision, identity.playerId());
            addClose(actions, items, revision);
            addBorder(items);
            return PlayerGuiInteractionResult.navigate(sessions.storeStaffScreen(subject,
                    m("gui.title.staff.requirements", "player", identity.name()), actions,
                    GuiScreenKind.STAFF_REQUIREMENTS, SIZE, items));
        });
    }

    private static GuiItemIcon requirementIcon(String label) {
        if (label.equalsIgnoreCase("Money")) {
            return GuiItemIcon.BALANCE;
        }
        if (label.equalsIgnoreCase("Total Skill Level")) {
            return GuiItemIcon.PROGRESS;
        }
        return GuiItemIcon.REQUIREMENTS;
    }

    private static MessageReference requirementTitle(String label) {
        if (label.equalsIgnoreCase("Money")) {
            return m("gui.item.staff.requirement.money.title");
        }
        if (label.equalsIgnoreCase("Total Skill Level")) {
            return m("gui.item.staff.requirement.total_skill_level.title");
        }
        return m("gui.item.staff.requirement.title", "label", label);
    }

    private static boolean requirementMet(MessageReference entry) {
        return entry.key().endsWith(".met");
    }

    private StaffPlayerIdentity known(UUID playerId) {
        return players.player(playerId).orElseThrow(() -> new AdministrationException(
                "gui.staff.player_unknown", "The selected player is no longer known to this server.",
                "Return to Player Management and select a known player."));
    }

    private static boolean playerPrestigeStateUnavailable(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof AdministrationException administration
                && administration.authorizationBlockers().stream().anyMatch(blocker ->
                        blocker.kind() == AuthorizationBlockerKind.PLAYER_PRESTIGE_STATE_UNAVAILABLE);
    }

    private static RuntimeException propagate(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof RuntimeException runtime
                ? runtime : new CompletionException(cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static CompletionStage<PlayerGuiInteractionResult> completed(GuiSessionView view) {
        return CompletableFuture.completedFuture(PlayerGuiInteractionResult.navigate(view));
    }

    private static UUID target(GuiAction action) {
        return action.targetPlayer().orElseThrow(() -> new AdministrationException(
                "gui.staff.target_missing", "The selected Staff GUI action has no player target.",
                "Return to player selection and choose a player again."));
    }

    private static int page(GuiAction action) {
        return action.page().orElseThrow(() ->
                new IllegalStateException("Server-owned paging action has no page target"));
    }

    private static long configurationLevel(GuiAction action) {
        String value = action.mutationContext().flatMap(GuiMutationContext::configPath)
                .orElseThrow(() -> new AdministrationException("gui.staff.configuration_level_missing",
                        "The selected configuration action has no server-owned Prestige level.",
                        "Return to Prestige Levels and choose a level again."));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AdministrationException("gui.staff.configuration_level_invalid",
                    "The selected configuration action has an invalid Prestige level.",
                    "Return to Prestige Levels and choose a level again.");
        }
    }

    private static UUID configurationReview(GuiAction action) {
        return action.confirmationId().orElseThrow(() -> new AdministrationException(
                "gui.staff.configuration_review_missing",
                "The selected configuration action has no server-owned review authority.",
                "Prepare and review the configuration change again."));
    }

    private static ConfigRevisionId configurationRevision(GuiAction action) {
        return action.expectedConfigRevision().orElseThrow(() -> new AdministrationException(
                "gui.staff.configuration_revision_missing",
                "The numeric input has no server-owned configuration revision.",
                "Return to the selected Prestige level and reopen the editor."));
    }

    private static AdministrationException invalidStaffAction() {
        return new AdministrationException("gui.action.staff_invalid",
                "The selected control does not belong to the current Staff GUI.",
                "Reopen the Staff GUI to obtain current server-owned controls.");
    }

    private static boolean invalidNumericInput(GuiActionKind kind, String code) {
        return kind == GuiActionKind.STAFF_REVIEW_PRESTIGE_MONEY
                && (code.equals("config.gui.money.invalid") || code.equals("config.gui.money.non_terminating"))
                || kind == GuiActionKind.STAFF_REVIEW_PRESTIGE_REWARD
                && code.equals("config.gui.reward.invalid")
                || kind == GuiActionKind.STAFF_REVIEW_TOTAL_SKILL_LEVEL
                && code.equals("config.gui.total_skill_level.invalid")
                || (kind == GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_BASE
                        || kind == GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_INCREMENT)
                && code.equals("config.gui.scaling.invalid")
                || kind == GuiActionKind.STAFF_REVIEW_SCALING_OVERRIDE
                && code.equals("config.gui.scaling.override.invalid");
    }

    private static GuidedScalingParameter scalingParameter(GuiActionKind kind) {
        return switch (kind) {
            case STAFF_REVIEW_SCALING_LINEAR_BASE -> GuidedScalingParameter.LINEAR_BASE;
            case STAFF_REVIEW_SCALING_LINEAR_INCREMENT -> GuidedScalingParameter.LINEAR_INCREMENT;
            default -> throw invalidStaffAction();
        };
    }

    private static String scalingInvalidMessage(GuiActionKind kind) {
        return switch (kind) {
            case STAFF_REVIEW_SCALING_LINEAR_BASE -> "gui.staff.scaling_base_input.invalid";
            case STAFF_REVIEW_SCALING_LINEAR_INCREMENT -> "gui.staff.scaling_input.invalid";
            case STAFF_REVIEW_SCALING_OVERRIDE -> "gui.staff.scaling_override_input.invalid";
            case STAFF_REVIEW_TOTAL_SKILL_LEVEL -> "gui.staff.total_skill_level_input.invalid";
            default -> "gui.staff.numeric_input.invalid";
        };
    }

    private static String humanize(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static ManualPrestigeAdjustmentReview prestigeReview(GuiAction action) {
        return action.prestigeAdjustment().orElseThrow(() -> new AdministrationException(
                "gui.staff.prestige_review_missing",
                "The selected Staff GUI action has no server-owned Prestige review.",
                "Return to Manage Player and prepare the adjustment again."));
    }

    private ManualPrestigeAdministrationService prestigeAdministration() {
        return prestigeAdministration.orElseThrow(() -> new AdministrationException(
                "gui.staff.prestige_administration_unavailable",
                "Player Prestige administration is unavailable in this runtime.",
                "Use the read-only player inspection surfaces and check server health."));
    }

    private GuidedConfigurationAdministration configurationAdministration() {
        return configurationAdministration.orElseThrow(() -> new AdministrationException(
                "gui.staff.configuration_administration_unavailable",
                "Guided configuration editing is unavailable in this runtime.",
                "Use the read-only Configuration view and check server health."));
    }

    private static boolean canManagePrestige(PermissionSubject subject) {
        return subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_SET)
                || subject.has(PhaseSixPermissions.PLAYER_PRESTIGE_RESET);
    }

    private static void addPage(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            String label,
            int slot,
            String permission,
            Optional<ConfigRevisionId> revision,
            UUID playerId,
            int pageIndex) {
        GuiAction action = new GuiAction(UUID.randomUUID(), kind, m(label),
                permission, false, revision, Optional.ofNullable(playerId),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pageIndex));
        actions.add(action);
        items.add(GuiDisplayItem.action(slot, GuiItemIcon.BACK, action.label(), List.of(),
                action.actionId(), false));
    }

    private static void addConfigurationPage(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            String label,
            int slot,
            String permission,
            Optional<ConfigRevisionId> revision,
            Long prestigeLevel,
            int pageIndex) {
        GuiAction action = configurationAction(kind, label, permission, false, revision,
                prestigeLevel, null, null, pageIndex);
        actions.add(action);
        items.add(GuiDisplayItem.action(slot, GuiItemIcon.BACK, action.label(), List.of(),
                action.actionId(), false));
    }

    private static void addConfigurationBack(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            Optional<ConfigRevisionId> revision,
            long prestigeLevel,
            int pageIndex) {
        GuiAction back = configurationAction(kind, "gui.action.back", PhaseSixPermissions.CONFIG_VIEW,
                false, revision, prestigeLevel, null, null, pageIndex);
        actions.add(back);
        items.add(GuiDisplayItem.action(18, GuiItemIcon.BACK, back.label(), List.of(), back.actionId(), false));
    }

    private static void addRefresh(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            Optional<ConfigRevisionId> revision,
            UUID playerId,
            int pageIndex) {
        GuiAction refresh = new GuiAction(UUID.randomUUID(), kind, m("gui.action.staff.refresh"),
                PhaseSixPermissions.ADMIN_GUI, false, revision, Optional.ofNullable(playerId),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pageIndex));
        actions.add(refresh);
        items.add(GuiDisplayItem.action(23, GuiItemIcon.PROGRESS, refresh.label(), List.of(),
                refresh.actionId(), false));
    }

    private static void addBack(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            GuiActionKind kind,
            Optional<ConfigRevisionId> revision,
            UUID playerId) {
        GuiAction back = action(kind, "gui.action.back", PhaseSixPermissions.ADMIN_GUI, revision, playerId);
        actions.add(back);
        items.add(GuiDisplayItem.action(18, GuiItemIcon.BACK, back.label(), List.of(), back.actionId(), false));
    }

    private static void addClose(
            List<GuiAction> actions,
            List<GuiDisplayItem> items,
            Optional<ConfigRevisionId> revision) {
        GuiAction close = action(GuiActionKind.STAFF_CLOSE, "gui.action.close",
                PhaseSixPermissions.ADMIN_GUI, revision, null);
        actions.add(close);
        items.add(GuiDisplayItem.action(26, GuiItemIcon.CLOSE, close.label(), List.of(), close.actionId(), false));
    }

    private static void addBorder(List<GuiDisplayItem> items) {
        java.util.Set<Integer> occupied = items.stream().map(GuiDisplayItem::slot)
                .collect(java.util.stream.Collectors.toSet());
        for (int index = 0; index < BORDER_SLOTS.size(); index++) {
            int slot = BORDER_SLOTS.get(index);
            if (!occupied.contains(slot)) {
                GuiItemIcon icon = index % 2 == 0 ? GuiItemIcon.BORDER_PURPLE : GuiItemIcon.BORDER_AQUA;
                items.add(GuiDisplayItem.display(slot, icon, m("gui.item.border"), List.of()));
            }
        }
    }

    private static GuiAction action(
            GuiActionKind kind,
            String label,
            String permission,
            Optional<ConfigRevisionId> revision,
            UUID playerId) {
        return new GuiAction(UUID.randomUUID(), kind, m(label), permission, false, revision,
                Optional.ofNullable(playerId), Optional.empty(), Optional.empty());
    }

    private static GuiAction configurationAction(
            GuiActionKind kind,
            String label,
            String permission,
            boolean mutating,
            Optional<ConfigRevisionId> revision,
            Long prestigeLevel,
            String amount,
            UUID reviewId,
            Object... labelArguments) {
        return configurationAction(kind, label, permission, mutating, revision, prestigeLevel,
                amount, reviewId, null, labelArguments);
    }

    private static GuiAction configurationAction(
            GuiActionKind kind,
            String label,
            String permission,
            boolean mutating,
            Optional<ConfigRevisionId> revision,
            Long prestigeLevel,
            String amount,
            UUID reviewId,
            Integer pageIndex,
            Object... labelArguments) {
        GuiMutationContext context = new GuiMutationContext(Optional.empty(),
                Optional.ofNullable(prestigeLevel).map(String::valueOf), Optional.ofNullable(amount),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        return new GuiAction(UUID.randomUUID(), kind, m(label, labelArguments), permission, mutating, revision,
                Optional.empty(), Optional.ofNullable(reviewId), Optional.empty(), Optional.empty(),
                Optional.of(context), Optional.ofNullable(pageIndex), Optional.empty());
    }

    private static GuiAction prestigeAction(
            GuiActionKind kind,
            String label,
            String permission,
            boolean mutating,
            Optional<ConfigRevisionId> revision,
            UUID playerId,
            ManualPrestigeAdjustmentReview review,
            int pageIndex,
            Object... labelArguments) {
        return new GuiAction(UUID.randomUUID(), kind, m(label, labelArguments), permission, mutating, revision,
                Optional.of(playerId), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(pageIndex), Optional.of(review));
    }

    private static void requirePlayerActor(PermissionSubject subject) {
        if (subject.actor().uuid().isEmpty()) {
            throw new AdministrationException("gui.staff.player_required",
                    "The Staff GUI requires an in-game player actor.",
                    "Run /maddprestige admin from an authorized player account.");
        }
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private Summary safeSummary() {
        try {
            return systemStatus.summary();
        } catch (RuntimeException | LinkageError failure) {
            return new Summary(Health.BLOCKED, false, 0, 0);
        }
    }

    private ConfigurationSummary safeConfigurationSummary() {
        try {
            return systemStatus.configuration();
        } catch (RuntimeException | LinkageError failure) {
            return ConfigurationSummary.inactive();
        }
    }

    private static String providerSummary(List<String> providers) {
        if (providers.isEmpty()) {
            return "None configured";
        }
        int visible = Math.min(5, providers.size());
        String joined = providers.subList(0, visible).stream()
                .map(StaffGuiService::boundedProviderLabel)
                .collect(java.util.stream.Collectors.joining(", "));
        return visible == providers.size() ? joined : joined + " +" + (providers.size() - visible) + " more";
    }

    private static String boundedProviderLabel(String provider) {
        String value = Objects.requireNonNull(provider, "provider label").strip();
        if (value.isEmpty()) {
            return "Unnamed provider";
        }
        return value.length() <= 48 ? value : value.substring(0, 47) + "…";
    }

    private static String statusActionKey(Health health) {
        return "gui.action.staff.system_status." + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusSummaryKey(Health health) {
        return "gui.item.staff.system_status.summary.title."
                + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusComponentKey(Health health) {
        return "gui.item.staff.system_status.component.title."
                + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusLineKey(Health health) {
        return "gui.item.staff.system_status.component.status."
                + health.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String statusText(Health health) {
        return switch (health) {
            case HEALTHY -> "Available";
            case WARNING -> "Attention";
            case BLOCKED -> "Unavailable";
        };
    }

}
