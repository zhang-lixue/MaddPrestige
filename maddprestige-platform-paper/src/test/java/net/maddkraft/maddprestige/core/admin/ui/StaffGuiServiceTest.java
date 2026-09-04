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
        assertEquals("gui.action.staff.players", itemAt(allowed, 10).title().key());
        assertEquals("gui.action.staff.configuration", itemAt(allowed, 12).title().key());
        assertEquals("gui.action.staff.history", itemAt(allowed, 14).title().key());
        assertEquals("gui.action.staff.system_status.healthy", itemAt(allowed, 16).title().key());
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
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.argument("revision").filter(REVISION.value()::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.usable")));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.range")
                        && line.argument("value").filter("P1 – P50"::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.requirements")
                        && line.argument("count").filter("2"::equals).isPresent()));
        assertTrue(configuration.items().stream().flatMap(item -> item.lore().stream())
                .anyMatch(line -> line.key().equals("gui.item.staff.configuration.costs")
                        && line.argument("count").filter("1"::equals).isPresent()));
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
        assertEquals("gui.item.staff.player.offline", itemAt(preview, 21).lore().getFirst().key());
        assertEquals(GuiItemIcon.BLOCKED, itemAt(preview, 22).icon());
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
        assertEquals(GuiItemIcon.BLOCKED, itemAt(blocked, 22).icon());
        assertEquals("gui.item.staff.prestige_preview.not_ready", itemAt(blocked, 22).title().key());
        assertTrue(itemAt(blocked, 22).actionId().isEmpty());
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
        assertEquals(GuiItemIcon.PROGRESS, itemAt(blocked, 20).icon());
        assertEquals(Optional.of(refresh.actionId()), itemAt(blocked, 20).actionId());
        assertTrue(blocked.actions().stream().anyMatch(action ->
                action.kind() == GuiActionKind.STAFF_VIEW_PLAYER_HISTORY));
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
        assertEquals(GuiItemIcon.READY, itemAt(ready, 22).icon());
        assertEquals("gui.item.staff.prestige_preview.ready", itemAt(ready, 22).title().key());
        assertTrue(itemAt(ready, 22).actionId().isEmpty());
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
        assertEquals("gui.item.staff.player.offline", itemAt(offline, 21).lore().getFirst().key());
        assertEquals(Optional.of(PLAYER), itemAt(offline, 21).profilePlayerId());

        known.set(List.of(new StaffPlayerIdentity(PLAYER, "tmydwc", true)));
        GuiSessionView online = click(route, subject, offline,
                GuiActionKind.STAFF_REFRESH_PRESTIGE_PREVIEW);
        assertEquals("gui.item.staff.player.online", itemAt(online, 21).lore().getFirst().key());
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
                    STAFF_CONFIRM_PRESTIGE_ADJUSTMENT -> true;
            default -> false;
        };
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
