package net.maddkraft.maddprestige.core.admin.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.OperationKind;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.config.GuidedConfigurationAdministration;
import net.maddkraft.maddprestige.core.admin.config.GuidedMoneyConfigurationResult;
import net.maddkraft.maddprestige.core.admin.config.GuidedMoneyConfigurationReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedNumericConfigurationInput;
import net.maddkraft.maddprestige.core.admin.config.GuidedRequirementConfigurationEntry;
import net.maddkraft.maddprestige.core.admin.config.GuidedRequirementConfigurationView;
import net.maddkraft.maddprestige.core.admin.config.GuidedRewardConfigurationResult;
import net.maddkraft.maddprestige.core.admin.config.GuidedRewardConfigurationReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingConfigurationResult;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingConfigurationReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingConfigurationView;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingParameter;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingOverrideResult;
import net.maddkraft.maddprestige.core.admin.config.GuidedScalingOverrideReview;
import net.maddkraft.maddprestige.core.admin.config.GuidedTotalSkillLevelResult;
import net.maddkraft.maddprestige.core.admin.config.GuidedTotalSkillLevelReview;
import net.maddkraft.maddprestige.core.admin.config.MoneyAmountPage;
import net.maddkraft.maddprestige.core.admin.config.PrestigeLevelConfigurationView;
import net.maddkraft.maddprestige.core.admin.config.PrestigeLevelPage;
import net.maddkraft.maddprestige.core.admin.config.RewardAmountPage;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressView;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffGuiServiceTest {
    private static final UUID STAFF = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_STAFF = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
    private static final UUID OFFLINE_PLAYER = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase9f-b-read-only");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    private final AtomicReference<Optional<ConfigRevisionId>> revision =
            new AtomicReference<>(Optional.of(REVISION));
    private PlayerProgressViewService progress;
    private StaffGuiService service;

    @BeforeEach
    void setUp() {
        progress = mock(PlayerProgressViewService.class);
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        service = new StaffGuiService(sessions, progress,
                () -> List.of(new StaffPlayerIdentity(PLAYER, "tmydwc")), revision::get,
                historySource(List.of(), List.of()),
                healthyStatus());
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff Dashboard is permission-filtered and entirely read-only")
    void opensReadOnlyPermissionFilteredDashboard() {
        GuiSessionView allowed = service.open(staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW));

        assertEquals(GuiAudience.STAFF, allowed.audience());
        assertEquals(GuiScreenKind.STAFF_DASHBOARD, allowed.screen());
        assertEquals(27, allowed.inventorySize());
        assertEquals("gui.item.staff.overview.title", itemAt(allowed, 4).title().key());
        assertEquals(GuiItemIcon.OVERVIEW, itemAt(allowed, 4).icon());
        assertEquals("gui.action.staff.players", itemAt(allowed, 10).title().key());
        assertEquals("gui.action.staff.configuration", itemAt(allowed, 12).title().key());
        assertEquals("gui.action.staff.history", itemAt(allowed, 14).title().key());
        assertEquals("gui.action.staff.system_status.healthy", itemAt(allowed, 16).title().key());
        assertEquals(List.of("gui.item.staff.system_status.lore"),
                itemAt(allowed, 16).lore().stream().map(MessageReference::key).toList());
        assertTrue(itemAt(allowed, 16).lore().getFirst().arguments().isEmpty());
        assertEquals("gui.action.close", itemAt(allowed, 26).title().key());
        assertTrue(allowed.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_OPEN_PLAYERS));
        assertTrue(allowed.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_CONFIGURATION));
        assertTrue(allowed.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_HISTORY));
        assertTrue(allowed.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_SYSTEM_STATUS));
        assertTrue(allowed.items().stream().noneMatch(item ->
                item.title().key().contains("read_only")
                        || item.lore().stream().anyMatch(line -> line.key().contains("read_only"))));
        assertTrue(allowed.items().stream().noneMatch(item -> item.lore().stream()
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.revision"))));
        assertTrue(allowed.actions().stream().allMatch(action -> !action.mutating()));
        assertTrue(allowed.actions().stream().noneMatch(StaffGuiServiceTest::mutationAction));

        GuiSessionView limited = service.open(staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI));
        assertTrue(limited.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_OPEN_PLAYERS));
        assertEquals("gui.item.staff.players.unavailable", itemAt(limited, 10).lore().getFirst().key());
        assertTrue(limited.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_CONFIGURATION));
        assertThrows(AdministrationException.class, () -> service.open(
                staff(UUID.randomUUID(), PhaseSixPermissions.PLAYER_VIEW)));
    }

    @Test
    @DisplayName("[Phase 9F-C2] Configuration uses level-first disclosure and stops at one sealed Money review")
    void opensGuidedMoneyReviewWithoutApplyingConfiguration() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of(new StaffPlayerIdentity(PLAYER, "TargetPlayer"))), revision::get,
                historySource(List.of(), List.of()), healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        assertEquals(GuiScreenKind.STAFF_CONFIGURATION, configuration.screen());
        GuiDisplayItem prestigeLevels = itemAt(configuration, 13);
        assertEquals("gui.action.staff.prestige_levels", prestigeLevels.title().key());
        assertEquals(List.of(
                "gui.item.staff.prestige_levels.lore",
                "gui.item.separator",
                "gui.item.staff.configuration.revision"),
                prestigeLevels.lore().stream().map(MessageReference::key).toList());
        assertEquals(REVISION.value(), prestigeLevels.lore().get(2)
                .argument("revision").orElseThrow());
        assertEquals(List.of(
                "gui.item.staff.configuration.usable",
                "gui.item.staff.configuration.range",
                "gui.item.staff.configuration.requirements",
                "gui.item.staff.configuration.rewards",
                "gui.item.staff.configuration.scaling",
                "gui.item.staff.configuration.providers"),
                itemAt(configuration, 4).lore().stream().map(MessageReference::key).toList());
        assertTrue(configuration.items().stream().noneMatch(item ->
                item.title().key().equals("gui.item.staff.configuration.details.title")));
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_LEVELS, levels.screen());
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7"), levels.items().stream()
                .filter(item -> item.title().key().equals("gui.action.staff.prestige_level"))
                .map(item -> item.title().argument("level").orElseThrow()).toList());

        GuiSessionView level = click(route, editor, levels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_LEVEL_CONFIGURATION, level.screen());
        assertEquals("gui.action.staff.level.money", itemAt(level, 13).title().key());
        assertEquals("gui.item.staff.level.scaling", itemAt(level, 22).title().key());
        GuiSessionView input = click(route, editor, level, GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_MONEY_EDITOR, input.screen());
        assertEquals("6", input.textInput().orElseThrow().initialValue());
        assertEquals("6", itemAt(input, 0).title().argument("value").orElseThrow());
        assertTrue(input.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_MONEY_PREVIOUS
                        || action.kind() == GuiActionKind.STAFF_MONEY_NEXT));
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_MONEY);
        PlayerGuiInteractionResult invalid = route.submitNumericInput(
                editor, input.sessionId(), submit.actionId(), "not-a-number").toCompletableFuture().join();
        assertEquals("gui.staff.numeric_input.invalid", invalid.messages().getFirst().key());
        assertTrue(invalid.nextView().isEmpty());
        assertFalse(invalid.close());
        assertEquals(0, guided.reviewCalls);

        GuiSessionView review = route.submitNumericInput(editor, input.sessionId(), submit.actionId(), "25")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_MONEY_REVIEW, review.screen());
        assertEquals("gui.item.staff.money.review.title", itemAt(review, 13).title().key());
        assertEquals(List.of("gui.item.staff.money.review.level", "gui.item.staff.money.review.change"),
                itemAt(review, 13).lore().stream().map(MessageReference::key).toList());
        assertTrue(itemAt(review, 13).lore().stream().noneMatch(line ->
                line.arguments().containsKey("revision")));
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_PRESTIGE_MONEY);
        assertTrue(confirm.mutating());
        assertEquals(Optional.of(REVISION), confirm.expectedConfigRevision());
        assertEquals("gui.action.staff.money.confirm", itemAt(review, 22).title().key());
        assertEquals(1, guided.reviewCalls);
        assertEquals("25", itemAt(review, 13).lore().get(1).argument("after").orElseThrow());
        assertEquals(0, guided.confirmCalls);

        revision.set(Optional.of(new ConfigRevisionId("newer-revision")));
        AdministrationException stale = assertThrows(AdministrationException.class, () ->
                route.click(editor, review.sessionId(), confirm.actionId()));
        assertEquals("gui.action.stale", stale.code());
        assertEquals(0, guided.confirmCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Reward editor reaches one concise sealed review without applying")
    void opensGuidedRewardReviewWithoutApplyingConfiguration() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of(new StaffPlayerIdentity(PLAYER, "TargetPlayer"))), revision::get,
                historySource(List.of(), List.of()), healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiAction selectSix = levels.actions().stream()
                .filter(action -> action.kind() == GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL)
                .filter(action -> action.mutationContext().flatMap(GuiMutationContext::configPath)
                        .filter("6"::equals).isPresent())
                .findFirst().orElseThrow();
        GuiSessionView level = route.click(editor, levels.sessionId(), selectSix.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals("gui.action.staff.level.rewards", itemAt(level, 16).title().key());

        GuiSessionView input = click(route, editor, level, GuiActionKind.STAFF_EDIT_PRESTIGE_REWARD);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_REWARD_EDITOR, input.screen());
        assertEquals("2", input.textInput().orElseThrow().initialValue());
        assertTrue(input.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_REWARD_PREVIOUS
                        || action.kind() == GuiActionKind.STAFF_REWARD_NEXT));
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_REWARD);
        GuiSessionView review = route.submitNumericInput(editor, input.sessionId(), submit.actionId(), "10")
                .toCompletableFuture().join().nextView().orElseThrow();

        assertEquals(GuiScreenKind.STAFF_PRESTIGE_REWARD_REVIEW, review.screen());
        assertEquals("gui.item.staff.reward.review.title", itemAt(review, 13).title().key());
        assertEquals(List.of("gui.item.staff.reward.review.level", "gui.item.staff.reward.review.change"),
                itemAt(review, 13).lore().stream().map(MessageReference::key).toList());
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_PRESTIGE_REWARD);
        assertTrue(confirm.mutating());
        assertEquals(Optional.of(REVISION), confirm.expectedConfigRevision());
        assertEquals("gui.action.staff.reward.confirm", itemAt(review, 22).title().key());
        assertEquals(1, guided.rewardReviewCalls);
        assertEquals("10", itemAt(review, 13).lore().get(1).argument("after").orElseThrow());
        assertEquals(0, guided.rewardConfirmCalls);

        revision.set(Optional.of(new ConfigRevisionId("newer-reward-revision")));
        AdministrationException stale = assertThrows(AdministrationException.class, () ->
                route.click(editor, review.sessionId(), confirm.actionId()));
        assertEquals("gui.action.stale", stale.code());
        assertEquals(0, guided.rewardConfirmCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Requirements opens first click and reaches a sealed Total Skill Level review")
    void opensGuidedTotalSkillLevelReviewWithoutApplyingConfiguration() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiAction selectSix = levels.actions().stream()
                .filter(action -> action.kind() == GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL)
                .filter(action -> action.mutationContext().flatMap(GuiMutationContext::configPath)
                        .filter("6"::equals).isPresent())
                .findFirst().orElseThrow();
        GuiSessionView level = route.click(editor, levels.sessionId(), selectSix.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();

        GuiSessionView requirements = click(route, editor, level,
                GuiActionKind.STAFF_VIEW_PRESTIGE_REQUIREMENTS);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_REQUIREMENTS, requirements.screen());
        assertEquals("gui.item.staff.config_requirements.money.title", itemAt(requirements, 11).title().key());
        assertEquals("gui.item.staff.config_requirements.total_skill_level.title",
                itemAt(requirements, 15).title().key());
        assertEquals(List.of("gui.item.staff.config_requirements.money.value"),
                itemAt(requirements, 11).lore().stream().map(MessageReference::key).toList());
        assertEquals(List.of("gui.item.staff.config_requirements.total_skill_level.current"),
                itemAt(requirements, 15).lore().stream().map(MessageReference::key).toList());
        assertEquals("1", itemAt(requirements, 15).lore().getFirst()
                .argument("current").orElseThrow());
        assertTrue(requirements.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY));
        assertTrue(requirements.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_EDIT_TOTAL_SKILL_LEVEL));

        GuiSessionView moneyInput = click(route, editor, requirements, GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_MONEY_EDITOR, moneyInput.screen());
        assertEquals("6", moneyInput.textInput().orElseThrow().initialValue());
        GuiSessionView returnedLevel = click(route, editor, moneyInput, GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL);
        GuiSessionView returnedRequirements = click(route, editor, returnedLevel,
                GuiActionKind.STAFF_VIEW_PRESTIGE_REQUIREMENTS);

        GuiSessionView input = click(route, editor, returnedRequirements,
                GuiActionKind.STAFF_EDIT_TOTAL_SKILL_LEVEL);
        assertEquals(GuiScreenKind.STAFF_TOTAL_SKILL_LEVEL_EDITOR, input.screen());
        assertEquals("1", input.textInput().orElseThrow().initialValue());
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_TOTAL_SKILL_LEVEL);
        PlayerGuiInteractionResult invalid = route.submitNumericInput(
                editor, input.sessionId(), submit.actionId(), "1.5").toCompletableFuture().join();
        assertEquals("gui.staff.total_skill_level_input.invalid", invalid.messages().getFirst().key());
        assertTrue(invalid.nextView().isEmpty());
        assertEquals(0, guided.totalSkillReviewCalls);

        GuiSessionView review = route.submitNumericInput(editor, input.sessionId(), submit.actionId(), "2")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_TOTAL_SKILL_LEVEL_REVIEW, review.screen());
        assertEquals("gui.item.staff.total_skill_level.review.title", itemAt(review, 13).title().key());
        assertEquals("1", itemAt(review, 13).lore().get(1).argument("before").orElseThrow());
        assertEquals("2", itemAt(review, 13).lore().get(1).argument("after").orElseThrow());
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_TOTAL_SKILL_LEVEL);
        assertTrue(confirm.mutating());
        assertEquals(Optional.of(REVISION), confirm.expectedConfigRevision());
        assertEquals("gui.action.staff.total_skill_level.confirm", itemAt(review, 22).title().key());
        assertTrue(itemAt(review, 22).lore().isEmpty());
        assertEquals(1, guided.totalSkillReviewCalls);
        assertEquals(0, guided.totalSkillConfirmCalls);

        GuiSessionView returned = click(route, editor, review, GuiActionKind.STAFF_BACK_PRESTIGE_REQUIREMENTS);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_REQUIREMENTS, returned.screen());
        assertEquals(0, guided.totalSkillConfirmCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Complex requirement trees remain visible without guided mutation controls")
    void keepsComplexRequirementTreesReadOnly() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub(true, true);
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiSessionView level = click(route, editor, levels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);
        GuiSessionView requirements = click(route, editor, level,
                GuiActionKind.STAFF_VIEW_PRESTIGE_REQUIREMENTS);

        assertEquals(GuiScreenKind.STAFF_PRESTIGE_REQUIREMENTS, requirements.screen());
        assertTrue(requirements.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_EDIT_TOTAL_SKILL_LEVEL
                        || action.kind() == GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY));
        assertEquals(List.of(
                "gui.item.staff.config_requirements.total_skill_level.current",
                "gui.item.staff.config_requirements.read_only",
                "gui.item.staff.config_requirements.read_only.edit"),
                itemAt(requirements, 15).lore().stream().map(MessageReference::key).toList());
    }

    @Test
    @DisplayName("[Phase 9F-C2] Scaling opens first click and reaches a sealed Linear increment review")
    void opensGuidedScalingReviewWithoutApplyingConfiguration() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiAction selectSix = levels.actions().stream()
                .filter(action -> action.kind() == GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL)
                .filter(action -> action.mutationContext().flatMap(GuiMutationContext::configPath)
                        .filter("6"::equals).isPresent())
                .findFirst().orElseThrow();
        GuiSessionView level = route.click(editor, levels.sessionId(), selectSix.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        GuiSessionView scaling = click(route, editor, level, GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING);

        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING, scaling.screen());
        assertEquals("Linear", itemAt(scaling, 4).lore().getFirst().argument("mode").orElseThrow());
        assertEquals("6", itemAt(scaling, 4).lore().get(1).argument("value").orElseThrow());
        assertEquals("1", itemAt(scaling, 10).lore().getFirst().argument("value").orElseThrow());
        assertEquals("0.5", itemAt(scaling, 13).lore().getFirst().argument("value").orElseThrow());
        assertEquals("3", itemAt(scaling, 16).lore().getFirst().argument("value").orElseThrow());
        assertEquals("gui.session.expired", assertThrows(AdministrationException.class,
                () -> route.click(editor, level.sessionId(),
                        action(level, GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING).actionId())).code());

        GuiSessionView input = click(route, editor, scaling,
                GuiActionKind.STAFF_EDIT_SCALING_LINEAR_INCREMENT);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_EDITOR, input.screen());
        assertEquals("0.5", input.textInput().orElseThrow().initialValue());
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_INCREMENT);
        assertFalse(route.acceptsNumericInput(editor, input.sessionId(), submit.actionId(), "-1"));
        assertTrue(route.acceptsNumericInput(editor, input.sessionId(), submit.actionId(), "1.250"));
        PlayerGuiInteractionResult invalid = route.submitNumericInput(
                editor, input.sessionId(), submit.actionId(), "-1").toCompletableFuture().join();
        assertEquals("gui.staff.scaling_input.invalid", invalid.messages().getFirst().key());
        assertEquals(0, guided.scalingReviewCalls);

        GuiAction back = action(input, GuiActionKind.STAFF_BACK_PRESTIGE_SCALING);
        GuiSessionView returned = route.click(editor, input.sessionId(), back.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING, returned.screen());
        GuiSessionView retryInput = click(route, editor, returned,
                GuiActionKind.STAFF_EDIT_SCALING_LINEAR_INCREMENT);
        GuiAction retrySubmit = action(retryInput, GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_INCREMENT);
        GuiSessionView review = route.submitNumericInput(
                editor, retryInput.sessionId(), retrySubmit.actionId(), "1.250")
                .toCompletableFuture().join().nextView().orElseThrow();

        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_REVIEW, review.screen());
        assertEquals("gui.item.staff.scaling.review.title", itemAt(review, 13).title().key());
        assertEquals("0.5", itemAt(review, 13).lore().get(1).argument("before").orElseThrow());
        assertEquals("1.25", itemAt(review, 13).lore().get(1).argument("after").orElseThrow());
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_SCALING_LINEAR_INCREMENT);
        assertTrue(confirm.mutating());
        assertEquals(Optional.of(REVISION), confirm.expectedConfigRevision());
        assertEquals(1, guided.scalingReviewCalls);
        assertEquals(0, guided.scalingConfirmCalls);

        revision.set(Optional.of(new ConfigRevisionId("newer-scaling-revision")));
        assertEquals("gui.action.stale", assertThrows(AdministrationException.class,
                () -> route.click(editor, review.sessionId(), confirm.actionId())).code());
        assertEquals(0, guided.scalingConfirmCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Linear Base opens first click with prefill and reaches review without mutation")
    void opensGuidedLinearBaseReviewOnFirstClick() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiAction selectSix = levels.actions().stream()
                .filter(action -> action.kind() == GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL)
                .filter(action -> action.mutationContext().flatMap(GuiMutationContext::configPath)
                        .filter("6"::equals).isPresent())
                .findFirst().orElseThrow();
        GuiSessionView level = route.click(editor, levels.sessionId(), selectSix.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        GuiSessionView scaling = click(route, editor, level, GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING);

        assertEquals(GuiActionKind.STAFF_EDIT_SCALING_LINEAR_BASE,
                scaling.actions().stream().filter(action -> action.actionId().equals(
                        itemAt(scaling, 10).actionId().orElseThrow())).findFirst().orElseThrow().kind());
        GuiSessionView input = click(route, editor, scaling, GuiActionKind.STAFF_EDIT_SCALING_LINEAR_BASE);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_EDITOR, input.screen());
        assertEquals("gui.title.staff.scaling_base_input", input.title().key());
        assertEquals("1", input.textInput().orElseThrow().initialValue());
        assertEquals(GuidedScalingParameter.LINEAR_BASE, guided.lastScalingParameter);
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_SCALING_LINEAR_BASE);
        assertFalse(route.acceptsNumericInput(editor, input.sessionId(), submit.actionId(), "-1"));
        assertTrue(route.acceptsNumericInput(editor, input.sessionId(), submit.actionId(), "2"));
        PlayerGuiInteractionResult invalid = route.submitNumericInput(
                editor, input.sessionId(), submit.actionId(), "-1").toCompletableFuture().join();
        assertEquals("gui.staff.scaling_base_input.invalid", invalid.messages().getFirst().key());
        assertEquals(0, guided.scalingReviewCalls);

        GuiSessionView review = route.submitNumericInput(editor, input.sessionId(), submit.actionId(), "2")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_REVIEW, review.screen());
        assertEquals(GuidedScalingParameter.LINEAR_BASE, guided.lastScalingParameter);
        assertEquals("gui.item.staff.scaling.base.review.title", itemAt(review, 13).title().key());
        assertEquals("1", itemAt(review, 13).lore().get(1).argument("before").orElseThrow());
        assertEquals("2", itemAt(review, 13).lore().get(1).argument("after").orElseThrow());
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_SCALING_LINEAR_BASE);
        assertTrue(confirm.mutating());
        assertEquals(Optional.of(REVISION), confirm.expectedConfigRevision());
        assertEquals(1, guided.scalingReviewCalls);
        assertEquals(0, guided.scalingConfirmCalls);

        revision.set(Optional.of(new ConfigRevisionId("newer-base-revision")));
        assertEquals("gui.action.stale", assertThrows(AdministrationException.class,
                () -> route.click(editor, review.sessionId(), confirm.actionId())).code());
        assertEquals(0, guided.scalingConfirmCalls);
    }
    @Test
    @DisplayName("[Phase 9F-C2] Prestige Override edit and structural removal reach sealed reviews on first click")
    void opensPrestigeOverrideEditAndRemovalReviewsWithoutApplyingConfiguration() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);
        PermissionSubject other = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.CONFIG_VIEW, PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiAction selectSix = levels.actions().stream()
                .filter(action -> action.kind() == GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL)
                .filter(action -> action.mutationContext().flatMap(GuiMutationContext::configPath)
                        .filter("6"::equals).isPresent())
                .findFirst().orElseThrow();
        GuiSessionView level = route.click(editor, levels.sessionId(), selectSix.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        GuiSessionView scaling = click(route, editor, level, GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING);
        GuiAction manageAction = action(scaling, GuiActionKind.STAFF_MANAGE_SCALING_OVERRIDE);
        assertEquals(16, itemAt(scaling, 16).slot());
        assertEquals("3", itemAt(scaling, 16).lore().getFirst().argument("value").orElseThrow());

        GuiSessionView management = route.click(editor, scaling.sessionId(), manageAction.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE, management.screen());
        assertEquals("gui.title.staff.scaling_override", management.title().key());
        assertEquals("3", itemAt(management, 4).lore().getFirst().argument("current").orElseThrow());
        assertEquals(GuiItemIcon.CONFIGURATION, itemAt(management, 11).icon());
        assertEquals(GuiItemIcon.BLOCKED, itemAt(management, 15).icon());
        assertEquals("gui.session.expired", assertThrows(AdministrationException.class,
                () -> route.click(editor, scaling.sessionId(), manageAction.actionId())).code());

        GuiAction editAction = action(management, GuiActionKind.STAFF_EDIT_SCALING_OVERRIDE);
        assertEquals("gui.session.actor_mismatch", assertThrows(AdministrationException.class,
                () -> route.click(other, management.sessionId(), editAction.actionId())).code());
        GuiSessionView input = route.click(editor, management.sessionId(), editAction.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE_EDITOR, input.screen());
        assertEquals("gui.title.staff.scaling_override_input", input.title().key());
        assertEquals("3", input.textInput().orElseThrow().initialValue());
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_SCALING_OVERRIDE);
        assertTrue(route.acceptsNumericInput(editor, input.sessionId(), submit.actionId(), "4"));
        assertFalse(route.acceptsNumericInput(editor, input.sessionId(), submit.actionId(), "-1"));
        PlayerGuiInteractionResult invalid = route.submitNumericInput(
                editor, input.sessionId(), submit.actionId(), "-1").toCompletableFuture().join();
        assertEquals("gui.staff.scaling_override_input.invalid", invalid.messages().getFirst().key());
        assertEquals(0, guided.scalingOverrideReviewCalls);
        GuiSessionView returned = route.click(editor, input.sessionId(),
                action(input, GuiActionKind.STAFF_BACK_SCALING_OVERRIDE).actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE, returned.screen());

        GuiSessionView retryInput = click(route, editor, returned, GuiActionKind.STAFF_EDIT_SCALING_OVERRIDE);
        GuiAction retrySubmit = action(retryInput, GuiActionKind.STAFF_REVIEW_SCALING_OVERRIDE);
        GuiSessionView editReview = route.submitNumericInput(
                editor, retryInput.sessionId(), retrySubmit.actionId(), "4")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE_REVIEW, editReview.screen());
        List<MessageReference> editLore = itemAt(editReview, 13).lore();
        assertEquals(3, editLore.size());
        assertTrue(editLore.stream().noneMatch(line ->
                line.key().equals("gui.item.staff.scaling.override.review.level")));
        assertEquals("3", editLore.get(0).argument("current").orElseThrow());
        assertEquals("4", editLore.get(1).argument("value").orElseThrow());
        assertEquals("6", editLore.get(2).argument("before").orElseThrow());
        assertEquals("8", editLore.get(2).argument("after").orElseThrow());
        GuiAction editConfirm = action(editReview, GuiActionKind.STAFF_CONFIRM_SCALING_OVERRIDE_EDIT);
        assertTrue(editConfirm.mutating());
        assertEquals(GuiItemIcon.CONFIRM, itemAt(editReview, 22).icon());
        assertTrue(itemAt(editReview, 22).lore().isEmpty());
        assertEquals(0, guided.scalingOverrideConfirmCalls);
        GuiSessionView afterEditBack = route.click(editor, editReview.sessionId(),
                action(editReview, GuiActionKind.STAFF_BACK_SCALING_OVERRIDE).actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE, afterEditBack.screen());

        GuiSessionView removeReview = click(route, editor, afterEditBack,
                GuiActionKind.STAFF_REMOVE_SCALING_OVERRIDE);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE_REVIEW, removeReview.screen());
        List<MessageReference> removeLore = itemAt(removeReview, 13).lore();
        assertEquals(3, removeLore.size());
        assertTrue(removeLore.stream().noneMatch(line ->
                line.key().equals("gui.item.staff.scaling.override.review.level")));
        assertEquals("gui.item.staff.scaling.override.review.inherited", removeLore.get(1).key());
        assertEquals("6", removeLore.get(2).argument("before").orElseThrow());
        assertEquals("7", removeLore.get(2).argument("after").orElseThrow());
        GuiAction removeConfirm = action(removeReview,
                GuiActionKind.STAFF_CONFIRM_SCALING_OVERRIDE_REMOVAL);
        assertTrue(removeConfirm.mutating());
        assertEquals(GuiItemIcon.BLOCKED, itemAt(removeReview, 22).icon());
        assertEquals("gui.action.staff.scaling.override.remove.confirm", itemAt(removeReview, 22).title().key());
        assertTrue(itemAt(removeReview, 22).lore().isEmpty());
        assertEquals(2, guided.scalingOverrideReviewCalls);
        assertEquals(0, guided.scalingOverrideConfirmCalls);

        revision.set(Optional.of(new ConfigRevisionId("newer-override-revision")));
        assertEquals("gui.action.stale", assertThrows(AdministrationException.class,
                () -> route.click(editor, removeReview.sessionId(), removeConfirm.actionId())).code());
        assertEquals(0, guided.scalingOverrideConfirmCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Inherited Override keeps its stable slot and opens Add Override on first click")
    void inheritedOverrideRemainsVisibleAndOpensCanonicalAddReview() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub(false, false);
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiSessionView level = click(route, editor, levels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);
        GuiSessionView scaling = click(route, editor, level, GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING);

        assertEquals(16, itemAt(scaling, 16).slot());
        assertEquals("gui.item.staff.scaling.override.inherited", itemAt(scaling, 16).lore().get(0).key());
        assertEquals("gui.item.staff.scaling.override.add", itemAt(scaling, 16).lore().get(1).key());
        GuiAction manage = action(scaling, GuiActionKind.STAFF_MANAGE_SCALING_OVERRIDE);
        GuiSessionView inherited = route.click(editor, scaling.sessionId(), manage.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals("gui.item.staff.scaling.override.current.inherited",
                itemAt(inherited, 4).lore().getFirst().key());
        assertTrue(inherited.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_EDIT_SCALING_OVERRIDE
                        || action.kind() == GuiActionKind.STAFF_REMOVE_SCALING_OVERRIDE));
        GuiAction add = action(inherited, GuiActionKind.STAFF_ADD_SCALING_OVERRIDE);
        assertEquals(13, inherited.items().stream()
                .filter(item -> item.actionId().filter(add.actionId()::equals).isPresent())
                .findFirst().orElseThrow().slot());

        GuiSessionView input = route.click(editor, inherited.sessionId(), add.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals("6", input.textInput().orElseThrow().initialValue());
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_SCALING_OVERRIDE);
        GuiSessionView review = route.submitNumericInput(
                editor, input.sessionId(), submit.actionId(), "4")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals("gui.item.staff.scaling.override.review.add.title", itemAt(review, 13).title().key());
        List<MessageReference> lore = itemAt(review, 13).lore();
        assertEquals(3, lore.size());
        assertEquals("gui.item.staff.scaling.override.review.current.inherited", lore.get(0).key());
        assertEquals("4", lore.get(1).argument("value").orElseThrow());
        assertEquals("6", lore.get(2).argument("before").orElseThrow());
        assertEquals("8", lore.get(2).argument("after").orElseThrow());
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_SCALING_OVERRIDE_EDIT);
        assertTrue(confirm.mutating());
        assertEquals(GuiItemIcon.CONFIRM, itemAt(review, 22).icon());
        assertTrue(itemAt(review, 22).lore().isEmpty());
        assertEquals(1, guided.scalingOverrideReviewCalls);
        assertEquals(0, guided.scalingOverrideConfirmCalls);
        GuiSessionView back = route.click(editor, review.sessionId(),
                action(review, GuiActionKind.STAFF_BACK_SCALING_OVERRIDE).actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SCALING_OVERRIDE, back.screen());
        assertEquals("gui.session.expired", assertThrows(AdministrationException.class,
                () -> route.click(editor, scaling.sessionId(), manage.actionId())).code());
    }

    @Test
    @DisplayName("[Phase 9F-C2] Complex Scaling does not expose unsupported Override mutation controls")
    void complexOverrideRemainsReadOnly() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub(true, true);
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiSessionView level = click(route, editor, levels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);
        GuiSessionView scaling = click(route, editor, level, GuiActionKind.STAFF_VIEW_PRESTIGE_SCALING);

        assertTrue(scaling.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_MANAGE_SCALING_OVERRIDE
                        || action.kind() == GuiActionKind.STAFF_ADD_SCALING_OVERRIDE
                        || action.kind() == GuiActionKind.STAFF_EDIT_SCALING_OVERRIDE
                        || action.kind() == GuiActionKind.STAFF_REMOVE_SCALING_OVERRIDE));
        assertEquals(0, guided.scalingOverrideReviewCalls);
        assertEquals(0, guided.scalingOverrideConfirmCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Numeric input is actor/revision-bound, cancelable, replaceable, and retryable")
    void numericInputPreservesSessionAndRevisionSafety() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()),
                healthyStatus(), null, guided);
        PermissionSubject editor = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW,
                PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);
        PermissionSubject other = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.CONFIG_VIEW, PhaseSixPermissions.CONFIG_EDIT, PhaseSixPermissions.CONFIG_APPLY);

        GuiSessionView configuration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, editor, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiSessionView level = click(route, editor, levels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);
        GuiSessionView input = click(route, editor, level, GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY);
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_MONEY);

        assertEquals("gui.session.actor_mismatch", assertThrows(AdministrationException.class,
                () -> route.submitNumericInput(other, input.sessionId(), submit.actionId(), "25")).code());
        for (String invalid : List.of("", "-1", "10001", "NaN", "1e2", "1.2.3")) {
            PlayerGuiInteractionResult result = route.submitNumericInput(
                    editor, input.sessionId(), submit.actionId(), invalid).toCompletableFuture().join();
            assertEquals("gui.staff.numeric_input.invalid", result.messages().getFirst().key());
        }
        assertEquals(0, guided.reviewCalls);

        GuiAction back = action(input, GuiActionKind.STAFF_BACK_PRESTIGE_LEVEL);
        GuiSessionView returned = route.click(editor, input.sessionId(), back.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_LEVEL_CONFIGURATION, returned.screen());
        assertEquals(0, guided.reviewCalls);

        GuiSessionView replacement = click(route, editor, returned, GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY);
        GuiAction replacementSubmit = action(replacement, GuiActionKind.STAFF_REVIEW_PRESTIGE_MONEY);
        route.open(editor);
        assertEquals("gui.session.expired", assertThrows(AdministrationException.class,
                () -> route.submitNumericInput(
                        editor, replacement.sessionId(), replacementSubmit.actionId(), "25")).code());

        GuiSessionView newConfiguration = click(route, editor, route.open(editor),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView newLevels = click(route, editor, newConfiguration,
                GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiSessionView newLevel = click(route, editor, newLevels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);
        GuiSessionView staleInput = click(route, editor, newLevel, GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY);
        GuiAction staleSubmit = action(staleInput, GuiActionKind.STAFF_REVIEW_PRESTIGE_MONEY);
        revision.set(Optional.of(new ConfigRevisionId("typed-input-newer-revision")));
        assertEquals("gui.action.stale", assertThrows(AdministrationException.class,
                () -> route.submitNumericInput(
                        editor, staleInput.sessionId(), staleSubmit.actionId(), "25")).code());
        assertEquals(0, guided.reviewCalls);
    }

    @Test
    @DisplayName("[Phase 9F-C2] Guided mutation controls require distinct edit and apply authority")
    void hidesGuidedMoneyMutationWithoutBothPermissions() {
        GuidedConfigurationStub guided = new GuidedConfigurationStub();
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        StaffGuiService route = new StaffGuiService(sessions, progress,
                directory(List.of()), revision::get, historySource(List.of(), List.of()), healthyStatus(), null,
                guided);
        PermissionSubject viewer = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW);

        GuiSessionView configuration = click(route, viewer, route.open(viewer),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView levels = click(route, viewer, configuration, GuiActionKind.STAFF_OPEN_PRESTIGE_LEVELS);
        GuiSessionView level = click(route, viewer, levels, GuiActionKind.STAFF_SELECT_PRESTIGE_LEVEL);

        assertTrue(level.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_EDIT_PRESTIGE_MONEY));
        assertEquals("gui.item.staff.level.money.permission", itemAt(level, 13).lore().getLast().key());
        assertTrue(level.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_EDIT_PRESTIGE_REWARD));
        assertEquals("gui.item.staff.level.reward.permission", itemAt(level, 16).lore().getLast().key());
    }

    @Test
    @DisplayName("[Phase 9F-B] History renders canonical activity, empty state, paging, Back, and Close")
    void rendersPagedAndEmptyHistoryWithoutInternalOperationDetails() {
        List<StaffHistorySource.Entry> entries = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> historyEntry(
                        index == 0 ? "tmydwc" : "Player " + index,
                        index, index + 1,
                        index == 1 ? StaffHistorySource.Outcome.FAILED
                                : index == 2 ? StaffHistorySource.Outcome.RECOVERED
                                        : index == 3 ? StaffHistorySource.Outcome.REJECTED
                                                : StaffHistorySource.Outcome.COMPLETED,
                        true, index % 2 == 0, CLOCK.instant().minusSeconds(index * 60L)))
                .toList();
        StaffHistorySource source = (offset, limit) -> {
            int last = Math.min(offset + limit, entries.size());
            List<StaffHistorySource.Entry> page = offset >= entries.size()
                    ? List.of() : entries.subList(offset, last);
            return CompletableFuture.completedFuture(new StaffHistorySource.Page(
                    page, offset > 0, last < entries.size()));
        };
        StaffGuiService route = service(source, healthyStatus());
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView dashboard = route.open(subject);
        GuiSessionView first = click(route, subject, dashboard, GuiActionKind.STAFF_VIEW_HISTORY);
        GuiSessionView second = click(route, subject, first, GuiActionKind.STAFF_HISTORY_NEXT);
        GuiSessionView returned = click(route, subject, second, GuiActionKind.STAFF_BACK_DASHBOARD);

        assertEquals(GuiScreenKind.STAFF_HISTORY, first.screen());
        assertEquals(7, historyCards(first).size());
        assertEquals(1, historyCards(second).size());
        assertTrue(first.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_HISTORY_NEXT && action.page().orElseThrow() == 1));
        assertTrue(second.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_HISTORY_PREVIOUS && action.page().orElseThrow() == 0));
        assertTrue(historyCards(first).stream().flatMap(item -> item.lore().stream())
                .noneMatch(line -> line.arguments().containsKey("operation")
                        || line.arguments().containsKey("id")));
        assertTrue(historyCards(first).stream().allMatch(item ->
                item.title().argument("player").isPresent()));
        assertTrue(historyCards(first).stream().flatMap(item -> item.lore().stream())
                .noneMatch(line -> line.key().startsWith("gui.item.staff.history.transaction.")));
        assertTrue(historyCards(first).stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.history.outcome.recovered")));
        assertTrue(historyCards(first).stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.history.outcome.rejected")));
        assertTrue(itemAt(first, 4).lore().stream().anyMatch(line ->
                line.key().equals("gui.item.staff.history.exceptions")
                        && line.argument("count").filter("3"::equals).isPresent()));
        assertEquals("gui.item.staff.history.lore", itemAt(dashboard, 14).lore().getFirst().key());
        assertEquals(GuiScreenKind.STAFF_DASHBOARD, returned.screen());
        assertTrue(List.of(first, second).stream().flatMap(view -> view.actions().stream())
                .allMatch(action -> !action.mutating()));

        GuiSessionView empty = click(subject, service.open(subject), GuiActionKind.STAFF_VIEW_HISTORY);
        assertTrue(empty.items().stream().anyMatch(item ->
                item.title().key().equals("gui.item.staff.history.empty.title")));
        PlayerGuiInteractionResult closed = service.click(subject, empty.sessionId(),
                action(empty, GuiActionKind.STAFF_CLOSE).actionId()).toCompletableFuture().join();
        assertTrue(closed.close());
    }

    @Test
    @DisplayName("[Phase 9F-B] Player History is selected-player filtered, paged, and actor-bound")
    void rendersSelectedPlayerHistoryWithFirstClickNavigationAndExplicitEmptyState() {
        OperationPreview preview = preview();
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, preview)));
        List<StaffHistorySource.Entry> selected = new ArrayList<>(java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> historyEntry("tmydwc", 5 + index, 6 + index,
                        index == 1 ? StaffHistorySource.Outcome.RECOVERED
                                : index == 2 ? StaffHistorySource.Outcome.FAILED
                                        : StaffHistorySource.Outcome.COMPLETED,
                        true, index % 2 == 0, CLOCK.instant().minusSeconds(index * 60L)))
                .toList());
        StaffHistorySource.Entry canonicalBalance = selected.getFirst();
        selected.set(0, new StaffHistorySource.Entry(canonicalBalance.entryId(), canonicalBalance.player(),
                canonicalBalance.before(), canonicalBalance.after(), canonicalBalance.outcome(),
                true, true, Optional.of("$7"), Optional.of("$1"),
                Optional.of("$5"), Optional.of("$1"), canonicalBalance.occurredAt()));
        AtomicReference<UUID> requestedPlayer = new AtomicReference<>();
        StaffHistorySource source = new StaffHistorySource() {
            @Override
            public java.util.concurrent.CompletionStage<Page> recent(int offset, int limit) {
                return CompletableFuture.completedFuture(new Page(List.of(
                        historyEntry("Other Player", 1, 2, Outcome.COMPLETED,
                                false, false, CLOCK.instant())), false, false));
            }

            @Override
            public java.util.concurrent.CompletionStage<Page> forPlayer(
                    UUID playerId,
                    int offset,
                    int limit) {
                requestedPlayer.set(playerId);
                int last = Math.min(offset + limit, selected.size());
                List<Entry> page = offset >= selected.size() ? List.of() : selected.subList(offset, last);
                return CompletableFuture.completedFuture(new Page(page, offset > 0, last < selected.size()));
            }
        };
        StaffGuiService route = service(source, healthyStatus());
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView selection = onlinePlayers(route, subject, route.open(subject));
        GuiSessionView overview = click(route, subject, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiAction historyAction = action(overview, GuiActionKind.STAFF_VIEW_PLAYER_HISTORY);
        PermissionSubject other = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);
        AdministrationException mismatch = assertThrows(AdministrationException.class,
                () -> route.click(other, overview.sessionId(), historyAction.actionId()));
        assertEquals("gui.session.actor_mismatch", mismatch.code());
        GuiSessionView first = click(route, subject, overview, GuiActionKind.STAFF_VIEW_PLAYER_HISTORY);
        GuiSessionView second = click(route, subject, first, GuiActionKind.STAFF_PLAYER_HISTORY_NEXT);
        GuiSessionView returned = click(route, subject, second, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW);

        assertEquals(24, itemAt(overview, 24).slot());
        assertEquals("gui.action.staff.player_history", itemAt(overview, 24).title().key());
        assertEquals(Optional.of(PLAYER), historyAction.targetPlayer());
        assertEquals(PLAYER, requestedPlayer.get());
        assertEquals(GuiScreenKind.STAFF_PLAYER_HISTORY, first.screen());
        assertEquals("tmydwc", first.title().argument("player").orElseThrow());
        assertEquals(7, playerHistoryCards(first).size());
        assertEquals(1, playerHistoryCards(second).size());
        assertTrue(playerHistoryCards(first).stream().allMatch(item ->
                item.title().arguments().containsKey("before")
                        && item.title().arguments().containsKey("after")
                        && !item.title().arguments().containsKey("player")));
        assertTrue(playerHistoryCards(first).stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.history.outcome.completed")));
        assertTrue(playerHistoryCards(first).stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.history.outcome.recovered")));
        assertTrue(playerHistoryCards(first).stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.history.outcome.failed")));
        List<MessageReference> balanceLore = playerHistoryCards(first).getFirst().lore();
        assertTrue(balanceLore.stream().anyMatch(line ->
                line.key().equals("gui.item.staff.history.balance")
                        && line.argument("before").filter("$7"::equals).isPresent()
                        && line.argument("after").filter("$1"::equals).isPresent()));
        assertTrue(balanceLore.stream().noneMatch(line ->
                line.key().equals("gui.item.staff.history.cost")));
        assertTrue(balanceLore.stream().anyMatch(line ->
                line.key().equals("gui.item.staff.history.reward")));
        List<MessageReference> fallbackLore = playerHistoryCards(first).get(1).lore();
        assertTrue(fallbackLore.stream().anyMatch(line ->
                line.key().equals("gui.item.staff.history.cost")));
        assertTrue(fallbackLore.stream().noneMatch(line ->
                line.key().equals("gui.item.staff.history.balance")));
        assertTrue(first.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_PLAYER_HISTORY_NEXT && action.page().orElseThrow() == 1));
        assertTrue(second.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_PLAYER_HISTORY_PREVIOUS && action.page().orElseThrow() == 0));
        assertEquals(GuiScreenKind.STAFF_PLAYER_OVERVIEW, returned.screen());
        assertTrue(List.of(first, second).stream().flatMap(view -> view.actions().stream())
                .allMatch(action -> !action.mutating()));

        StaffGuiService emptyRoute = service(historySource(List.of(), List.of()), healthyStatus());
        GuiSessionView emptySelection = onlinePlayers(emptyRoute, subject, emptyRoute.open(subject));
        GuiSessionView emptyOverview = click(emptyRoute, subject, emptySelection,
                GuiActionKind.STAFF_SELECT_PLAYER);
        GuiSessionView empty = click(emptyRoute, subject, emptyOverview,
                GuiActionKind.STAFF_VIEW_PLAYER_HISTORY);
        assertTrue(empty.items().stream().anyMatch(item ->
                item.title().key().equals("gui.item.staff.player_history.empty.title")
                        && item.lore().stream().anyMatch(line ->
                                line.key().equals("gui.item.staff.player_history.empty.lore"))));
        PlayerGuiInteractionResult closed = emptyRoute.click(subject, empty.sessionId(),
                action(empty, GuiActionKind.STAFF_CLOSE).actionId()).toCompletableFuture().join();
        assertTrue(closed.close());
    }

    @Test
    @DisplayName("[Phase 9F-B] System Status renders healthy/unavailable truth and stable paging")
    void rendersSystemHealthAndUnavailableStates() {
        List<StaffSystemStatusSource.Component> components = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new StaffSystemStatusSource.Component(
                        index == 0 ? "Vault" : "Component " + index,
                        index == 7 ? StaffSystemStatusSource.Health.WARNING
                                : StaffSystemStatusSource.Health.HEALTHY,
                        index == 7 ? "Degraded" : "Available",
                        index == 0 ? "Available from cached provider health" : ""))
                .toList();
        StaffSystemStatusSource.Summary summary = new StaffSystemStatusSource.Summary(
                StaffSystemStatusSource.Health.WARNING, true, 7, 8);
        StaffSystemStatusSource source = status(summary, components);
        StaffGuiService route = service((offset, limit) -> CompletableFuture.completedFuture(
                new StaffHistorySource.Page(List.of(), offset > 0, false)), source);
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView dashboard = route.open(subject);
        assertEquals("gui.action.staff.system_status.warning", itemAt(dashboard, 16).title().key());
        GuiSessionView first = click(route, subject, dashboard, GuiActionKind.STAFF_VIEW_SYSTEM_STATUS);
        GuiSessionView second = click(route, subject, first, GuiActionKind.STAFF_SYSTEM_STATUS_NEXT);
        GuiAction refresh = action(second, GuiActionKind.STAFF_REFRESH_SYSTEM_STATUS);
        PlayerGuiInteractionResult refreshedResult = route.click(subject, second.sessionId(), refresh.actionId())
                .toCompletableFuture().join();
        GuiSessionView refreshed = refreshedResult.nextView().orElseThrow();
        GuiSessionView back = click(route, subject, refreshed, GuiActionKind.STAFF_BACK_DASHBOARD);

        assertEquals(GuiScreenKind.STAFF_SYSTEM_STATUS, first.screen());
        assertEquals("gui.item.staff.system_status.summary.title.warning", itemAt(first, 4).title().key());
        assertEquals(7, statusCards(first).size());
        assertEquals(1, statusCards(second).size());
        assertTrue(first.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_SYSTEM_STATUS_NEXT));
        assertTrue(second.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_SYSTEM_STATUS_PREVIOUS));
        assertEquals(GuiScreenKind.STAFF_SYSTEM_STATUS, refreshed.screen());
        assertEquals(1, statusCards(refreshed).size());
        assertEquals(GuiItemIcon.REFRESH, itemAt(refreshed, 23).icon());
        assertFalse(second.sessionId().equals(refreshed.sessionId()));
        assertTrue(refreshedResult.messages().isEmpty());
        assertFalse(refresh.mutating());
        assertTrue(statusCards(second).getFirst().lore().stream().anyMatch(line ->
                line.argument("status").filter("Degraded"::equals).isPresent()));
        assertEquals(GuiScreenKind.STAFF_DASHBOARD, back.screen());

        StaffSystemStatusSource.Summary blocked = new StaffSystemStatusSource.Summary(
                StaffSystemStatusSource.Health.BLOCKED, false, 0, 1);
        StaffGuiService unavailable = service((offset, limit) -> CompletableFuture.completedFuture(
                new StaffHistorySource.Page(List.of(), offset > 0, false)), status(blocked, List.of(
                        new StaffSystemStatusSource.Component("Configuration",
                                StaffSystemStatusSource.Health.BLOCKED,
                                "Unsupported", "No active configuration"))));
        GuiSessionView unavailableDashboard = unavailable.open(subject);
        GuiSessionView unavailableDetail = click(unavailable, subject, unavailableDashboard,
                GuiActionKind.STAFF_VIEW_SYSTEM_STATUS);
        assertEquals("gui.action.staff.system_status.blocked", itemAt(unavailableDashboard, 16).title().key());
        assertTrue(statusCards(unavailableDetail).stream().anyMatch(item ->
                item.title().key().equals("gui.item.staff.system_status.component.title.blocked")));
        assertTrue(statusCards(unavailableDetail).stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.argument("status").filter("Unsupported"::equals).isPresent()));
    }

    @Test
    @DisplayName("[Phase 9F-B] Admin completion is visible only to authorized in-game staff")
    void completesAdminRouteOnlyForAuthorizedPlayer() {
        CommandCompletionService completion = new CommandCompletionService();

        assertTrue(completion.suggest(staff(STAFF, PhaseSixPermissions.ADMIN_GUI), List.of(""))
                .contains("admin"));
        assertFalse(completion.suggest(staff(STAFF, PhaseSixPermissions.PLAYER_VIEW), List.of(""))
                .contains("admin"));
        PermissionSubject console = new PermissionSubject(new Actor("console", Optional.empty(), "Console"),
                Set.of(PhaseSixPermissions.ADMIN_GUI));
        assertFalse(completion.suggest(console, List.of("")).contains("admin"));
    }

    @Test
    @DisplayName("[Phase 9F-B] Dashboard navigates through online selection, canonical overview, and requirements")
    void navigatesFirstReadOnlyOwnerReviewMilestone() {
        OperationPreview preview = preview();
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, preview)));
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView configuration = click(subject, service.open(subject),
                GuiActionKind.STAFF_VIEW_CONFIGURATION);
        GuiSessionView configurationBack = click(subject, configuration, GuiActionKind.STAFF_BACK_DASHBOARD);
        GuiSessionView dashboard = service.open(subject);
        GuiSessionView selection = onlinePlayers(subject, dashboard);
        GuiSessionView overview = click(subject, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiSessionView requirements = click(subject, overview, GuiActionKind.STAFF_VIEW_REQUIREMENTS);
        GuiSessionView returned = click(subject, requirements, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW);

        assertEquals(GuiScreenKind.STAFF_CONFIGURATION, configuration.screen());
        assertEquals(GuiScreenKind.STAFF_DASHBOARD, configurationBack.screen());
        assertTrue(configuration.items().stream().noneMatch(item ->
                item.title().key().equals("gui.item.staff.configuration.details.title")));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.usable")));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.range")
                        && line.argument("value").filter("P1 – P50"::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.requirements")
                        && line.argument("count").filter("2"::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .noneMatch(line -> line.key().equals("gui.item.staff.configuration.costs")));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.rewards")
                        && line.argument("count").filter("1"::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.scaling")
                        && line.argument("count").filter("3"::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.providers")
                        && line.argument("provider").filter("Vault, mcMMO, LuckPerms"::equals).isPresent()));
        assertEquals(GuiScreenKind.STAFF_PLAYER_SELECTION, selection.screen());
        assertEquals(GuiScreenKind.STAFF_PLAYER_OVERVIEW, overview.screen());
        assertEquals(GuiScreenKind.STAFF_REQUIREMENTS, requirements.screen());
        assertEquals(GuiScreenKind.STAFF_PLAYER_OVERVIEW, returned.screen());
        GuiDisplayItem selectedPlayer = selection.items().stream()
                .filter(item -> item.actionId().isPresent()
                        && item.title().argument("player").filter("tmydwc"::equals).isPresent())
                .findFirst().orElseThrow();
        assertEquals(GuiItemIcon.PLAYERS, selectedPlayer.icon());
        assertEquals(Optional.of(PLAYER), selectedPlayer.profilePlayerId());
        assertTrue(selectedPlayer.lore().stream()
                .anyMatch(line -> line.key().equals("gui.item.staff.player.selection.online")));
        assertTrue(selectedPlayer.lore().stream()
                .anyMatch(line -> line.key().equals("gui.item.staff.player.selection.inspect")));
        assertTrue(selectedPlayer.lore().stream().noneMatch(line -> line.arguments().containsKey("uuid")));
        assertEquals(GuiItemIcon.PROGRESS, itemAt(overview, 4).icon());
        assertTrue(overview.items().stream().anyMatch(item -> item.icon() == GuiItemIcon.BALANCE));
        assertTrue(overview.items().stream().anyMatch(item -> item.icon() == GuiItemIcon.REQUIREMENTS
                && item.actionId().isPresent()));
        assertEquals("gui.action.staff.refresh", itemAt(overview, 0).title().key());
        assertEquals(GuiItemIcon.REFRESH, itemAt(overview, 0).icon());
        assertEquals("gui.item.staff.player.info.title", itemAt(overview, 8).title().key());
        assertEquals(GuiItemIcon.PLAYER_INFORMATION, itemAt(overview, 8).icon());
        assertEquals("gui.action.back", itemAt(overview, 18).title().key());
        assertTrue(itemAt(overview, 19).actionId().isEmpty());
        assertEquals("gui.action.staff.prestige_preview", itemAt(overview, 20).title().key());
        assertTrue(itemAt(overview, 21).actionId().isEmpty());
        assertTrue(overview.items().stream().noneMatch(item -> item.slot() == 22));
        assertTrue(itemAt(overview, 23).actionId().isEmpty());
        assertEquals("gui.action.staff.player_history", itemAt(overview, 24).title().key());
        assertTrue(itemAt(overview, 25).actionId().isEmpty());
        assertEquals("gui.action.close", itemAt(overview, 26).title().key());
        GuiDisplayItem playerInfo = overview.items().stream()
                .filter(item -> item.title().key().equals("gui.item.staff.player.info.title"))
                .findFirst().orElseThrow();
        assertEquals(8, playerInfo.slot());
        assertTrue(playerInfo.actionId().isPresent());
        assertEquals(Optional.of(PLAYER), playerInfo.profilePlayerId());
        assertTrue(playerInfo.lore().stream().anyMatch(line -> line.arguments().containsKey("uuid")));
        assertTrue(playerInfo.lore().stream().anyMatch(line -> line.key().equals("gui.item.staff.player.online")));
        assertTrue(playerInfo.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.staff.player.info.copy")));
        assertEquals(1, overview.items().stream().filter(item ->
                item.title().key().equals("gui.item.staff.player.info.title")).count());
        assertTrue(overview.items().stream().noneMatch(item ->
                item.title().key().equals("gui.action.staff.copy_uuid")));
        assertTrue(requirements.items().stream().anyMatch(item ->
                item.title().key().equals("gui.item.staff.requirements.summary.title")
                        && item.lore().stream().anyMatch(line ->
                                line.argument("progress").filter("1"::equals).isPresent()
                                        && line.argument("total").filter("2"::equals).isPresent())));
        GuiDisplayItem money = requirements.items().stream()
                .filter(item -> item.title().key().equals("gui.item.staff.requirement.money.title"))
                .findFirst().orElseThrow();
        assertEquals(11, money.slot());
        assertEquals(GuiItemIcon.BALANCE, money.icon());
        assertTrue(money.lore().stream().anyMatch(line ->
                line.argument("current").filter("$1"::equals).isPresent()
                        && line.argument("target").filter("$8"::equals).isPresent()));
        assertTrue(money.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.staff.requirement.not_met")));
        GuiDisplayItem totalSkillLevel = requirements.items().stream()
                .filter(item -> item.title().key().equals("gui.item.staff.requirement.total_skill_level.title"))
                .findFirst().orElseThrow();
        assertEquals(15, totalSkillLevel.slot());
        assertEquals(GuiItemIcon.PROGRESS, totalSkillLevel.icon());
        assertTrue(totalSkillLevel.lore().stream().anyMatch(line ->
                line.argument("current").filter("1"::equals).isPresent()
                        && line.argument("target").filter("1"::equals).isPresent()));
        assertTrue(totalSkillLevel.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.staff.requirement.met")));
        assertTrue(requirements.items().stream().noneMatch(item -> item.lore().stream()
                .anyMatch(line -> line.argument("label").isPresent())));
        assertTrue(List.of(configuration, configurationBack, dashboard, selection, overview, requirements, returned)
                .stream()
                .flatMap(view -> view.actions().stream()).allMatch(action -> !action.mutating()));
        verify(progress, times(3)).inspect(any(), eq(PLAYER));
    }

    @Test
    @DisplayName("[Phase 9F-B] Known offline players resolve explicitly into read-only canonical views")
    void findsKnownOfflinePlayerWithoutFabricatingLiveProviderState() {
        StaffPlayerDirectory directory = directory(List.of(
                new StaffPlayerIdentity(PLAYER, "tmydwc", true),
                new StaffPlayerIdentity(OFFLINE_PLAYER, "KnownPlayer", false)));
        OperationPreview unavailable = withoutBalanceProjection(preview(OFFLINE_PLAYER, List.of(
                leaf("balance", "Unavailable", "8", ExplanationStatus.UNAVAILABLE),
                leaf("total_level", "Unavailable", "1", ExplanationStatus.UNAVAILABLE)), false));
        when(progress.inspect(any(), eq(OFFLINE_PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(OFFLINE_PLAYER, unavailable)));
        StaffHistorySource.Entry persisted = historyEntry("KnownPlayer", 2, 3,
                StaffHistorySource.Outcome.COMPLETED, true, true, CLOCK.instant());
        StaffGuiService route = service(directory, historySource(List.of(), List.of(persisted)), healthyStatus());
        PermissionSubject owner = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);
        PermissionSubject other = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView management = click(route, owner, route.open(owner), GuiActionKind.STAFF_OPEN_PLAYERS);
        assertEquals(GuiScreenKind.STAFF_PLAYER_MANAGEMENT, management.screen());
        assertTrue(management.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_OPEN_ONLINE_PLAYERS));
        GuiAction find = action(management, GuiActionKind.STAFF_FIND_PLAYER);
        PlayerGuiInteractionResult prompt = route.click(owner, management.sessionId(), find.actionId())
                .toCompletableFuture().join();
        assertTrue(prompt.close());
        assertEquals("gui.staff.find.prompt", prompt.messages().getFirst().key());

        GuiSessionView results = route.findPlayers(owner, "KnownPlayer");
        assertEquals(GuiScreenKind.STAFF_PLAYER_SEARCH_RESULTS, results.screen());
        GuiAction select = action(results, GuiActionKind.STAFF_SELECT_SEARCH_RESULT);
        assertEquals(Optional.of(OFFLINE_PLAYER), select.targetPlayer());
        GuiDisplayItem result = results.items().stream()
                .filter(item -> item.actionId().filter(select.actionId()::equals).isPresent())
                .findFirst().orElseThrow();
        assertEquals(Optional.of(OFFLINE_PLAYER), result.profilePlayerId());
        assertTrue(result.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.staff.player.selection.offline")));
        assertTrue(result.lore().stream().noneMatch(line -> line.arguments().containsKey("uuid")));
        AdministrationException crossActor = assertThrows(AdministrationException.class,
                () -> route.click(other, results.sessionId(), select.actionId()));
        assertEquals("gui.session.actor_mismatch", crossActor.code());
        AdministrationException forged = assertThrows(AdministrationException.class,
                () -> route.click(owner, results.sessionId(), UUID.randomUUID()));
        assertEquals("gui.action.forged", forged.code());

        GuiSessionView overview = click(route, owner, results, GuiActionKind.STAFF_SELECT_SEARCH_RESULT);
        GuiDisplayItem identity = itemAt(overview, 8);
        assertEquals("gui.item.staff.player.offline", identity.lore().getFirst().key());
        assertEquals(Optional.of(OFFLINE_PLAYER), identity.profilePlayerId());
        assertTrue(identity.lore().stream().anyMatch(line ->
                line.argument("uuid").filter(OFFLINE_PLAYER.toString()::equals).isPresent()));

        GuiSessionView history = click(route, owner, overview, GuiActionKind.STAFF_VIEW_PLAYER_HISTORY);
        assertEquals(GuiScreenKind.STAFF_PLAYER_HISTORY, history.screen());
        assertEquals(1, playerHistoryCards(history).size());
        assertEquals("2", playerHistoryCards(history).getFirst().title()
                .argument("before").orElseThrow());
        assertEquals("3", playerHistoryCards(history).getFirst().title()
                .argument("after").orElseThrow());
        GuiSessionView overviewAgain = click(route, owner, history, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW);
        GuiSessionView preview = click(route, owner, overviewAgain,
                GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_PREVIEW, preview.screen());
        assertTrue(preview.items().stream().noneMatch(item -> item.slot() == 8));
        assertEquals("gui.item.staff.player.offline", itemAt(preview, 22).lore().getFirst().key());
        assertTrue(itemAt(preview, 22).lore().stream().noneMatch(line -> line.arguments().containsKey("uuid")));
        assertTrue(itemAt(preview, 10).lore().stream().anyMatch(line ->
                line.argument("current").filter("Unavailable"::equals).isPresent()));
        assertTrue(preview.actions().stream().allMatch(action -> !action.mutating()));
        PlayerGuiInteractionResult closed = route.click(owner, preview.sessionId(),
                action(preview, GuiActionKind.STAFF_CLOSE).actionId()).toCompletableFuture().join();
        assertTrue(closed.close());
        verify(progress, times(3)).inspect(any(), eq(OFFLINE_PLAYER));
    }

    @Test
    @DisplayName("[Phase 9F-B] Partial known-player searches expose candidates without ambiguous auto-selection")
    void keepsPartialAndAmbiguousKnownPlayerMatchesExplicit() {
        StaffPlayerDirectory directory = directory(List.of(
                new StaffPlayerIdentity(OFFLINE_PLAYER, "AlexOne", false),
                new StaffPlayerIdentity(UUID.fromString("44444444-4444-4444-8444-444444444444"),
                        "AlexTwo", false)));
        StaffGuiService route = service(directory, historySource(List.of(), List.of()), healthyStatus());
        PermissionSubject owner = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView results = route.findPlayers(owner, "alex");

        assertEquals(2, results.actions().stream().filter(action ->
                action.kind() == GuiActionKind.STAFF_SELECT_SEARCH_RESULT).count());
        assertTrue(directory.findPlayer("alex").isEmpty());
        assertEquals(2, directory.searchPlayers("alex").size());

        StaffPlayerDirectory duplicateNames = directory(List.of(
                new StaffPlayerIdentity(OFFLINE_PLAYER, "SameName", false),
                new StaffPlayerIdentity(UUID.fromString("55555555-5555-4555-8555-555555555555"),
                        "SameName", false)));
        StaffGuiService duplicateRoute = service(duplicateNames,
                historySource(List.of(), List.of()), healthyStatus());
        GuiSessionView duplicateResults = duplicateRoute.findPlayers(owner, "SameName");
        List<GuiDisplayItem> duplicateItems = duplicateResults.items().stream()
                .filter(item -> item.actionId().isPresent())
                .filter(item -> duplicateResults.actions().stream().anyMatch(action ->
                        action.actionId().equals(item.actionId().orElseThrow())
                                && action.kind() == GuiActionKind.STAFF_SELECT_SEARCH_RESULT))
                .toList();
        assertEquals(2, duplicateItems.size());
        assertTrue(duplicateItems.stream().allMatch(item -> item.lore().stream().anyMatch(line ->
                line.arguments().containsKey("uuid"))));
        assertTrue(duplicateNames.findPlayer("SameName").isEmpty());

        GuiSessionView none = route.findPlayers(owner, "unknown");
        assertTrue(none.items().stream().anyMatch(item ->
                item.title().key().equals("gui.item.staff.find_player.empty.title")));
        assertTrue(none.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_SELECT_SEARCH_RESULT));
    }

    @Test
    @DisplayName("[Phase 9F-B] Server-known offline identity without Prestige state remains inspectable")
    void rendersTruthfulReadOnlyOverviewWhenCanonicalPrestigeStateIsAbsent() {
        StaffPlayerDirectory directory = directory(List.of(
                new StaffPlayerIdentity(OFFLINE_PLAYER, "KnownPlayer", false)));
        AdministrationException missingState = AdministrationException.authorizationRejected(
                "Prestige", List.of(AuthorizationBlocker.of(
                        AuthorizationBlockerKind.PLAYER_PRESTIGE_STATE_UNAVAILABLE,
                        "Authoritative player Prestige state is unavailable")));
        when(progress.inspect(any(), eq(OFFLINE_PLAYER))).thenReturn(
                CompletableFuture.failedFuture(missingState));
        StaffGuiService route = service(directory, historySource(List.of(), List.of()), healthyStatus());
        PermissionSubject owner = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView results = route.findPlayers(owner, OFFLINE_PLAYER.toString());
        GuiSessionView overview = click(route, owner, results,
                GuiActionKind.STAFF_SELECT_SEARCH_RESULT);

        assertEquals(GuiScreenKind.STAFF_PLAYER_OVERVIEW, overview.screen());
        assertEquals("gui.item.staff.player.state_unavailable", itemAt(overview, 4).lore().getFirst().key());
        assertEquals(Optional.of("Unavailable"), itemAt(overview, 10).lore().getFirst().argument("current"));
        assertEquals(Optional.of("Unavailable"), itemAt(overview, 13).lore().getFirst().argument("value"));
        assertEquals("gui.item.staff.player.offline", itemAt(overview, 8).lore().getFirst().key());
        assertEquals(GuiActionKind.STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW,
                action(overview, GuiActionKind.STAFF_REFRESH_SEARCH_PLAYER_OVERVIEW).kind());
        assertEquals(GuiActionKind.STAFF_COPY_SEARCH_PLAYER_UUID,
                action(overview, GuiActionKind.STAFF_COPY_SEARCH_PLAYER_UUID).kind());
        assertEquals("gui.action.staff.refresh", itemAt(overview, 0).title().key());
        assertEquals("gui.item.staff.player.info.title", itemAt(overview, 8).title().key());
        assertTrue(overview.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW
                        || action.kind() == GuiActionKind.STAFF_VIEW_REQUIREMENTS));
        assertTrue(overview.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_PLAYER_HISTORY));
        assertTrue(overview.actions().stream().allMatch(action -> !action.mutating()));
    }

    @Test
    @DisplayName("[Phase 9F-B] Selected-player Preview renders canonical Why data and refreshes read-only")
    void rendersSelectedPlayerPrestigePreviewWithBoundFirstClickNavigation() {
        AtomicReference<OperationPreview> current = new AtomicReference<>(preview());
        when(progress.inspect(any(), eq(PLAYER))).thenAnswer(invocation ->
                CompletableFuture.completedFuture(new PlayerProgressView(PLAYER, current.get())));
        PermissionSubject owner = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);
        PermissionSubject other = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView selection = onlinePlayers(owner, service.open(owner));
        GuiSessionView overview = click(owner, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiDisplayItem previewNavigation = itemAt(overview, 20);
        assertEquals(GuiItemIcon.PRESTIGE, previewNavigation.icon());
        assertEquals("gui.action.staff.prestige_preview", previewNavigation.title().key());
        assertTrue(previewNavigation.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.staff.prestige_preview.lore")));

        GuiSessionView blocked = click(owner, overview, GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_PREVIEW, blocked.screen());
        assertEquals("tmydwc", blocked.title().argument("player").orElseThrow());
        assertEquals(GuiItemIcon.PROGRESS, itemAt(blocked, 4).icon());
        assertTrue(itemAt(blocked, 4).lore().stream().anyMatch(line ->
                line.key().equals("gui.item.progress.not_ready")));
        assertTrue(blocked.items().stream().noneMatch(item -> item.slot() == 8));
        GuiDisplayItem identity = itemAt(blocked, 22);
        assertEquals(GuiItemIcon.PLAYERS, identity.icon());
        assertEquals("gui.item.staff.prestige_preview.player", identity.title().key());
        assertEquals("tmydwc", identity.title().argument("player").orElseThrow());
        assertEquals(List.of("gui.item.staff.player.online"),
                identity.lore().stream().map(MessageReference::key).toList());
        assertEquals(Optional.of(PLAYER), identity.profilePlayerId());
        GuiDisplayItem balance = itemAt(blocked, 10);
        assertTrue(balance.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.balance.current")
                        && line.argument("current").filter("$1"::equals).isPresent()));
        assertTrue(balance.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.balance.missing")
                        && line.argument("missing").filter("$7"::equals).isPresent()));
        assertTrue(balance.lore().stream().noneMatch(line ->
                line.key().equals("gui.item.balance.projected")
                        || line.argument("projected").isPresent()));
        GuiDisplayItem requirements = itemAt(blocked, 13);
        assertTrue(requirements.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.requirements.progress.incomplete")
                        && line.argument("progress").filter("1"::equals).isPresent()
                        && line.argument("total").filter("2"::equals).isPresent()));
        assertTrue(requirements.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.requirement.money.not_met")));
        assertTrue(requirements.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.requirement.total_skill_level.met")));
        assertTrue(itemAt(blocked, 16).lore().stream().anyMatch(line ->
                line.argument("value").filter("$1"::equals).isPresent()));
        GuiAction refresh = action(blocked, GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW);
        assertEquals(GuiItemIcon.REFRESH, itemAt(blocked, 20).icon());
        assertEquals(Optional.of(refresh.actionId()), itemAt(blocked, 20).actionId());
        assertEquals(Optional.of(action(blocked, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW).actionId()),
                itemAt(blocked, 18).actionId());
        assertEquals(Optional.of(action(blocked, GuiActionKind.STAFF_VIEW_PLAYER_HISTORY).actionId()),
                itemAt(blocked, 24).actionId());
        assertEquals(Optional.of(action(blocked, GuiActionKind.STAFF_CLOSE).actionId()),
                itemAt(blocked, 26).actionId());
        assertTrue(blocked.actions().stream().filter(action -> action.kind() != GuiActionKind.STAFF_CLOSE)
                .allMatch(action -> action.targetPlayer().filter(PLAYER::equals).isPresent()));
        assertTrue(blocked.actions().stream().allMatch(action -> !action.mutating()));
        assertTrue(blocked.actions().stream().noneMatch(StaffGuiServiceTest::mutationAction));

        AdministrationException mismatch = assertThrows(AdministrationException.class,
                () -> service.click(other, blocked.sessionId(), refresh.actionId()));
        assertEquals("gui.session.actor_mismatch", mismatch.code());
        AdministrationException forged = assertThrows(AdministrationException.class,
                () -> service.click(owner, blocked.sessionId(), UUID.randomUUID()));
        assertEquals("gui.action.forged", forged.code());
        revision.set(Optional.of(new ConfigRevisionId("phase9f-b-preview-stale")));
        AdministrationException stale = assertThrows(AdministrationException.class,
                () -> service.click(owner, blocked.sessionId(), refresh.actionId()));
        assertEquals("gui.action.stale", stale.code());

        revision.set(Optional.of(REVISION));
        current.set(preview(List.of(
                leaf("balance", "12", "8", ExplanationStatus.SATISFIED),
                leaf("total_level", "1", "1", ExplanationStatus.SATISFIED)), true));
        GuiSessionView ready = click(owner, blocked, GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW);
        assertTrue(itemAt(ready, 4).lore().stream().anyMatch(line ->
                line.key().equals("gui.item.progress.ready")));
        assertTrue(ready.items().stream().noneMatch(item -> item.slot() == 8));
        assertEquals("gui.item.staff.prestige_preview.player", itemAt(ready, 22).title().key());
        assertTrue(itemAt(ready, 10).lore().stream().anyMatch(line ->
                line.key().equals("gui.item.balance.projected")
                        && line.argument("current").filter("$12"::equals).isPresent()
                        && line.argument("projected").filter("$8"::equals).isPresent()));
        AdministrationException replay = assertThrows(AdministrationException.class,
                () -> service.click(owner, blocked.sessionId(), refresh.actionId()));
        assertEquals("gui.session.expired", replay.code());

        GuiSessionView returned = click(owner, ready, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW);
        assertEquals(GuiScreenKind.STAFF_PLAYER_OVERVIEW, returned.screen());
        GuiSessionView reopened = click(owner, returned, GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW);
        PlayerGuiInteractionResult closed = service.click(owner, reopened.sessionId(),
                action(reopened, GuiActionKind.STAFF_CLOSE).actionId()).toCompletableFuture().join();
        assertTrue(closed.close());
        verify(progress, times(5)).inspect(any(), eq(PLAYER));
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff Preview shows truthful unavailable balance without reconstruction")
    void staffPrestigePreviewDoesNotInventUnavailableBalanceProjection() {
        OperationPreview unavailable = withoutBalanceProjection(preview());
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, unavailable)));
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView selection = onlinePlayers(subject, service.open(subject));
        GuiSessionView overview = click(subject, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiSessionView staffPreview = click(subject, overview, GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW);

        GuiDisplayItem balance = itemAt(staffPreview, 10);
        assertTrue(balance.lore().stream().anyMatch(line ->
                line.key().equals("gui.item.balance.current")
                        && line.argument("current").filter("Unavailable"::equals).isPresent()));
        assertTrue(balance.lore().stream().noneMatch(line -> line.argument("projected").isPresent()));
        assertTrue(itemAt(staffPreview, 16).lore().stream().anyMatch(line ->
                line.argument("value").filter("$1"::equals).isPresent()));
        assertTrue(staffPreview.actions().stream().allMatch(action -> !action.mutating()));
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff Preview shows only current balance when canonical shortfall is unavailable")
    void staffPrestigePreviewDoesNotInventUnavailableShortfall() {
        OperationPreview noShortfall = withoutBalanceShortfall(preview());
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, noShortfall)));
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView selection = onlinePlayers(subject, service.open(subject));
        GuiSessionView overview = click(subject, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiSessionView staffPreview = click(subject, overview, GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW);

        GuiDisplayItem balance = itemAt(staffPreview, 10);
        assertEquals(1, balance.lore().size());
        assertEquals("gui.item.balance.current", balance.lore().getFirst().key());
        assertEquals(Optional.of("$1"), balance.lore().getFirst().argument("current"));
        assertTrue(balance.lore().stream().noneMatch(line ->
                line.argument("missing").isPresent() || line.argument("projected").isPresent()));
    }

    @Test
    @DisplayName("[Phase 9F-B] Requirements use stable server-owned paging without truncating canonical leaves")
    void paginatesLargerCanonicalRequirementSets() {
        List<ExplanationNode> leaves = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(index -> leaf("metric_" + index, Integer.toString(index), Integer.toString(index),
                        ExplanationStatus.SATISFIED))
                .toList();
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, preview(leaves, true))));
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView selection = onlinePlayers(subject, service.open(subject));
        GuiSessionView overview = click(subject, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiSessionView first = click(subject, overview, GuiActionKind.STAFF_VIEW_REQUIREMENTS);
        GuiSessionView second = click(subject, first, GuiActionKind.STAFF_REQUIREMENTS_NEXT);

        assertEquals(7, requirementCards(first).size());
        assertEquals(1, requirementCards(second).size());
        assertTrue(first.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_REQUIREMENTS_NEXT && action.page().orElseThrow() == 1));
        assertTrue(second.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_REQUIREMENTS_PREVIOUS && action.page().orElseThrow() == 0));
        assertTrue(List.of(first, second).stream().flatMap(view -> view.actions().stream())
                .allMatch(action -> !action.mutating()));
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff sessions reject cross-actor, forged, replayed, stale, and logout authority")
    void preservesActorBoundSingleUseRevisionAwareAuthority() {
        PermissionSubject owner = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW);
        PermissionSubject other = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);
        GuiSessionView dashboard = service.open(owner);
        GuiAction players = action(dashboard, GuiActionKind.STAFF_OPEN_PLAYERS);

        AdministrationException mismatch = assertThrows(AdministrationException.class,
                () -> service.click(other, dashboard.sessionId(), players.actionId()));
        assertEquals("gui.session.actor_mismatch", mismatch.code());
        AdministrationException forged = assertThrows(AdministrationException.class,
                () -> service.click(owner, dashboard.sessionId(), UUID.randomUUID()));
        assertEquals("gui.action.forged", forged.code());

        GuiSessionView management = service.click(owner, dashboard.sessionId(), players.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
        AdministrationException replay = assertThrows(AdministrationException.class,
                () -> service.click(owner, dashboard.sessionId(), players.actionId()));
        assertEquals("gui.session.expired", replay.code());

        GuiAction onlinePlayers = action(management, GuiActionKind.STAFF_OPEN_ONLINE_PLAYERS);
        revision.set(Optional.of(new ConfigRevisionId("phase9f-b-changed")));
        AdministrationException stale = assertThrows(AdministrationException.class,
                () -> service.click(owner, management.sessionId(), onlinePlayers.actionId()));
        assertEquals("gui.action.stale", stale.code());

        revision.set(Optional.of(REVISION));
        GuiSessionView logout = service.open(owner);
        GuiAction logoutAction = action(logout, GuiActionKind.STAFF_OPEN_PLAYERS);
        service.invalidateStaff(STAFF);
        AdministrationException expired = assertThrows(AdministrationException.class,
                () -> service.click(owner, logout.sessionId(), logoutAction.actionId()));
        assertEquals("gui.session.expired", expired.code());
    }

    @Test
    @DisplayName("[Phase 9F-B] Empty online selector remains explicit without a fabricated player")
    void emptyOnlineSelectionRemainsTruthful() {
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW);
        GuiSessionView dashboard = service.open(subject);
        GuiSessionView selection = onlinePlayers(subject, dashboard);
        StaffGuiService disconnected = new StaffGuiService(new GuiSessionService(revision::get,
                (ignoredSubject, ignoredAction) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK), progress,
                List::<StaffPlayerIdentity>of, revision::get,
                (offset, limit) -> CompletableFuture.completedFuture(
                        new StaffHistorySource.Page(List.of(), offset > 0, false)),
                healthyStatus());

        // The original selection authority cannot be transferred into another service/session store.
        assertFalse(selection.actions().stream().anyMatch(GuiAction::mutating));
        GuiSessionView empty = disconnected.open(subject);
        GuiSessionView noPlayers = onlinePlayers(disconnected, subject, empty);
        assertTrue(noPlayers.items().stream().anyMatch(item ->
                item.title().key().equals("gui.item.staff.no_players.title")));
        assertTrue(noPlayers.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_SELECT_PLAYER));
        verify(progress, times(0)).inspect(any(), any());
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff Preview preserves identity across online and offline refreshes")
    void staffPrestigePreviewRefreshTracksOnlineStateWithoutLosingSelection() {
        AtomicReference<List<StaffPlayerIdentity>> known = new AtomicReference<>(
                List.of(new StaffPlayerIdentity(PLAYER, "tmydwc")));
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, preview())));
        StaffPlayerDirectory directory = new StaffPlayerDirectory() {
            @Override
            public List<StaffPlayerIdentity> onlinePlayers() {
                return known.get().stream().filter(StaffPlayerIdentity::online).toList();
            }

            @Override
            public List<StaffPlayerIdentity> knownPlayers() {
                return known.get();
            }
        };
        StaffGuiService route = new StaffGuiService(new GuiSessionService(revision::get,
                (ignoredSubject, ignoredAction) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK), progress,
                directory, revision::get, historySource(List.of(), List.of()), healthyStatus());
        PermissionSubject subject = staff(STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);

        GuiSessionView selection = onlinePlayers(route, subject, route.open(subject));
        GuiSessionView overview = click(route, subject, selection, GuiActionKind.STAFF_SELECT_PLAYER);
        GuiAction copy = action(overview, GuiActionKind.STAFF_COPY_PLAYER_UUID);
        PlayerGuiInteractionResult copiedResult = route.click(subject, overview.sessionId(), copy.actionId())
                .toCompletableFuture().join();
        GuiSessionView copied = copiedResult.nextView().orElseThrow();
        assertEquals("gui.staff.player.uuid.copy", copiedResult.messages().getFirst().key());
        assertEquals(PLAYER.toString(), copiedResult.messages().getFirst().argument("uuid").orElseThrow());
        assertEquals(Optional.of(PLAYER), copy.targetPlayer());
        assertFalse(copy.mutating());

        GuiAction overviewRefresh = action(copied, GuiActionKind.STAFF_REFRESH_PLAYER_OVERVIEW);
        known.set(List.of(new StaffPlayerIdentity(PLAYER, "tmydwc", false)));
        PlayerGuiInteractionResult offlineOverviewResult = route.click(subject, copied.sessionId(),
                overviewRefresh.actionId()).toCompletableFuture().join();
        GuiSessionView offlineOverview = offlineOverviewResult.nextView().orElseThrow();
        assertTrue(offlineOverviewResult.messages().isEmpty());
        assertEquals("gui.item.staff.player.offline", itemAt(offlineOverview, 8).lore().getFirst().key());

        known.set(List.of(new StaffPlayerIdentity(PLAYER, "tmydwc", true)));
        GuiSessionView onlineOverview = click(route, subject, offlineOverview,
                GuiActionKind.STAFF_REFRESH_PLAYER_OVERVIEW);
        assertEquals("gui.item.staff.player.online", itemAt(onlineOverview, 8).lore().getFirst().key());
        GuiSessionView staffPreview = click(route, subject, onlineOverview,
                GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW);
        GuiAction refresh = action(staffPreview, GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW);
        known.set(List.of(new StaffPlayerIdentity(PLAYER, "tmydwc", false)));

        GuiSessionView offline = click(route, subject, staffPreview,
                GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW);
        assertTrue(offline.items().stream().noneMatch(item -> item.slot() == 8));
        assertEquals("gui.item.staff.player.offline", itemAt(offline, 22).lore().getFirst().key());
        assertEquals(Optional.of(PLAYER), itemAt(offline, 22).profilePlayerId());

        known.set(List.of(new StaffPlayerIdentity(PLAYER, "tmydwc", true)));
        GuiSessionView online = click(route, subject, offline,
                GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW);
        assertTrue(online.items().stream().noneMatch(item -> item.slot() == 8));
        assertEquals("gui.item.staff.player.online", itemAt(online, 22).lore().getFirst().key());
        assertEquals(PLAYER, refresh.targetPlayer().orElseThrow());
        assertTrue(List.of(copied, offlineOverview, onlineOverview, staffPreview, offline, online).stream()
                .flatMap(view -> view.actions().stream()).allMatch(action -> !action.mutating()));
        verify(progress, times(7)).inspect(any(), eq(PLAYER));
    }

    private GuiSessionView click(
            PermissionSubject subject,
            GuiSessionView view,
            GuiActionKind kind) {
        return click(service, subject, view, kind);
    }

    private GuiSessionView onlinePlayers(
            PermissionSubject subject,
            GuiSessionView dashboard) {
        return onlinePlayers(service, subject, dashboard);
    }

    private static GuiSessionView onlinePlayers(
            StaffGuiService route,
            PermissionSubject subject,
            GuiSessionView dashboard) {
        GuiSessionView management = click(route, subject, dashboard, GuiActionKind.STAFF_OPEN_PLAYERS);
        return click(route, subject, management, GuiActionKind.STAFF_OPEN_ONLINE_PLAYERS);
    }

    private static GuiSessionView click(
            StaffGuiService route,
            PermissionSubject subject,
            GuiSessionView view,
            GuiActionKind kind) {
        GuiAction action = action(view, kind);
        return route.click(subject, view.sessionId(), action.actionId())
                .toCompletableFuture().join().nextView().orElseThrow();
    }

    private static GuiAction action(GuiSessionView view, GuiActionKind kind) {
        return view.actions().stream().filter(action -> action.kind() == kind).findFirst().orElseThrow();
    }

    private static PermissionSubject staff(UUID playerId, String... permissions) {
        return new PermissionSubject(new Actor("player", Optional.of(playerId), "Staff"), Set.of(permissions));
    }

    private static StaffSystemStatusSource healthyStatus() {
        StaffSystemStatusSource.Summary summary = new StaffSystemStatusSource.Summary(
                StaffSystemStatusSource.Health.HEALTHY, true, 2, 2);
        return new StaffSystemStatusSource() {
            @Override
            public Summary summary() {
                return summary;
            }

            @Override
            public ConfigurationSummary configuration() {
                return new ConfigurationSummary(true, true, "P1 – P50", 2, 1, 1, 3,
                        List.of("Vault", "mcMMO", "LuckPerms"));
            }

            @Override
            public java.util.concurrent.CompletionStage<Snapshot> inspect() {
                return CompletableFuture.completedFuture(new Snapshot(summary, List.of(
                        new Component("Vault", Health.HEALTHY, ""),
                        new Component("Persistence", Health.HEALTHY, "SQLite schema is current"))));
            }
        };
    }

    private StaffGuiService service(StaffHistorySource history, StaffSystemStatusSource status) {
        return service(directory(List.of(new StaffPlayerIdentity(PLAYER, "tmydwc"))), history, status);
    }

    private StaffGuiService service(
            StaffPlayerDirectory directory,
            StaffHistorySource history,
            StaffSystemStatusSource status) {
        GuiSessionService sessions = new GuiSessionService(revision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        return new StaffGuiService(sessions, progress, directory, revision::get, history, status);
    }

    private static StaffPlayerDirectory directory(List<StaffPlayerIdentity> knownPlayers) {
        return new StaffPlayerDirectory() {
            @Override
            public List<StaffPlayerIdentity> onlinePlayers() {
                return knownPlayers.stream().filter(StaffPlayerIdentity::online).toList();
            }

            @Override
            public List<StaffPlayerIdentity> knownPlayers() {
                return knownPlayers;
            }
        };
    }

    private static StaffSystemStatusSource status(
            StaffSystemStatusSource.Summary summary,
            List<StaffSystemStatusSource.Component> components) {
        return new StaffSystemStatusSource() {
            @Override
            public Summary summary() {
                return summary;
            }

            @Override
            public java.util.concurrent.CompletionStage<Snapshot> inspect() {
                return CompletableFuture.completedFuture(new Snapshot(summary, components));
            }
        };
    }

    private static GuiDisplayItem itemAt(GuiSessionView view, int slot) {
        return view.items().stream().filter(item -> item.slot() == slot).findFirst().orElseThrow();
    }

    private static List<GuiDisplayItem> historyCards(GuiSessionView view) {
        return view.items().stream().filter(item ->
                item.title().key().equals("gui.item.staff.history.entry.title")).toList();
    }

    private static List<GuiDisplayItem> playerHistoryCards(GuiSessionView view) {
        return view.items().stream().filter(item ->
                item.title().key().equals("gui.item.staff.player_history.entry.title")).toList();
    }

    private static StaffHistorySource historySource(
            List<StaffHistorySource.Entry> globalEntries,
            List<StaffHistorySource.Entry> playerEntries) {
        return new StaffHistorySource() {
            @Override
            public java.util.concurrent.CompletionStage<Page> recent(int offset, int limit) {
                return historyPage(globalEntries, offset, limit);
            }

            @Override
            public java.util.concurrent.CompletionStage<Page> forPlayer(
                    UUID playerId,
                    int offset,
                    int limit) {
                return historyPage(playerEntries, offset, limit);
            }
        };
    }

    private static java.util.concurrent.CompletionStage<StaffHistorySource.Page> historyPage(
            List<StaffHistorySource.Entry> entries,
            int offset,
            int limit) {
        int last = Math.min(offset + limit, entries.size());
        List<StaffHistorySource.Entry> page = offset >= entries.size()
                ? List.of() : entries.subList(offset, last);
        return CompletableFuture.completedFuture(new StaffHistorySource.Page(
                page, offset > 0, last < entries.size()));
    }

    private static List<GuiDisplayItem> statusCards(GuiSessionView view) {
        return view.items().stream().filter(item ->
                item.title().key().startsWith("gui.item.staff.system_status.component.title.")).toList();
    }

    private static boolean mutationAction(GuiAction action) {
        return switch (action.kind()) {
            case PREPARE_PRESTIGE, CONFIRM_PRESTIGE, EDIT_CONFIGURATION,
                    ADD_CONFIGURATION_VALUE, REMOVE_CONFIGURATION_VALUE,
                    ADD_CONFIGURATION_OBJECT, EDIT_CONFIGURATION_OBJECT,
                    REMOVE_CONFIGURATION_OBJECT, ADD_STAGE, APPLY_CONFIGURATION,
                    ROLLBACK_CONFIGURATION, DELETE_STAGE,
                    STAFF_CONFIRM_PRESTIGE_ADJUSTMENT, STAFF_CONFIRM_PRESTIGE_MONEY,
                    STAFF_CONFIRM_PRESTIGE_REWARD, STAFF_CONFIRM_TOTAL_SKILL_LEVEL,
                    STAFF_CONFIRM_SCALING_LINEAR_INCREMENT, STAFF_CONFIRM_SCALING_OVERRIDE_EDIT,
                    STAFF_CONFIRM_SCALING_OVERRIDE_REMOVAL -> true;
            default -> false;
        };
    }

    private static final class GuidedConfigurationStub implements GuidedConfigurationAdministration {
        private static final UUID REVIEW = UUID.fromString("44444444-4444-4444-8444-444444444444");
        private int reviewCalls;
        private int confirmCalls;
        private int rewardReviewCalls;
        private int rewardConfirmCalls;
        private int totalSkillReviewCalls;
        private int totalSkillConfirmCalls;
        private int scalingReviewCalls;
        private int scalingConfirmCalls;
        private int scalingOverrideReviewCalls;
        private int scalingOverrideConfirmCalls;
        private GuidedScalingParameter lastScalingParameter;
        private final boolean overridePresent;
        private final boolean complexScaling;

        private GuidedConfigurationStub() {
            this(true, false);
        }

        private GuidedConfigurationStub(boolean overridePresent, boolean complexScaling) {
            this.overridePresent = overridePresent;
            this.complexScaling = complexScaling;
        }

        @Override
        public PrestigeLevelPage prestigeLevels(PermissionSubject subject, int pageIndex, int pageSize) {
            return new PrestigeLevelPage(REVISION, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                    pageIndex, false, true);
        }

        @Override
        public PrestigeLevelConfigurationView prestigeLevel(PermissionSubject subject, long prestigeLevel) {
            return new PrestigeLevelConfigurationView(REVISION, prestigeLevel, true, "6", "6", 1, "2",
                    "Linear with level override", true, true, true, true);
        }

        @Override
        public MoneyAmountPage moneyAmounts(
                PermissionSubject subject,
                long prestigeLevel,
                int pageIndex,
                int pageSize) {
            return new MoneyAmountPage(REVISION, prestigeLevel, "7",
                    List.of("1", "2", "3", "4", "5", "6", "7"), pageIndex, false, true);
        }

        @Override
        public GuidedNumericConfigurationInput moneyInput(PermissionSubject subject, long prestigeLevel) {
            return new GuidedNumericConfigurationInput(REVISION, prestigeLevel, "6");
        }

        @Override
        public String validateMoneyInput(
                PermissionSubject subject,
                long prestigeLevel,
                String newAmount,
                ConfigRevisionId expectedRevision) {
            return validAmount(newAmount, expectedRevision, "config.gui.money.invalid");
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedMoneyConfigurationReview> reviewMoney(
                PermissionSubject subject,
                long prestigeLevel,
                String newAmount) {
            return reviewMoney(subject, prestigeLevel, newAmount, REVISION);
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedMoneyConfigurationReview> reviewMoney(
                PermissionSubject subject,
                long prestigeLevel,
                String newAmount,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            reviewCalls++;
            return CompletableFuture.completedFuture(new GuidedMoneyConfigurationReview(REVIEW, REVISION,
                    prestigeLevel, "6", newAmount, CLOCK.instant().plus(Duration.ofMinutes(5))));
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedMoneyConfigurationResult> confirmMoney(
                PermissionSubject subject,
                UUID reviewId) {
            confirmCalls++;
            return CompletableFuture.completedFuture(new GuidedMoneyConfigurationResult(REVISION,
                    new ConfigRevisionId("applied-revision"), 1, "7", "1"));
        }

        @Override
        public RewardAmountPage rewardAmounts(
                PermissionSubject subject,
                long prestigeLevel,
                int pageIndex,
                int pageSize) {
            return new RewardAmountPage(REVISION, prestigeLevel, "1",
                    List.of("1", "2", "3", "4", "5", "6", "7"), pageIndex, false, true);
        }

        @Override
        public GuidedNumericConfigurationInput rewardInput(PermissionSubject subject, long prestigeLevel) {
            return new GuidedNumericConfigurationInput(REVISION, prestigeLevel, "2");
        }

        @Override
        public String validateRewardInput(
                PermissionSubject subject,
                long prestigeLevel,
                String newAmount,
                ConfigRevisionId expectedRevision) {
            return validAmount(newAmount, expectedRevision, "config.gui.reward.invalid");
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedRewardConfigurationReview> reviewReward(
                PermissionSubject subject,
                long prestigeLevel,
                String newAmount) {
            return reviewReward(subject, prestigeLevel, newAmount, REVISION);
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedRewardConfigurationReview> reviewReward(
                PermissionSubject subject,
                long prestigeLevel,
                String newAmount,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            rewardReviewCalls++;
            return CompletableFuture.completedFuture(new GuidedRewardConfigurationReview(
                    UUID.fromString("55555555-5555-4555-8555-555555555555"), REVISION,
                    prestigeLevel, "2", newAmount, CLOCK.instant().plus(Duration.ofMinutes(5))));
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedRewardConfigurationResult> confirmReward(
                PermissionSubject subject,
                UUID reviewId) {
            rewardConfirmCalls++;
            return CompletableFuture.completedFuture(new GuidedRewardConfigurationResult(REVISION,
                    new ConfigRevisionId("applied-reward-revision"), 1, "1", "2"));
        }

        @Override
        public GuidedRequirementConfigurationView requirements(
                PermissionSubject subject,
                long prestigeLevel) {
            return new GuidedRequirementConfigurationView(REVISION, prestigeLevel, List.of(
                    new GuidedRequirementConfigurationEntry("money",
                            GuidedRequirementConfigurationEntry.Kind.MONEY, "Money", "6", !complexScaling),
                    new GuidedRequirementConfigurationEntry("skill",
                            GuidedRequirementConfigurationEntry.Kind.TOTAL_SKILL_LEVEL,
                            "Total Skill Level", "1", !complexScaling)), complexScaling);
        }

        @Override
        public GuidedNumericConfigurationInput totalSkillLevelInput(
                PermissionSubject subject,
                long prestigeLevel) {
            return new GuidedNumericConfigurationInput(REVISION, prestigeLevel, "1");
        }

        @Override
        public String validateTotalSkillLevelInput(
                PermissionSubject subject,
                long prestigeLevel,
                String newTarget,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            if (newTarget == null || !newTarget.matches("[0-9]+")) {
                throw new AdministrationException("config.gui.total_skill_level.invalid",
                        "Invalid count.", "Enter a non-negative whole number.");
            }
            return new java.math.BigInteger(newTarget).toString();
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedTotalSkillLevelReview> reviewTotalSkillLevel(
                PermissionSubject subject,
                long prestigeLevel,
                String newTarget,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            totalSkillReviewCalls++;
            return CompletableFuture.completedFuture(new GuidedTotalSkillLevelReview(
                    UUID.fromString("99999999-9999-4999-8999-999999999999"), REVISION,
                    prestigeLevel, "1", newTarget, CLOCK.instant().plus(Duration.ofMinutes(5))));
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedTotalSkillLevelResult> confirmTotalSkillLevel(
                PermissionSubject subject,
                UUID reviewId) {
            totalSkillConfirmCalls++;
            return CompletableFuture.completedFuture(new GuidedTotalSkillLevelResult(REVISION,
                    new ConfigRevisionId("applied-skill-revision"), 6, "1", "2"));
        }

        @Override
        public GuidedScalingConfigurationView scaling(PermissionSubject subject, long prestigeLevel) {
            return new GuidedScalingConfigurationView(REVISION, prestigeLevel,
                    net.maddkraft.maddprestige.core.scaling.SegmentScalingMode.LINEAR,
                    "1", "0.5", "6", overridePresent ? Optional.of("3") : Optional.empty(),
                    complexScaling ? Set.of()
                            : Set.of(GuidedScalingParameter.LINEAR_BASE, GuidedScalingParameter.LINEAR_INCREMENT),
                    complexScaling, !complexScaling);
        }

        @Override
        public GuidedNumericConfigurationInput scalingInput(
                PermissionSubject subject,
                long prestigeLevel,
                GuidedScalingParameter parameter) {
            lastScalingParameter = parameter;
            return new GuidedNumericConfigurationInput(REVISION, prestigeLevel,
                    parameter == GuidedScalingParameter.LINEAR_BASE ? "1" : "0.5");
        }

        @Override
        public String validateScalingInput(
                PermissionSubject subject,
                long prestigeLevel,
                GuidedScalingParameter parameter,
                String newValue,
                ConfigRevisionId expectedRevision) {
            lastScalingParameter = parameter;
            assertEquals(REVISION, expectedRevision);
            try {
                if (newValue == null || !newValue.matches("[0-9]+(?:\\.[0-9]+)?")) {
                    throw new NumberFormatException();
                }
                return new java.math.BigDecimal(newValue).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException exception) {
                throw new AdministrationException("config.gui.scaling.invalid",
                        "Invalid increment.", "Enter another value.");
            }
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedScalingConfigurationReview> reviewScaling(
                PermissionSubject subject,
                long prestigeLevel,
                GuidedScalingParameter parameter,
                String newValue,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            scalingReviewCalls++;
            lastScalingParameter = parameter;
            String current = parameter == GuidedScalingParameter.LINEAR_BASE ? "1" : "0.5";
            return CompletableFuture.completedFuture(new GuidedScalingConfigurationReview(
                    UUID.fromString("66666666-6666-4666-8666-666666666666"), REVISION,
                    prestigeLevel, parameter, current, newValue, CLOCK.instant().plus(Duration.ofMinutes(5))));
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedScalingConfigurationResult> confirmScaling(
                PermissionSubject subject,
                UUID reviewId) {
            scalingConfirmCalls++;
            return CompletableFuture.completedFuture(new GuidedScalingConfigurationResult(REVISION,
                    new ConfigRevisionId("applied-scaling-revision"), 6,
                    GuidedScalingParameter.LINEAR_INCREMENT, "0.5", "1"));
        }

        @Override
        public GuidedNumericConfigurationInput scalingOverrideInput(
                PermissionSubject subject,
                long prestigeLevel) {
            return new GuidedNumericConfigurationInput(REVISION, prestigeLevel, overridePresent ? "3" : "6");
        }

        @Override
        public String validateScalingOverrideInput(
                PermissionSubject subject,
                long prestigeLevel,
                String newValue,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            try {
                if (newValue == null || !newValue.matches("[0-9]+(?:\\.[0-9]+)?")) {
                    throw new NumberFormatException();
                }
                return new java.math.BigDecimal(newValue).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException exception) {
                throw new AdministrationException("config.gui.scaling.override.invalid",
                        "Invalid override.", "Enter another value.");
            }
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedScalingOverrideReview> reviewScalingOverride(
                PermissionSubject subject,
                long prestigeLevel,
                String newValue,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            scalingOverrideReviewCalls++;
            return CompletableFuture.completedFuture(new GuidedScalingOverrideReview(
                    UUID.fromString("77777777-7777-4777-8777-777777777777"), REVISION,
                    prestigeLevel, overridePresent ? Optional.of("3") : Optional.empty(), Optional.of(newValue),
                    Optional.of("6"), Optional.of("8"),
                    CLOCK.instant().plus(Duration.ofMinutes(5))));
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedScalingOverrideReview> reviewScalingOverrideRemoval(
                PermissionSubject subject,
                long prestigeLevel,
                ConfigRevisionId expectedRevision) {
            assertEquals(REVISION, expectedRevision);
            scalingOverrideReviewCalls++;
            return CompletableFuture.completedFuture(new GuidedScalingOverrideReview(
                    UUID.fromString("88888888-8888-4888-8888-888888888888"), REVISION,
                    prestigeLevel, Optional.of("3"), Optional.empty(), Optional.of("6"), Optional.of("7"),
                    CLOCK.instant().plus(Duration.ofMinutes(5))));
        }

        @Override
        public java.util.concurrent.CompletionStage<GuidedScalingOverrideResult> confirmScalingOverride(
                PermissionSubject subject,
                UUID reviewId) {
            scalingOverrideConfirmCalls++;
            return CompletableFuture.completedFuture(new GuidedScalingOverrideResult(REVISION,
                    new ConfigRevisionId("applied-override-revision"), 6, Optional.of("3"), Optional.of("4")));
        }

        private static String validAmount(
                String value,
                ConfigRevisionId expectedRevision,
                String code) {
            assertEquals(REVISION, expectedRevision);
            try {
                if (value == null || !value.matches("[0-9]+(?:\\.[0-9]+)?")) {
                    throw new NumberFormatException();
                }
                java.math.BigDecimal amount = new java.math.BigDecimal(value);
                if (amount.signum() <= 0 || amount.compareTo(java.math.BigDecimal.valueOf(10_000)) > 0) {
                    throw new NumberFormatException();
                }
                return amount.stripTrailingZeros().toPlainString();
            } catch (NumberFormatException exception) {
                throw new AdministrationException(code, "Invalid amount.", "Enter another value.");
            }
        }
    }

    private static StaffHistorySource.Entry historyEntry(
            String player,
            long before,
            long after,
            StaffHistorySource.Outcome outcome,
            boolean costRecorded,
            boolean rewardRecorded,
            Instant occurredAt) {
        UUID id = UUID.nameUUIDFromBytes((player + ':' + before + ':' + after + ':' + occurredAt)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new StaffHistorySource.Entry(id, player, before, after, outcome,
                costRecorded, rewardRecorded, Optional.empty(), Optional.empty(),
                costRecorded ? Optional.of("$5") : Optional.empty(),
                rewardRecorded ? Optional.of("$1") : Optional.empty(), occurredAt);
    }

    private static List<GuiDisplayItem> requirementCards(GuiSessionView view) {
        return view.items().stream().filter(item ->
                item.title().key().startsWith("gui.item.staff.requirement.")
                        && !item.title().key().contains("summary"))
                .toList();
    }

    private static OperationPreview preview() {
        ExplanationNode money = leaf("balance", "1", "8", ExplanationStatus.UNSATISFIED);
        ExplanationNode skill = leaf("total_level", "1", "1", ExplanationStatus.SATISFIED);
        return preview(List.of(money, skill), false);
    }

    private static OperationPreview preview(List<ExplanationNode> leaves, boolean executable) {
        return preview(PLAYER, leaves, executable);
    }

    private static OperationPreview preview(
            UUID playerId,
            List<ExplanationNode> leaves,
            boolean executable) {
        long satisfied = leaves.stream().filter(leaf -> leaf.status() == ExplanationStatus.SATISFIED).count();
        ExplanationNode requirements = new ExplanationNode("requirement.group",
                executable ? ExplanationStatus.SATISFIED : ExplanationStatus.UNSATISFIED,
                executable ? "Ready" : "Not Ready",
                Map.of("mode", "ALL", "progress", Long.toString(satisfied),
                        "threshold", Integer.toString(leaves.size())), leaves);
        return new OperationPreview(OperationKind.PRESTIGE, playerId, executable, "Prestige 6 → 7",
                Optional.of(requirements), List.of("$5"), List.of("$1"), List.of(), List.of(), List.of(),
                REVISION, Map.of(), List.of(), List.of(
                        MessageReference.of("command.preview.prestige_state_change",
                                "current_prestige", 6, "target_prestige", 7,
                                "current_lifetime", 6, "target_lifetime", 7),
                        MessageReference.of("command.preview.cost", "id", "vault-cost", "provider", "vault",
                                "type", "WITHDRAW", "amount", "5", "canonical", "5", "value", "Money"),
                        MessageReference.of("command.preview.reward", "id", "vault-reward", "provider", "vault",
                                "type", "DEPOSIT", "amount", "1", "canonical", "1", "value", "Money"),
                executable
                        ? MessageReference.of("command.preview.balance_projection",
                                "current", "12", "projected", "8")
                        : MessageReference.of("command.preview.balance_projection",
                                "current", "1", "projected", "-3", "missing", "7")), List.of());
    }

    private static OperationPreview withoutBalanceProjection(OperationPreview preview) {
        return new OperationPreview(preview.kind(), preview.playerId(), preview.executable(),
                preview.stateChange(), preview.requirements(), preview.costs(), preview.rewards(),
                preview.milestones(), preview.consequences(), preview.blockers(), preview.configRevision(),
                preview.providerGenerations(), preview.externalUncertainty(), preview.semanticDetails().stream()
                        .filter(line -> !line.key().equals("command.preview.balance_projection")).toList(),
                preview.authorizationBlockers());
    }

    private static OperationPreview withoutBalanceShortfall(OperationPreview preview) {
        return new OperationPreview(preview.kind(), preview.playerId(), preview.executable(),
                preview.stateChange(), preview.requirements(), preview.costs(), preview.rewards(),
                preview.milestones(), preview.consequences(), preview.blockers(), preview.configRevision(),
                preview.providerGenerations(), preview.externalUncertainty(), preview.semanticDetails().stream()
                        .map(line -> line.key().equals("command.preview.balance_projection")
                                ? MessageReference.of(line.key(),
                                        "current", line.argument("current").orElseThrow(),
                                        "projected", line.argument("projected").orElseThrow())
                                : line).toList(), preview.authorizationBlockers());
    }

    private static ExplanationNode leaf(
            String metric,
            String current,
            String target,
            ExplanationStatus status) {
        Map<String, String> facts = metric.equals("balance")
                ? Map.of("provider", "vault_balance", "metric", metric, "operator", "GREATER_OR_EQUAL",
                        "current", current, "target", target)
                : Map.of("metric", metric, "current", current, "target", target);
        return new ExplanationNode("requirement.leaf", status, status.name(), facts, List.of());
    }
}
