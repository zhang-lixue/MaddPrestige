package net.maddkraft.maddprestige.core.admin.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdministrationService;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustment;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustmentKind;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustmentPolicy;
import net.maddkraft.maddprestige.core.admin.OperationKind;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.PrestigeAdministrationStore;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressView;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffPrestigeMutationGuiTest {
    private static final UUID STAFF = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_STAFF = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-3000-8000-000000000001");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase9f-c1");
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AtomicReference<Optional<ConfigRevisionId>> activeRevision =
            new AtomicReference<>(Optional.of(REVISION));
    private MemoryStore store;
    private StaffGuiService service;

    @BeforeEach
    void setUp() {
        store = new MemoryStore(state(6, 4));
        service = service(new StaffPlayerIdentity(PLAYER, "FixturePlayer", true));
    }

    @Test
    @DisplayName("[Phase 9F-C1] Manage Player and Set/Reset reviews are permission-filtered and first-click safe")
    void rendersPermissionAwareManagementAndReviewScreens() {
        PermissionSubject owner = staff(STAFF, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW,
                PhaseSixPermissions.PLAYER_PRESTIGE_SET, PhaseSixPermissions.PLAYER_PRESTIGE_RESET);

        GuiSessionView overview = overview(service, owner);
        assertEquals(GuiItemIcon.REFRESH, itemAt(overview, 0).icon());
        assertEquals(GuiItemIcon.PROGRESS, itemAt(overview, 4).icon());
        assertEquals(GuiItemIcon.PLAYER_INFORMATION, itemAt(overview, 8).icon());
        assertEquals(GuiItemIcon.BALANCE, itemAt(overview, 10).icon());
        assertEquals(GuiItemIcon.REQUIREMENTS, itemAt(overview, 13).icon());
        assertEquals(GuiItemIcon.REWARD, itemAt(overview, 16).icon());
        assertEquals(18, itemFor(overview, GuiActionKind.STAFF_BACK_PLAYER_LIST).slot());
        assertEquals(20, itemFor(overview, GuiActionKind.STAFF_VIEW_PRESTIGE_PREVIEW).slot());
        assertEquals(22, itemFor(overview, GuiActionKind.STAFF_MANAGE_PLAYER).slot());
        assertEquals(GuiItemIcon.ADMINISTRATION,
                itemFor(overview, GuiActionKind.STAFF_MANAGE_PLAYER).icon());
        assertEquals(24, itemFor(overview, GuiActionKind.STAFF_VIEW_PLAYER_HISTORY).slot());
        assertEquals(26, itemFor(overview, GuiActionKind.STAFF_CLOSE).slot());
        assertEquals(1, overview.items().stream().filter(item ->
                item.title().key().equals("gui.item.staff.player.info.title")).count());
        assertTrue(overview.items().stream().noneMatch(item ->
                item.title().key().equals("gui.action.staff.copy_uuid")));
        GuiSessionView management = click(service, owner, overview, GuiActionKind.STAFF_MANAGE_PLAYER);

        assertEquals(GuiScreenKind.STAFF_PLAYER_MANAGEMENT_ACTIONS, management.screen());
        assertEquals("gui.title.staff.manage_player", management.title().key());
        assertTrue(management.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_SET_PRESTIGE));
        assertTrue(management.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET));
        assertEquals(GuiScreenKind.STAFF_PLAYER_OVERVIEW,
                click(service, owner, management, GuiActionKind.STAFF_BACK_PLAYER_OVERVIEW).screen());

        GuiSessionView resetManagement = click(service, owner, overview(service, owner),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        GuiSessionView resetReview = click(service, owner, resetManagement,
                GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET);
        assertReview(resetReview, ManualPrestigeAdjustmentKind.RESET, 6, 0);

        PermissionSubject reader = staff(OTHER_STAFF, PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW);
        assertTrue(overview(service, reader).actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_MANAGE_PLAYER));

        PermissionSubject setter = staff(UUID.randomUUID(), PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW, PhaseSixPermissions.PLAYER_PRESTIGE_SET);
        GuiSessionView setManagement = click(service, setter, overview(service, setter),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        assertTrue(setManagement.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_SET_PRESTIGE));
        assertTrue(setManagement.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET));
        GuiSessionView input = click(service, setter, setManagement, GuiActionKind.STAFF_SET_PRESTIGE);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SET_INPUT, input.screen());
        assertEquals("6", input.textInput().orElseThrow().initialValue());
        assertTrue(input.actions().stream().noneMatch(action -> action.prestigeAdjustment().isPresent()));
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_SET);
        assertFalse(service.acceptsNumericInput(setter, input.sessionId(), submit.actionId(), "6"));
        assertTrue(service.acceptsNumericInput(setter, input.sessionId(), submit.actionId(), "20"));
        assertFalse(service.acceptsNumericInput(setter, input.sessionId(), submit.actionId(), "0"));
        assertFalse(service.acceptsNumericInput(setter, input.sessionId(), submit.actionId(), "21"));
        GuiSessionView largeTargetReview = service.submitNumericInput(setter, input.sessionId(),
                submit.actionId(), "20").toCompletableFuture().join().nextView().orElseThrow();
        assertReview(largeTargetReview, ManualPrestigeAdjustmentKind.SET, 6, 20);

        PermissionSubject resetter = staff(UUID.randomUUID(), PhaseSixPermissions.ADMIN_GUI,
                PhaseSixPermissions.PLAYER_VIEW, PhaseSixPermissions.PLAYER_PRESTIGE_RESET);
        GuiSessionView resetOnly = click(service, resetter, overview(service, resetter),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        assertTrue(resetOnly.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_SET_PRESTIGE));
        assertTrue(resetOnly.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET));
    }

    @Test
    @DisplayName("[Phase 9F-D] Set Prestige typed input rejects non-canonical and no-op values without consuming")
    void setPrestigeTypedInputValidatesBeforeReviewWithoutPagination() {
        PermissionSubject owner = owner(STAFF);
        GuiSessionView management = click(service, owner, overview(service, owner),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        GuiSessionView input = click(service, owner, management, GuiActionKind.STAFF_SET_PRESTIGE);
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_SET);

        for (String invalid : List.of("0", "-1", "1.5", "1e2", "9223372036854775808", "6", "21")) {
            assertFalse(service.acceptsNumericInput(owner, input.sessionId(), submit.actionId(), invalid), invalid);
        }
        PlayerGuiInteractionResult invalid = service.submitNumericInput(owner, input.sessionId(),
                submit.actionId(), "6").toCompletableFuture().join();
        assertEquals("gui.staff.prestige_input.invalid", invalid.messages().getFirst().key());
        assertTrue(invalid.nextView().isEmpty());

        assertTrue(service.acceptsNumericInput(owner, input.sessionId(), submit.actionId(), "8"));
        GuiSessionView review = service.submitNumericInput(owner, input.sessionId(), submit.actionId(), "8")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertReview(review, ManualPrestigeAdjustmentKind.SET, 6, 8);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SET_INPUT,
                click(service, owner, review, GuiActionKind.STAFF_SET_PRESTIGE).screen());
    }

    @Test
    @DisplayName("[Phase 9F-D] Reset at baseline remains visible and returns a concise no-op without review or write")
    void resetAtBaselineIsFriendlyNoOpWithoutReviewOrWrite() {
        store.state.set(state(0, 9));
        PermissionSubject owner = owner(STAFF);
        GuiSessionView management = click(service, owner, overview(service, owner),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        GuiDisplayItem reset = itemFor(management, GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET);

        assertEquals(15, reset.slot());
        assertEquals("gui.action.staff.reset_prestige", reset.title().key());
        assertEquals(List.of("gui.item.staff.reset_prestige.already_baseline"),
                reset.lore().stream().map(MessageReference::key).toList());

        PlayerGuiInteractionResult result = clickResult(service, owner, management,
                GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET);

        GuiSessionView refreshed = result.nextView().orElseThrow();
        assertEquals(GuiScreenKind.STAFF_PLAYER_MANAGEMENT_ACTIONS, refreshed.screen());
        assertEquals("gui.staff.prestige_already_baseline", result.messages().getFirst().key());
        assertEquals("FixturePlayer", result.messages().getFirst().argument("player").orElseThrow());
        assertTrue(refreshed.actions().stream().noneMatch(action ->
                action.kind() == GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT));
        assertTrue(store.adjustments.isEmpty());
        assertEquals(0, store.state.get().currentPrestige());
        assertEquals(0, store.state.get().lifetimePrestige());
        assertEquals(9, store.state.get().stateRevision());

        GuiSessionView input = click(service, owner, refreshed, GuiActionKind.STAFF_SET_PRESTIGE);
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_SET_INPUT, input.screen());
        assertEquals("0", input.textInput().orElseThrow().initialValue());
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_SET);
        GuiSessionView setReview = service.submitNumericInput(owner, input.sessionId(), submit.actionId(), "20")
                .toCompletableFuture().join().nextView().orElseThrow();
        assertReview(setReview, ManualPrestigeAdjustmentKind.SET, 0, 20);
        assertTrue(store.adjustments.isEmpty());
    }

    @Test
    @DisplayName("[Phase 9F-C1] Set commits one mirrored numeric state and one server-owned authority")
    void setPrestigeCommitsExactlyOnceAndRejectsReplay() {
        PermissionSubject owner = owner(STAFF);
        GuiSessionView review = setReview(service, owner, 8);
        assertReview(review, ManualPrestigeAdjustmentKind.SET, 6, 8);
        GuiAction confirm = action(review, GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT);

        PlayerGuiInteractionResult result = service.click(owner, review.sessionId(), confirm.actionId())
                .toCompletableFuture().join();

        assertEquals(8, store.state.get().currentPrestige());
        assertEquals(8, store.state.get().lifetimePrestige());
        assertEquals(1, store.adjustments.size());
        assertEquals(ManualPrestigeAdjustmentKind.SET, store.adjustments.getFirst().kind());
        assertEquals(GuiScreenKind.STAFF_PLAYER_MANAGEMENT_ACTIONS,
                result.nextView().orElseThrow().screen());
        assertEquals("gui.staff.prestige_adjusted", result.messages().getFirst().key());
        AdministrationException replay = assertThrows(AdministrationException.class,
                () -> service.click(owner, review.sessionId(), confirm.actionId()));
        assertEquals("gui.session.expired", replay.code());
        assertEquals(1, store.adjustments.size());
    }

    @Test
    @DisplayName("[Phase 9F-C1] Stale, forged, cross-staff, logout, and identity-loss authority fails closed")
    void rejectsInvalidMutationAuthorityWithoutWriting() {
        PermissionSubject owner = owner(STAFF);
        GuiSessionView management = click(service, owner, overview(service, owner),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        GuiSessionView staleInput = click(service, owner, management, GuiActionKind.STAFF_SET_PRESTIGE);
        GuiAction inputSubmit = action(staleInput, GuiActionKind.STAFF_REVIEW_PRESTIGE_SET);
        store.state.set(state(7, 5));
        assertThrows(CompletionException.class, () -> service.submitNumericInput(owner,
                staleInput.sessionId(), inputSubmit.actionId(), "8").toCompletableFuture().join());
        assertTrue(store.adjustments.isEmpty());

        store.state.set(state(6, 4));
        GuiSessionView staleState = setReview(service, owner, 8);
        store.state.set(state(7, 5));
        assertThrows(CompletionException.class, () -> clickResult(service, owner, staleState,
                GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT));
        assertTrue(store.adjustments.isEmpty());

        store.state.set(state(6, 4));
        GuiSessionView staleConfiguration = setReview(service, owner, 8);
        activeRevision.set(Optional.of(new ConfigRevisionId("phase9f-c1-replaced")));
        AdministrationException stale = assertThrows(AdministrationException.class,
                () -> clickResult(service, owner, staleConfiguration,
                        GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT));
        assertEquals("gui.action.stale", stale.code());
        assertTrue(store.adjustments.isEmpty());
        activeRevision.set(Optional.of(REVISION));

        GuiSessionView crossStaff = setReview(service, owner, 8);
        AdministrationException actorMismatch = assertThrows(AdministrationException.class,
                () -> service.click(owner(OTHER_STAFF), crossStaff.sessionId(),
                        action(crossStaff, GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT).actionId()));
        assertEquals("gui.session.actor_mismatch", actorMismatch.code());

        GuiSessionView forged = click(service, owner, overview(service, owner),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        AdministrationException forgedAction = assertThrows(AdministrationException.class,
                () -> service.click(owner, forged.sessionId(), UUID.randomUUID()));
        assertEquals("gui.action.forged", forgedAction.code());

        GuiSessionView loggedOut = setReview(service, owner, 8);
        service.invalidateStaff(STAFF);
        AdministrationException expired = assertThrows(AdministrationException.class,
                () -> service.click(owner, loggedOut.sessionId(),
                        action(loggedOut, GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT).actionId()));
        assertEquals("gui.session.expired", expired.code());
        assertTrue(store.adjustments.isEmpty());

        StaffPlayerIdentity online = new StaffPlayerIdentity(PLAYER, "FixturePlayer", true);
        AtomicReference<List<StaffPlayerIdentity>> known = new AtomicReference<>(List.of(online));
        StaffGuiService disappearing = service(directory(known));
        GuiSessionView missingIdentity = setReview(disappearing, owner, 8);
        known.set(List.of());
        AdministrationException missing = assertThrows(AdministrationException.class, () ->
                clickResult(disappearing, owner, missingIdentity,
                        GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT));
        assertEquals("gui.staff.player_unknown", missing.code());
        assertTrue(store.adjustments.isEmpty());
    }

    @Test
    @DisplayName("[Phase 9F-C1] Known offline players use the same provider-free administrative service")
    void supportsKnownOfflinePlayerWithoutProviderMutation() {
        service = service(new StaffPlayerIdentity(PLAYER, "FixturePlayer", false));
        PermissionSubject owner = owner(STAFF);

        GuiSessionView results = service.findPlayers(owner, "FixturePlayer");
        GuiSessionView overview = click(service, owner, results, GuiActionKind.STAFF_SELECT_SEARCH_RESULT);
        GuiSessionView management = click(service, owner, overview, GuiActionKind.STAFF_MANAGE_PLAYER);
        GuiSessionView resetReview = click(service, owner, management,
                GuiActionKind.STAFF_REVIEW_PRESTIGE_RESET);

        assertReview(resetReview, ManualPrestigeAdjustmentKind.RESET, 6, 0);
        assertTrue(store.adjustments.isEmpty());
    }

    @Test
    @DisplayName("[Phase 9F-C1] Admin Set and Reset history never presents fictional costs or rewards")
    void rendersAdministrativeHistoryTruthfully() {
        StaffHistorySource.Entry set = adminHistory(StaffHistorySource.Kind.ADMIN_SET, 3, 8,
                Optional.of("$5"), Optional.of("$1"));
        StaffHistorySource.Entry reset = adminHistory(StaffHistorySource.Kind.ADMIN_RESET, 8, 0,
                Optional.of("$5"), Optional.of("$1"));
        StaffHistorySource history = history(List.of(set, reset));
        service = service(new StaffPlayerIdentity(PLAYER, "FixturePlayer", true), history);
        PermissionSubject owner = owner(STAFF);

        GuiSessionView global = click(service, owner, service.open(owner), GuiActionKind.STAFF_VIEW_HISTORY);
        assertEquals(2, global.items().stream().filter(item ->
                item.title().key().equals("gui.item.staff.history.admin_entry.title")).count());
        assertTrue(global.items().stream().flatMap(item -> item.lore().stream())
                .noneMatch(StaffPrestigeMutationGuiTest::financialLine));

        GuiSessionView overview = overview(service, owner);
        GuiSessionView player = click(service, owner, overview, GuiActionKind.STAFF_VIEW_PLAYER_HISTORY);
        assertEquals(2, player.items().stream().filter(item ->
                item.title().key().equals("gui.item.staff.player_history.admin_entry.title")).count());
        assertTrue(player.items().stream().flatMap(item -> item.lore().stream())
                .noneMatch(StaffPrestigeMutationGuiTest::financialLine));
        assertTrue(player.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.history.actor")));
    }

    private StaffGuiService service(StaffPlayerIdentity identity) {
        return service(identity, history(List.of()));
    }

    private StaffGuiService service(StaffPlayerIdentity identity, StaffHistorySource history) {
        return service(directory(new AtomicReference<>(List.of(identity))), history);
    }

    private StaffGuiService service(StaffPlayerDirectory directory) {
        return service(directory, history(List.of()));
    }

    private StaffGuiService service(StaffPlayerDirectory directory, StaffHistorySource history) {
        PlayerProgressViewService progress = mock(PlayerProgressViewService.class);
        when(progress.inspect(any(), eq(PLAYER))).thenReturn(CompletableFuture.completedFuture(
                new PlayerProgressView(PLAYER, preview())));
        GuiSessionService sessions = new GuiSessionService(activeRevision::get,
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), CLOCK);
        ManualPrestigeAdministrationService administration = new ManualPrestigeAdministrationService(
                store, () -> activeRevision.get().orElseThrow(), Runnable::run, ignored -> Optional.empty(),
                () -> new ManualPrestigeAdjustmentPolicy(0, OptionalLong.of(20)));
        return new StaffGuiService(sessions, progress, directory, activeRevision::get,
                history, healthyStatus(), administration);
    }

    private static GuiSessionView overview(StaffGuiService route, PermissionSubject subject) {
        GuiSessionView dashboard = route.open(subject);
        GuiSessionView management = click(route, subject, dashboard, GuiActionKind.STAFF_OPEN_PLAYERS);
        GuiSessionView players = click(route, subject, management, GuiActionKind.STAFF_OPEN_ONLINE_PLAYERS);
        return click(route, subject, players, GuiActionKind.STAFF_SELECT_PLAYER);
    }

    private static GuiSessionView setReview(
            StaffGuiService route,
            PermissionSubject subject,
            long target) {
        GuiSessionView management = click(route, subject, overview(route, subject),
                GuiActionKind.STAFF_MANAGE_PLAYER);
        GuiSessionView input = click(route, subject, management, GuiActionKind.STAFF_SET_PRESTIGE);
        GuiAction submit = action(input, GuiActionKind.STAFF_REVIEW_PRESTIGE_SET);
        return route.submitNumericInput(subject, input.sessionId(), submit.actionId(), String.valueOf(target))
                .toCompletableFuture().join().nextView().orElseThrow();
    }

    private static void assertReview(
            GuiSessionView view,
            ManualPrestigeAdjustmentKind kind,
            long current,
            long target) {
        assertEquals(GuiScreenKind.STAFF_PRESTIGE_ADJUSTMENT_REVIEW, view.screen());
        GuiAction confirm = action(view, GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT);
        assertTrue(confirm.mutating());
        assertEquals(kind, confirm.prestigeAdjustment().orElseThrow().kind());
        assertEquals(current, confirm.prestigeAdjustment().orElseThrow().currentPrestige());
        assertEquals(target, confirm.prestigeAdjustment().orElseThrow().targetPrestige());
        assertEquals(PLAYER, confirm.targetPlayer().orElseThrow());
        assertEquals(kind == ManualPrestigeAdjustmentKind.SET
                        ? "gui.title.staff.review_set_prestige"
                        : "gui.title.staff.review_reset_prestige",
                view.title().key());
        assertEquals("FixturePlayer", view.title().argument("player").orElseThrow());
        GuiDisplayItem confirmation = itemFor(view, GuiActionKind.STAFF_CONFIRM_PRESTIGE_ADJUSTMENT);
        assertTrue(confirmation.lore().isEmpty());
        if (kind == ManualPrestigeAdjustmentKind.RESET) {
            assertEquals(GuiItemIcon.BLOCKED, confirmation.icon());
            assertEquals("gui.action.staff.reset_prestige_adjustment", confirmation.title().key());
        } else {
            assertEquals(GuiItemIcon.CONFIRM, confirmation.icon());
            assertEquals("gui.action.staff.confirm_prestige_adjustment", confirmation.title().key());
        }
    }

    private static GuiSessionView click(
            StaffGuiService route,
            PermissionSubject subject,
            GuiSessionView view,
            GuiActionKind kind) {
        return click(route, subject, view, action(view, kind));
    }

    private static GuiSessionView click(
            StaffGuiService route,
            PermissionSubject subject,
            GuiSessionView view,
            GuiAction action) {
        return route.click(subject, view.sessionId(), action.actionId()).toCompletableFuture().join()
                .nextView().orElseThrow();
    }

    private static PlayerGuiInteractionResult clickResult(
            StaffGuiService route,
            PermissionSubject subject,
            GuiSessionView view,
            GuiActionKind kind) {
        return route.click(subject, view.sessionId(), action(view, kind).actionId()).toCompletableFuture().join();
    }

    private static GuiAction action(GuiSessionView view, GuiActionKind kind) {
        return view.actions().stream().filter(action -> action.kind() == kind).findFirst().orElseThrow();
    }

    private static GuiDisplayItem itemFor(GuiSessionView view, GuiActionKind kind) {
        UUID actionId = action(view, kind).actionId();
        return view.items().stream().filter(item -> item.actionId().filter(actionId::equals).isPresent())
                .findFirst().orElseThrow();
    }

    private static GuiDisplayItem itemAt(GuiSessionView view, int slot) {
        return view.items().stream().filter(item -> item.slot() == slot).findFirst().orElseThrow();
    }

    private static PermissionSubject owner(UUID actorId) {
        return staff(actorId, PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.PLAYER_VIEW,
                PhaseSixPermissions.PLAYER_PRESTIGE_SET, PhaseSixPermissions.PLAYER_PRESTIGE_RESET);
    }

    private static PermissionSubject staff(UUID actorId, String... permissions) {
        return new PermissionSubject(new Actor("player", Optional.of(actorId), "Owner"), Set.of(permissions));
    }

    private static StaffPlayerDirectory directory(AtomicReference<List<StaffPlayerIdentity>> known) {
        return new StaffPlayerDirectory() {
            @Override
            public List<StaffPlayerIdentity> onlinePlayers() {
                return known.get().stream().filter(StaffPlayerIdentity::online).toList();
            }

            @Override
            public List<StaffPlayerIdentity> knownPlayers() {
                return known.get();
            }
        };
    }

    private static StaffHistorySource history(List<StaffHistorySource.Entry> entries) {
        return new StaffHistorySource() {
            @Override
            public java.util.concurrent.CompletionStage<Page> recent(int offset, int limit) {
                return CompletableFuture.completedFuture(page(entries, offset, limit));
            }

            @Override
            public java.util.concurrent.CompletionStage<Page> forPlayer(UUID playerId, int offset, int limit) {
                return CompletableFuture.completedFuture(page(entries, offset, limit));
            }
        };
    }

    private static StaffHistorySource.Page page(
            List<StaffHistorySource.Entry> entries,
            int offset,
            int limit) {
        int last = Math.min(entries.size(), offset + limit);
        List<StaffHistorySource.Entry> values = offset >= entries.size() ? List.of() : entries.subList(offset, last);
        return new StaffHistorySource.Page(values, offset > 0, last < entries.size(), entries.size());
    }

    private static StaffHistorySource.Entry adminHistory(
            StaffHistorySource.Kind kind,
            long before,
            long after,
            Optional<String> cost,
            Optional<String> reward) {
        return new StaffHistorySource.Entry(UUID.randomUUID(), "FixturePlayer", kind, Optional.of(STAFF),
                Optional.of("Owner"), before, after, StaffHistorySource.Outcome.COMPLETED,
                cost.isPresent(), reward.isPresent(), Optional.empty(), Optional.empty(), cost, reward, NOW);
    }

    private static boolean financialLine(MessageReference line) {
        return Set.of("gui.item.staff.history.balance", "gui.item.staff.history.cost",
                "gui.item.staff.history.reward", "gui.item.staff.history.transaction.cost_reward",
                "gui.item.staff.history.transaction.cost", "gui.item.staff.history.transaction.reward")
                .contains(line.key());
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
            public java.util.concurrent.CompletionStage<Snapshot> inspect() {
                return CompletableFuture.completedFuture(new Snapshot(summary, List.of()));
            }
        };
    }

    private static OperationPreview preview() {
        return new OperationPreview(OperationKind.PRESTIGE, PLAYER, false, "Prestige 6 → 7",
                Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), REVISION,
                Map.of(), List.of(), List.of(), List.of());
    }

    private static PlayerPrestigeState state(long prestige, long revision) {
        return new PlayerPrestigeState(PLAYER, prestige, prestige, revision, REVISION,
                new ScopeId("numeric-p" + prestige), Optional.empty(), NOW, NOW);
    }

    private static final class MemoryStore implements PrestigeAdministrationStore {
        private final AtomicReference<PlayerPrestigeState> state;
        private final List<ManualPrestigeAdjustment> adjustments = new ArrayList<>();

        private MemoryStore(PlayerPrestigeState state) {
            this.state = new AtomicReference<>(state);
        }

        @Override
        public Optional<PlayerPrestigeState> find(UUID playerId) {
            return state.get().playerId().equals(playerId) ? Optional.of(state.get()) : Optional.empty();
        }

        @Override
        public synchronized PlayerPrestigeState adjust(ManualPrestigeAdjustment adjustment) {
            PlayerPrestigeState current = state.get();
            if (!current.playerId().equals(adjustment.playerId())
                    || current.stateRevision() != adjustment.expectedStateRevision()
                    || current.currentPrestige() == adjustment.currentPrestige()) {
                throw new IllegalStateException("stale administrative Prestige adjustment");
            }
            PlayerPrestigeState updated = new PlayerPrestigeState(current.playerId(),
                    adjustment.currentPrestige(), adjustment.lifetimePrestige(), current.stateRevision() + 1,
                    adjustment.configRevision(), current.prestigeScope(), current.lastPrestigedAt(),
                    current.createdAt(), NOW);
            state.set(updated);
            adjustments.add(adjustment);
            return updated;
        }
    }
}
