package net.maddkraft.maddprestige.platform.paper.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.OperationKind;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.admin.command.CommandResponse;
import net.maddkraft.maddprestige.core.admin.command.PhaseSixCommandService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressView;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.ui.GuiActionKind;
import net.maddkraft.maddprestige.core.admin.ui.GuiAudience;
import net.maddkraft.maddprestige.core.admin.ui.GuiConfigurationAuthority;
import net.maddkraft.maddprestige.core.admin.ui.GuiDisplayItem;
import net.maddkraft.maddprestige.core.admin.ui.GuiItemIcon;
import net.maddkraft.maddprestige.core.admin.ui.GuiScreenKind;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiInteractionResult;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiService;
import net.maddkraft.maddprestige.core.admin.ui.StaffGuiService;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistorySource;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerIdentity;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class PaperGuiInventoryGuardTest {
    private final PaperGuiInventoryGuard guard = new PaperGuiInventoryGuard();

    @Test
    @DisplayName("[A56][A69] Only ordinary clicks on server-owned top slots may dispatch")
    void permitsOnlyNarrowServerOwnedClicks() {
        var allowed = guard.click(ClickType.LEFT, InventoryAction.PICKUP_ALL, 2, 9);
        assertTrue(allowed.cancelEvent());
        assertTrue(allowed.dispatchServerAction());

        assertBlocked(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, 2);
        assertBlocked(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 2);
        assertBlocked(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, 2);
        assertBlocked(ClickType.DROP, InventoryAction.DROP_ONE_SLOT, 2);
        assertBlocked(ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR, 2);
        assertBlocked(ClickType.CREATIVE, InventoryAction.CLONE_STACK, 2);
        assertBlocked(ClickType.LEFT, InventoryAction.PLACE_ALL, 2);
        assertBlocked(ClickType.LEFT, InventoryAction.PICKUP_ALL, 12);
    }

    @Test
    @DisplayName("[Phase6-security] Every drag is cancelled while an authoritative GUI is open")
    void blocksDragInjectionAndTransfer() {
        var topDrag = guard.drag(Set.of(1, 2), 9);
        var bottomDrag = guard.drag(Set.of(10, 11), 9);

        assertTrue(topDrag.cancelEvent());
        assertFalse(topDrag.dispatchServerAction());
        assertTrue(bottomDrag.cancelEvent());
        assertFalse(bottomDrag.dispatchServerAction());
    }

    @Test
    @DisplayName("[Phase 9F-A] Semantic GUI icons map only to inert server-rendered inventory materials")
    void mapsServerOwnedDisplayMaterials() {
        assertEquals(Material.CLOCK, PaperGuiInventory.material(GuiItemIcon.PROGRESS));
        assertEquals(Material.COMPASS, PaperGuiInventory.material(GuiItemIcon.REFRESH));
        assertFalse(PaperGuiInventory.material(GuiItemIcon.PROGRESS)
                .equals(PaperGuiInventory.material(GuiItemIcon.REFRESH)));
        assertEquals(Material.NETHER_STAR, PaperGuiInventory.material(GuiItemIcon.PRESTIGE));
        assertEquals(Material.WRITABLE_BOOK, PaperGuiInventory.material(GuiItemIcon.REQUIREMENTS));
        assertEquals(Material.GOLD_INGOT, PaperGuiInventory.material(GuiItemIcon.BALANCE));
        assertEquals(Material.RED_CONCRETE, PaperGuiInventory.material(GuiItemIcon.BLOCKED));
        assertEquals(Material.LIME_CONCRETE, PaperGuiInventory.material(GuiItemIcon.CONFIRM));
        assertEquals(Material.COMPASS, PaperGuiInventory.material(GuiItemIcon.STAFF));
        assertEquals(Material.PLAYER_HEAD, PaperGuiInventory.material(GuiItemIcon.PLAYERS));
        assertEquals(Material.NAME_TAG, PaperGuiInventory.material(GuiItemIcon.PLAYER_INFORMATION));
        assertEquals(Material.KNOWLEDGE_BOOK, PaperGuiInventory.material(GuiItemIcon.CONFIGURATION));
        assertEquals(Material.BOOK, PaperGuiInventory.material(GuiItemIcon.HISTORY));
        assertEquals(Material.COMPARATOR, PaperGuiInventory.material(GuiItemIcon.SYSTEM_STATUS));
        assertEquals(Material.BARRIER, PaperGuiInventory.material(GuiItemIcon.CLOSE));
        assertEquals(Material.PURPLE_STAINED_GLASS_PANE,
                PaperGuiInventory.material(GuiItemIcon.BORDER_PURPLE));
        assertEquals(Material.CYAN_STAINED_GLASS_PANE,
                PaperGuiInventory.material(GuiItemIcon.BORDER_AQUA));
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff inventory actions route only to the Staff GUI service")
    void routesStaffInventoryToStaffAuthority(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        PlayerGuiService playerGui = mock(PlayerGuiService.class);
        StaffGuiService staffGui = mock(StaffGuiService.class);
        Player player = mock(Player.class);
        preparePlayer(player);
        when(player.isOnline()).thenReturn(true);
        PaperGuiInventory holder = mock(PaperGuiInventory.class);
        UUID sessionId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(holder.audience()).thenReturn(GuiAudience.STAFF);
        when(holder.sessionId()).thenReturn(sessionId);
        when(holder.actionAt(22)).thenReturn(Optional.of(actionId));
        when(staffGui.click(any(), org.mockito.ArgumentMatchers.eq(sessionId),
                org.mockito.ArgumentMatchers.eq(actionId)))
                .thenReturn(CompletableFuture.completedFuture(PlayerGuiInteractionResult.closed()));
        DeferredScheduler deferred = new DeferredScheduler();
        InventoryClickEvent event = click(holder, player);
        PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(
                playerGui, staffGui, guard, deferred, messages);

        controller.onClick(event);
        deferred.runNext();

        verify(staffGui).click(any(), org.mockito.ArgumentMatchers.eq(sessionId),
                org.mockito.ArgumentMatchers.eq(actionId));
        verify(playerGui, never()).click(any(), any(), any());
        verify(player).closeInventory();
    }

    @Test
    @DisplayName("[Phase 9F-B] Copy UUID uses a safe client clipboard action and refreshed Staff view")
    void copiesSelectedPlayerUuidThroughClickableFallback(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        PlayerGuiService playerGui = mock(PlayerGuiService.class);
        StaffGuiService staffGui = mock(StaffGuiService.class);
        Player player = mock(Player.class);
        preparePlayer(player);
        when(player.isOnline()).thenReturn(true);
        ArrayList<Component> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0)))
                .when(player).sendMessage(any(Component.class));
        PaperGuiInventory holder = mock(PaperGuiInventory.class);
        UUID sessionId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID selectedPlayer = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
        when(holder.audience()).thenReturn(GuiAudience.STAFF);
        when(holder.sessionId()).thenReturn(sessionId);
        when(holder.actionAt(8)).thenReturn(Optional.of(actionId));
        GuiSessionView destination = staffDashboardView();
        when(staffGui.click(any(), org.mockito.ArgumentMatchers.eq(sessionId),
                org.mockito.ArgumentMatchers.eq(actionId))).thenReturn(CompletableFuture.completedFuture(
                        PlayerGuiInteractionResult.navigate(destination,
                                MessageReference.of("gui.staff.player.uuid.copy",
                                        "uuid", selectedPlayer))));
        DeferredScheduler deferred = new DeferredScheduler();
        Inventory rendered = mock(Inventory.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = paperItems()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenReturn(rendered);
            PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(
                    playerGui, staffGui, guard, deferred, messages);

            controller.onClick(click(holder, player, 8));
            deferred.runNext();

            assertEquals(1, sent.size());
            ClickEvent copy = sent.getFirst().clickEvent();
            assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, copy.action());
            assertEquals(selectedPlayer.toString(), copy.value());
            assertTrue(plain(sent.getFirst()).contains(selectedPlayer.toString()));
            verify(player).openInventory(rendered);
            verify(playerGui, never()).click(any(), any(), any());
            assertFalse(items.constructed().isEmpty());
        }
    }

    @Test
    @DisplayName("[Phase 9F-B correction] First Staff clicks open Player Management and Player Overview")
    void firstStaffClicksOpenPlayerManagementAndOverview(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        UUID staffId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID playerId = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
        ConfigRevisionId revision = new ConfigRevisionId("phase9f-b-first-click");
        assertDoesNotThrow(() -> messages.render(
                MessageReference.of("gui.item.staff.player.uuid", "uuid", playerId)));
        PlayerProgressViewService progress = mock(PlayerProgressViewService.class);
        when(progress.inspect(any(), org.mockito.ArgumentMatchers.eq(playerId)))
                .thenReturn(CompletableFuture.completedFuture(
                        new PlayerProgressView(playerId, staffPreview(playerId, revision))));
        GuiSessionService sessions = new GuiSessionService(() -> Optional.of(revision),
                (subject, action) -> CompletableFuture.completedFuture(MessageReference.of("unused")),
                mock(GuiConfigurationAuthority.class), Duration.ofMinutes(5), Clock.systemUTC());
        StaffHistorySource staffHistory = new StaffHistorySource() {
            @Override
            public java.util.concurrent.CompletionStage<Page> recent(int offset, int limit) {
                return page(offset);
            }

            @Override
            public java.util.concurrent.CompletionStage<Page> forPlayer(
                    UUID selectedPlayerId,
                    int offset,
                    int limit) {
                assertEquals(playerId, selectedPlayerId);
                return page(offset);
            }

            private java.util.concurrent.CompletionStage<Page> page(int offset) {
                return CompletableFuture.completedFuture(new Page(List.of(new Entry(
                        UUID.fromString("4be90c91-dd87-4210-bbeb-30d906d77a7a"),
                        "tmydwc", 5, 6, Outcome.COMPLETED, true, true,
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Instant.parse("2026-09-01T12:00:00Z"))), offset > 0, false));
            }
        };
        StaffGuiService staffGui = new StaffGuiService(sessions, progress,
                () -> List.of(new StaffPlayerIdentity(playerId, "tmydwc")), () -> Optional.of(revision),
                staffHistory,
                healthyStaffStatus());
        PlayerGuiService playerGui = mock(PlayerGuiService.class);
        Player staff = mock(Player.class);
        preparePlayer(staff);
        when(staff.getUniqueId()).thenReturn(staffId);
        when(staff.getName()).thenReturn("phase9f-staff");
        when(staff.isOnline()).thenReturn(true);
        ArrayList<Component> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0)))
                .when(staff).sendMessage(any(Component.class));
        DeferredScheduler deferred = new DeferredScheduler();
        ArrayList<PaperGuiInventory> holders = new ArrayList<>();
        ArrayList<Component> titles = new ArrayList<>();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = paperItems()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenAnswer(invocation -> {
                        PaperGuiInventory holder = invocation.getArgument(0);
                        Inventory inventory = mock(Inventory.class);
                        when(inventory.getHolder()).thenReturn(holder);
                        when(inventory.getSize()).thenReturn(27);
                        holders.add(holder);
                        titles.add(invocation.getArgument(2));
                        return inventory;
                    });
            PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(
                    playerGui, staffGui, guard, deferred, messages);
            var subject = PaperPermissionSubjects.from(staff);
            GuiSessionView dashboard = staffGui.open(subject);
            var dashboardAction = dashboard.actions().stream()
                    .filter(action -> action.kind() == GuiActionKind.STAFF_OPEN_PLAYERS)
                    .findFirst().orElseThrow();
            controller.open(staff, dashboard);

            controller.onClick(click(holders.get(0), staff, 12));
            deferred.runNext();

            assertEquals("Configuration", plain(titles.get(1)));
            AdministrationException consumed = assertThrows(AdministrationException.class,
                    () -> staffGui.click(subject, dashboard.sessionId(), dashboardAction.actionId()));
            assertEquals("gui.session.expired", consumed.code());

            controller.onClose(close(holders.get(0)));
            controller.onClick(click(holders.get(1), staff, 18));
            deferred.runNext();

            assertEquals("Staff Dashboard", plain(titles.get(2)));

            controller.onClose(close(holders.get(1)));
            controller.onClick(click(holders.get(2), staff, 14));
            deferred.runNext();

            assertEquals("History & Audit", plain(titles.get(3)));
            controller.onClose(close(holders.get(2)));
            controller.onClick(click(holders.get(3), staff, 18));
            deferred.runNext();

            assertEquals("Staff Dashboard", plain(titles.get(4)));
            controller.onClose(close(holders.get(3)));
            controller.onClick(click(holders.get(4), staff, 16));
            deferred.runNext();

            assertEquals("System Status", plain(titles.get(5)));
            controller.onClose(close(holders.get(4)));
            controller.onClick(click(holders.get(5), staff, 18));
            deferred.runNext();

            assertEquals("Staff Dashboard", plain(titles.get(6)));
            controller.onClose(close(holders.get(5)));
            controller.onClick(click(holders.get(6), staff, 10));
            deferred.runNext();

            assertEquals("Player Management", plain(titles.get(7)));
            assertTrue(holders.get(7).actionAt(11).isPresent());
            assertTrue(holders.get(7).actionAt(15).isPresent());
            controller.onClose(close(holders.get(6)));
            controller.onClick(click(holders.get(7), staff, 11));
            deferred.runNext();

            assertEquals("Online Players", plain(titles.get(8)));
            assertTrue(holders.get(8).actionAt(13).isPresent());
            controller.onClose(close(holders.get(7)));
            controller.onClick(click(holders.get(8), staff, 13));
            deferred.runNext();

            assertEquals("tmydwc Overview", plain(titles.get(9)));
            assertTrue(holders.get(9).actionAt(18).isPresent());
            assertTrue(holders.get(9).actionAt(26).isPresent());
            controller.onClose(close(holders.get(8)));
            controller.onClick(click(holders.get(9), staff, 13));
            deferred.runNext();

            assertEquals("tmydwc Requirements", plain(titles.get(10)));
            controller.onClose(close(holders.get(9)));
            assertTrue(holders.get(10).actionAt(18).isPresent());
            assertTrue(holders.get(10).actionAt(26).isPresent());
            controller.onClick(click(holders.get(10), staff, 18));
            deferred.runNext();

            assertEquals("tmydwc Overview", plain(titles.get(11)));
            controller.onClose(close(holders.get(10)));
            assertTrue(holders.get(11).actionAt(24).isPresent());
            controller.onClick(click(holders.get(11), staff, 24));
            deferred.runNext();

            assertEquals("tmydwc History", plain(titles.get(12)));
            assertTrue(holders.get(12).actionAt(18).isPresent());
            assertTrue(holders.get(12).actionAt(26).isPresent());
            controller.onClose(close(holders.get(11)));
            controller.onClick(click(holders.get(12), staff, 18));
            deferred.runNext();

            assertEquals("tmydwc Overview", plain(titles.get(13)));
            controller.onClose(close(holders.get(12)));
            assertTrue(holders.get(13).actionAt(20).isPresent());
            controller.onClick(click(holders.get(13), staff, 20));
            deferred.runNext();

            assertEquals("tmydwc Preview", plain(titles.get(14)));
            assertTrue(holders.get(14).actionAt(18).isPresent());
            assertTrue(holders.get(14).actionAt(20).isPresent());
            assertTrue(holders.get(14).actionAt(24).isPresent());
            assertTrue(holders.get(14).actionAt(26).isPresent());
            controller.onClose(close(holders.get(13)));
            controller.onClick(click(holders.get(14), staff, 20));
            deferred.runNext();

            assertEquals("tmydwc Preview", plain(titles.get(15)));
            controller.onClose(close(holders.get(14)));
            controller.onClick(click(holders.get(15), staff, 18));
            deferred.runNext();

            assertEquals("tmydwc Overview", plain(titles.get(16)));
            controller.onClose(close(holders.get(15)));
            controller.onClick(click(holders.get(16), staff, 26));
            deferred.runNext();

            verify(staff, times(17)).openInventory(any(Inventory.class));
            verify(staff).closeInventory();
            assertTrue(sent.isEmpty());
            assertFalse(items.constructed().isEmpty());
        }
    }

    @Test
    @DisplayName("[Phase 9F-B] Online player heads use live profiles and degrade to ordinary heads")
    void appliesLivePlayerProfileWithoutMakingItAuthority(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        UUID playerId = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
        Player player = mock(Player.class);
        com.destroystokyo.paper.profile.PlayerProfile profile =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        SkullMeta skull = mock(SkullMeta.class);
        Inventory inventory = mock(Inventory.class);
        GuiSessionView view = new GuiSessionView(UUID.randomUUID(), GuiAudience.STAFF,
                MessageReference.of("gui.title.staff.players"), List.of(), Instant.now().plusSeconds(300),
                GuiScreenKind.STAFF_PLAYER_SELECTION, 27, List.of(GuiDisplayItem.profiledDisplay(
                        13, GuiItemIcon.PLAYERS, MessageReference.of("gui.item.staff.player.title",
                                "player", "tmydwc"), List.of(), playerId)));
        when(player.isOnline()).thenReturn(true);
        when(player.getPlayerProfile()).thenReturn(profile)
                .thenThrow(new IllegalArgumentException("profile unavailable"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> ignored = mockConstruction(ItemStack.class, (item, context) ->
                        doAnswer(invocation -> {
                            Consumer<? super ItemMeta> editor = invocation.getArgument(0);
                            editor.accept(skull);
                            return true;
                        }).when(item).editMeta(any()))) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenReturn(inventory);

            assertDoesNotThrow(() -> new PaperGuiInventory(view, messages));
            assertDoesNotThrow(() -> new PaperGuiInventory(view, messages));
            verify(skull).setPlayerProfile(profile);
        }

        assertTrue(view.items().getFirst().actionId().isEmpty());
        assertEquals(Optional.of(playerId), view.items().getFirst().profilePlayerId());
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff identity and requirement cards retain concise semantic styling")
    void rendersStaffIdentityAndRequirementCardStyles(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        Component player = messages.render(MessageReference.of(
                "gui.item.staff.player.title", "player", "tmydwc"));
        Component online = messages.render(MessageReference.of("gui.item.staff.player.selection.online"));
        Component inspect = messages.render(MessageReference.of("gui.item.staff.player.selection.inspect"));
        Component info = messages.render(MessageReference.of("gui.item.staff.player.info.title"));
        Component summary = messages.render(MessageReference.of(
                "gui.item.staff.requirements.summary.title"));
        Component money = messages.render(MessageReference.of("gui.item.staff.requirement.money.title"));
        Component skill = messages.render(MessageReference.of(
                "gui.item.staff.requirement.total_skill_level.title"));
        Component value = messages.render(MessageReference.of(
                "gui.item.staff.requirement.value", "current", "$1", "target", "$8"));
        Component met = messages.render(MessageReference.of("gui.item.staff.requirement.met"));
        Component notMet = messages.render(MessageReference.of("gui.item.staff.requirement.not_met"));
        Component preview = messages.render(MessageReference.of("gui.action.staff.prestige_preview"));
        Component refresh = messages.render(MessageReference.of("gui.action.staff.refresh"));
        Component ready = messages.render(MessageReference.of("gui.item.staff.prestige_preview.ready"));
        Component blocked = messages.render(MessageReference.of("gui.item.staff.prestige_preview.not_ready"));
        Component refreshLore = messages.render(MessageReference.of("gui.item.staff.refresh.lore"));

        assertHeading(List.of(player), "tmydwc", NamedTextColor.AQUA);
        assertHeading(List.of(info), "Player Info", NamedTextColor.AQUA);
        assertHeading(List.of(summary), "Requirements Summary", NamedTextColor.AQUA);
        assertHeading(List.of(money), "Money", NamedTextColor.GOLD);
        assertHeading(List.of(skill), "Total Skill Level", NamedTextColor.AQUA);
        assertHeading(List.of(preview), "Prestige Preview", NamedTextColor.LIGHT_PURPLE);
        assertHeading(List.of(refresh), "Refresh", NamedTextColor.AQUA);
        assertHeading(List.of(ready), "Ready", NamedTextColor.GREEN);
        assertHeading(List.of(blocked), "Not Ready", NamedTextColor.RED);
        assertEquals(NamedTextColor.GREEN, textNode(online, "Online").color());
        assertEquals(NamedTextColor.GRAY, textNode(inspect, "Click to inspect").color());
        assertEquals(NamedTextColor.WHITE, textNode(value, "$1 / $8").color());
        assertEquals(NamedTextColor.GREEN, textNode(met, "Met").color());
        assertEquals(NamedTextColor.RED, textNode(notMet, "Not Met").color());
        assertEquals(NamedTextColor.GRAY, textNode(refreshLore, "Reload current player state.").color());
        assertFalse(textNode(refreshLore, "Reload current player state.")
                .decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE);
        assertFalse(textNode(refreshLore, "Reload current player state.")
                .decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE);
    }

    @Test
    @DisplayName("[Phase 9F-A correction] Player roots open silently while advanced GUI routing remains available")
    void routesBothPlayerCommandsToInventoryOpen(@TempDir Path temporaryDirectory) throws Exception {
        PhaseSixCommandService commands = mock(PhaseSixCommandService.class);
        PaperPhaseSixGuiController controller = mock(PaperPhaseSixGuiController.class);
        PaperMessageService messages = messages(temporaryDirectory);
        GuiSessionView view = blockedView();
        when(commands.execute(any(CommandInvocation.class))).thenReturn(CompletableFuture.completedFuture(
                CommandResponse.gui("gui.open", List.of(), view)));
        PaperPhaseSixCommandAdapter adapter = new PaperPhaseSixCommandAdapter(commands,
                mock(CommandCompletionService.class), immediateScheduler(), controller, messages);
        PaperPlayerGuiCommandAdapter playerAdapter = new PaperPlayerGuiCommandAdapter(adapter);
        Player player = mock(Player.class);
        Command command = mock(Command.class);
        preparePlayer(player);

        assertTrue(playerAdapter.onCommand(player, command, "prestige", new String[0]));
        assertTrue(adapter.onCommand(player, command, "maddprestige", new String[0]));
        assertTrue(adapter.onCommand(player, command, "maddprestige", new String[] {"gui"}));
        assertFalse(playerAdapter.onCommand(player, command, "prestige", new String[] {"why"}));
        assertEquals(List.of(), playerAdapter.onTabComplete(player, command, "prestige", new String[0]));

        verify(controller, times(3)).open(player, view);
        verify(player, never()).sendMessage(any(Component.class));
        ArgumentCaptor<CommandInvocation> invocations = ArgumentCaptor.forClass(CommandInvocation.class);
        verify(commands, times(3)).execute(invocations.capture());
        assertEquals(List.of(), invocations.getAllValues().get(0).arguments());
        assertEquals(List.of(), invocations.getAllValues().get(1).arguments());
        assertEquals(List.of("gui"), invocations.getAllValues().get(2).arguments());
    }

    @Test
    @DisplayName("[Phase 9F-A correction] Blocked state with optional sections absent constructs and opens")
    @SuppressWarnings("unchecked")
    void constructsAndOpensBlockedInventory(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        List<Component> renderedNames = new ArrayList<>();
        List<List<Component>> renderedLore = new ArrayList<>();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = mockConstruction(ItemStack.class, (item, context) -> {
                    ItemMeta meta = mock(ItemMeta.class);
                    doAnswer(invocation -> {
                        Consumer<? super ItemMeta> editor = invocation.getArgument(0);
                        editor.accept(meta);
                        return true;
                    }).when(item).editMeta(any());
                    doAnswer(invocation -> {
                        renderedLore.add(List.copyOf(invocation.getArgument(0)));
                        return null;
                    }).when(meta).lore(anyList());
                    doAnswer(invocation -> {
                        renderedNames.add(invocation.getArgument(0));
                        return null;
                    }).when(meta).displayName(any(Component.class));
                })) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenReturn(inventory);
            PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(mock(PlayerGuiService.class),
                    guard, immediateScheduler(), messages);

            assertDoesNotThrow(() -> controller.open(player, blockedView()));
            verify(player).openInventory(inventory);
            assertFalse(items.constructed().isEmpty());
        }

        String lore = renderedLore.stream().flatMap(List::stream)
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        assertTrue(lore.contains("1 / 2\n\n✗ Money: $8 / $50"));
        assertTrue(lore.contains("✗ Money: $8 / $50"));
        assertTrue(lore.contains("✓ Total Skill Level: 1 / 1"));
        assertFalse(lore.contains("Mode:"));
        assertFalse(lore.contains("All"));
        assertFalse(lore.contains("Any"));
        assertFalse(lore.contains("X_OF_N"));
        assertFalse(lore.contains(" met"));
        assertFalse(lore.contains("mcMMO"));
        assertTrue(renderedNames.stream().allMatch(component ->
                component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE));
        assertTrue(renderedLore.stream().flatMap(List::stream).allMatch(component ->
                component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE));
        assertTrue(renderedLore.stream().flatMap(List::stream)
                .noneMatch(component -> containsDecoration(component, TextDecoration.BOLD)));
        assertHeading(renderedNames, "Progress", NamedTextColor.AQUA);
        assertHeading(renderedNames, "Balance", NamedTextColor.GOLD);
        assertHeading(renderedNames, "Requirements", NamedTextColor.AQUA);
        assertHeading(renderedNames, "Rewards", NamedTextColor.GREEN);
        assertHeading(renderedNames, "Prestige", NamedTextColor.DARK_PURPLE);
        assertHeading(renderedNames, "Close", NamedTextColor.RED);
        assertSemanticIndicator(renderedLore, "✗", NamedTextColor.RED);
        assertSemanticIndicator(renderedLore, "✓", NamedTextColor.GREEN);

        assertEquals("Prestige 3", plain(messages.render(
                MessageReference.of("gui.title.player", "current", 3))));
        assertEquals("Prestige 3 → 4", plain(messages.render(
                MessageReference.of("gui.title.prestige_preview", "current", 3, "target", 4))));
        assertEquals("Confirm Prestige 4", plain(messages.render(
                MessageReference.of("gui.title.prestige_confirmation", "target", 4))));
    }

    @Test
    @DisplayName("[Phase 9F-B] Staff Dashboard renders only useful bold navigation without revision leakage")
    @SuppressWarnings("unchecked")
    void rendersCleanStaffDashboard(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        List<Component> renderedNames = new ArrayList<>();
        List<List<Component>> renderedLore = new ArrayList<>();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = mockConstruction(ItemStack.class, (item, context) -> {
                    ItemMeta meta = mock(ItemMeta.class);
                    doAnswer(invocation -> {
                        Consumer<? super ItemMeta> editor = invocation.getArgument(0);
                        editor.accept(meta);
                        return true;
                    }).when(item).editMeta(any());
                    doAnswer(invocation -> {
                        renderedLore.add(List.copyOf(invocation.getArgument(0)));
                        return null;
                    }).when(meta).lore(anyList());
                    doAnswer(invocation -> {
                        renderedNames.add(invocation.getArgument(0));
                        return null;
                    }).when(meta).displayName(any(Component.class));
                })) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenReturn(inventory);
            PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(mock(PlayerGuiService.class),
                    guard, immediateScheduler(), messages);

            assertDoesNotThrow(() -> controller.open(player, staffDashboardView()));
            verify(player).openInventory(inventory);
            assertFalse(items.constructed().isEmpty());
        }

        String visible = java.util.stream.Stream
                .concat(renderedNames.stream(), renderedLore.stream().flatMap(List::stream))
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        assertHeading(renderedNames, "Player Management", NamedTextColor.AQUA);
        assertHeading(renderedNames, "Configuration", NamedTextColor.GOLD);
        assertHeading(renderedNames, "Overview", NamedTextColor.DARK_PURPLE);
        assertHeading(renderedNames, "History & Audit", NamedTextColor.LIGHT_PURPLE);
        assertHeading(renderedNames, "System Status", NamedTextColor.GREEN);
        assertHeading(renderedNames, "Close", NamedTextColor.RED);
        assertTrue(visible.contains("Inspect a player."));
        assertTrue(visible.contains("View active Prestige configuration."));
        assertTrue(visible.contains("Review recent activity and exceptions."));
        assertFalse(visible.contains("Original"));
        assertFalse(visible.contains("Read-Only"));
        assertFalse(visible.contains("Revision:"));
        assertFalse(visible.contains("phase9f-b-read-only"));
        assertTrue(renderedLore.stream().flatMap(List::stream).allMatch(component ->
                component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE));
        assertTrue(renderedLore.stream().flatMap(List::stream)
                .noneMatch(component -> containsDecoration(component, TextDecoration.BOLD)));
    }

    @Test
    @DisplayName("[Phase 9F-A correction] Inventory construction or open failure remains visibly fail-closed")
    void inventoryFailureProducesVisibleCommandFailure(@TempDir Path temporaryDirectory) throws Exception {
        PhaseSixCommandService commands = mock(PhaseSixCommandService.class);
        PaperPhaseSixGuiController controller = mock(PaperPhaseSixGuiController.class);
        PaperMessageService messages = messages(temporaryDirectory);
        GuiSessionView view = blockedView();
        when(commands.execute(any(CommandInvocation.class))).thenReturn(CompletableFuture.completedFuture(
                CommandResponse.gui("gui.open", List.of(), view)));
        Player player = mock(Player.class);
        preparePlayer(player);
        ArrayList<Component> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(player).sendMessage(any(Component.class));
        doThrow(new IllegalStateException("qualification construction failure")).when(controller).open(player, view);
        PaperPhaseSixCommandAdapter adapter = new PaperPhaseSixCommandAdapter(commands,
                mock(CommandCompletionService.class), immediateScheduler(), controller, messages);

        assertTrue(adapter.onCommand(player, mock(Command.class), "maddprestige", new String[0]));

        String visible = sent.stream().map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        assertTrue(visible.contains("[command.failed]"));
    }

    @Test
    @DisplayName("[Phase 9F-A correction] First Main click defers and opens the Preview with valid ownership")
    void firstMainClickDefersPreviewHandoff(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        PlayerGuiService playerGui = mock(PlayerGuiService.class);
        Player player = mock(Player.class);
        preparePlayer(player);
        when(player.isOnline()).thenReturn(true);
        PaperGuiInventory source = mock(PaperGuiInventory.class);
        UUID sourceSession = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(source.sessionId()).thenReturn(sourceSession);
        when(source.actionAt(22)).thenReturn(Optional.of(actionId));
        GuiSessionView destination = previewView();
        when(playerGui.click(any(), org.mockito.ArgumentMatchers.eq(sourceSession),
                org.mockito.ArgumentMatchers.eq(actionId)))
                .thenReturn(CompletableFuture.completedFuture(PlayerGuiInteractionResult.navigate(destination)));
        DeferredScheduler deferred = new DeferredScheduler();
        InventoryClickEvent click = click(source, player);
        Inventory rendered = mock(Inventory.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = paperItems()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenReturn(rendered);
            PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(
                    playerGui, guard, deferred, messages);

            assertDoesNotThrow(() -> new PaperGuiInventory(destination, messages));

            controller.onClick(click);

            verify(click).setCancelled(true);
            verify(player, never()).openInventory(any(Inventory.class));
            assertEquals(1, deferred.pending());

            deferred.runNext();

            verify(player).openInventory(rendered);
            verify(player, never()).sendMessage(any(Component.class));
            assertFalse(items.constructed().isEmpty());

            InventoryCloseEvent sourceClose = mock(InventoryCloseEvent.class);
            Inventory sourceInventory = mock(Inventory.class);
            when(sourceInventory.getHolder()).thenReturn(source);
            when(sourceClose.getInventory()).thenReturn(sourceInventory);
            controller.onClose(sourceClose);
            verify(playerGui).closeView(sourceSession);
            verify(playerGui, never()).closeView(destination.sessionId());
        }
    }

    @Test
    @DisplayName("[Phase 9F-A correction] Failed Preview open retires its authority and reports visibly")
    void failedPreviewOpenIsVisibleAndRecoverable(@TempDir Path temporaryDirectory) throws Exception {
        PaperMessageService messages = messages(temporaryDirectory);
        PlayerGuiService playerGui = mock(PlayerGuiService.class);
        Player player = mock(Player.class);
        preparePlayer(player);
        when(player.isOnline()).thenReturn(true);
        ArrayList<Component> sent = new ArrayList<>();
        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(player).sendMessage(any(Component.class));
        PaperGuiInventory source = mock(PaperGuiInventory.class);
        UUID sourceSession = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(source.sessionId()).thenReturn(sourceSession);
        when(source.actionAt(22)).thenReturn(Optional.of(actionId));
        GuiSessionView destination = previewView();
        when(playerGui.click(any(), org.mockito.ArgumentMatchers.eq(sourceSession),
                org.mockito.ArgumentMatchers.eq(actionId)))
                .thenReturn(CompletableFuture.completedFuture(PlayerGuiInteractionResult.navigate(destination)));
        DeferredScheduler deferred = new DeferredScheduler();
        InventoryClickEvent click = click(source, player);
        Inventory rendered = mock(Inventory.class);
        doThrow(new IllegalStateException("qualification Preview open failure"))
                .when(player).openInventory(rendered);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedConstruction<ItemStack> items = paperItems()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), any(Component.class))).thenReturn(rendered);
            PaperPhaseSixGuiController controller = new PaperPhaseSixGuiController(
                    playerGui, guard, deferred, messages);

            controller.onClick(click);
            deferred.runNext();

            verify(playerGui).closeView(destination.sessionId());
            verify(player).closeInventory();
            assertFalse(items.constructed().isEmpty());
            String visible = sent.stream().map(PlainTextComponentSerializer.plainText()::serialize)
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            assertTrue(visible.contains("GUI action failed safely"));
        }
    }

    private void assertBlocked(ClickType click, InventoryAction action, int rawSlot) {
        var decision = guard.click(click, action, rawSlot, 9);
        assertTrue(decision.cancelEvent());
        assertFalse(decision.dispatchServerAction());
    }

    private static InventoryClickEvent click(PaperGuiInventory holder, Player player) {
        return click(holder, player, 22);
    }

    private static InventoryClickEvent click(PaperGuiInventory holder, Player player, int rawSlot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(27);
        when(view.getTopInventory()).thenReturn(inventory);
        when(event.getView()).thenReturn(view);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getWhoClicked()).thenReturn(player);
        return event;
    }

    private static InventoryCloseEvent close(PaperGuiInventory holder) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(holder.getInventory());
        return event;
    }

    private static MockedConstruction<ItemStack> paperItems() {
        return mockConstruction(ItemStack.class, (item, context) -> {
            ItemMeta meta = mock(ItemMeta.class);
            doAnswer(invocation -> {
                Consumer<? super ItemMeta> editor = invocation.getArgument(0);
                editor.accept(meta);
                return true;
            }).when(item).editMeta(any());
        });
    }

    private static GuiSessionView blockedView() {
        List<GuiDisplayItem> items = List.of(
                GuiDisplayItem.display(0, GuiItemIcon.BORDER_PURPLE, MessageReference.of("gui.item.border"),
                        List.of()),
                GuiDisplayItem.display(1, GuiItemIcon.BORDER_AQUA, MessageReference.of("gui.item.border"),
                        List.of()),
                GuiDisplayItem.display(4, GuiItemIcon.PROGRESS, MessageReference.of("gui.item.progress.title"),
                        List.of(MessageReference.of("gui.item.progress.prestige", "current", 3),
                                MessageReference.of("gui.item.progress.next", "target", 4),
                                MessageReference.of("gui.item.progress.not_ready"))),
                GuiDisplayItem.display(10, GuiItemIcon.BALANCE, MessageReference.of("gui.item.balance.title"),
                        List.of(MessageReference.of("gui.item.balance.current", "current", "$8"))),
                GuiDisplayItem.display(13, GuiItemIcon.REQUIREMENTS,
                        MessageReference.of("gui.item.requirements.title"),
                        List.of(MessageReference.of("gui.item.requirements.progress.incomplete",
                                        "progress", 1, "total", 2),
                                MessageReference.of("gui.item.separator"),
                                MessageReference.of("gui.item.requirement.money.not_met",
                                        "label", "Money", "current", "$8", "target", "$50"),
                                MessageReference.of("gui.item.requirement.total_skill_level.met",
                                        "label", "Total Skill Level", "current", 1, "target", 1))),
                GuiDisplayItem.display(16, GuiItemIcon.REWARD, MessageReference.of("gui.item.rewards.title"),
                        List.of(MessageReference.of("gui.item.change.value", "value", "$1"))),
                GuiDisplayItem.display(22, GuiItemIcon.PRESTIGE, MessageReference.of("gui.action.prestige"),
                        List.of(MessageReference.of("gui.item.prestige.open_preview",
                                "current", 3, "target", 4))),
                GuiDisplayItem.display(26, GuiItemIcon.CLOSE, MessageReference.of("gui.action.close"), List.of()));
        return new GuiSessionView(UUID.randomUUID(), GuiAudience.PLAYER,
                MessageReference.of("gui.title.player", "current", 3),
                List.of(), Instant.now().plusSeconds(300), GuiScreenKind.PLAYER, 27, items);
    }

    private static GuiSessionView staffDashboardView() {
        List<GuiDisplayItem> items = List.of(
                GuiDisplayItem.display(4, GuiItemIcon.STAFF,
                        MessageReference.of("gui.item.staff.overview.title"),
                        List.of(MessageReference.of("gui.item.staff.overview.configuration.active"),
                                MessageReference.of("gui.item.staff.overview.providers",
                                        "current", 2, "total", 2))),
                GuiDisplayItem.display(10, GuiItemIcon.PLAYERS,
                        MessageReference.of("gui.action.staff.players"),
                        List.of(MessageReference.of("gui.item.staff.players.lore"))),
                GuiDisplayItem.display(12, GuiItemIcon.CONFIGURATION,
                        MessageReference.of("gui.action.staff.configuration"),
                        List.of(MessageReference.of("gui.item.staff.configuration.lore"))),
                GuiDisplayItem.display(14, GuiItemIcon.HISTORY,
                        MessageReference.of("gui.action.staff.history"),
                        List.of(MessageReference.of("gui.item.staff.history.lore"))),
                GuiDisplayItem.display(16, GuiItemIcon.SYSTEM_STATUS,
                        MessageReference.of("gui.action.staff.system_status.healthy"),
                        List.of(MessageReference.of("gui.item.staff.system_status.lore",
                                "status", "Available"))),
                GuiDisplayItem.display(26, GuiItemIcon.CLOSE,
                        MessageReference.of("gui.action.close"), List.of()));
        return new GuiSessionView(UUID.randomUUID(), GuiAudience.STAFF,
                MessageReference.of("gui.title.staff.dashboard"),
                List.of(), Instant.now().plusSeconds(300), GuiScreenKind.STAFF_DASHBOARD, 27, items);
    }

    private static GuiSessionView previewView() {
        GuiSessionView main = blockedView();
        List<GuiDisplayItem> items = main.items().stream().map(item -> item.slot() == 10
                ? GuiDisplayItem.display(10, GuiItemIcon.BALANCE, MessageReference.of("gui.item.balance.title"),
                        List.of(MessageReference.of("gui.item.balance.projected",
                                "current", "$7", "projected", "$1")))
                : item).toList();
        return new GuiSessionView(UUID.randomUUID(), GuiAudience.PLAYER,
                MessageReference.of("gui.title.prestige_preview", "current", 5, "target", 6),
                List.of(), Instant.now().plusSeconds(300), GuiScreenKind.PRESTIGE_PREVIEW, 27, items);
    }

    private static OperationPreview staffPreview(UUID playerId, ConfigRevisionId revision) {
        ExplanationNode money = new ExplanationNode("requirement.leaf", ExplanationStatus.SATISFIED,
                "Satisfied", Map.of("metric", "balance", "current", "12", "target", "10"), List.of());
        ExplanationNode skill = new ExplanationNode("requirement.leaf", ExplanationStatus.SATISFIED,
                "Satisfied", Map.of("metric", "total_level", "current", "1", "target", "1"), List.of());
        ExplanationNode requirements = new ExplanationNode("requirement.group", ExplanationStatus.SATISFIED,
                "Ready", Map.of("mode", "ALL", "progress", "2", "threshold", "2"), List.of(money, skill));
        return new OperationPreview(OperationKind.PRESTIGE, playerId, true, "Prestige 6 → 7",
                Optional.of(requirements), List.of("$5"), List.of("$1"), List.of(), List.of(), List.of(), revision,
                Map.of(), List.of(), List.of(
                        MessageReference.of("command.preview.prestige_state_change",
                                "current_prestige", 6, "target_prestige", 7,
                                "current_lifetime", 6, "target_lifetime", 7),
                        MessageReference.of("command.preview.cost", "id", "vault-cost", "provider", "vault",
                                "type", "WITHDRAW", "amount", "5", "canonical", "5", "value", "Money"),
                        MessageReference.of("command.preview.reward", "id", "vault-reward", "provider", "vault",
                                "type", "DEPOSIT", "amount", "1", "canonical", "1", "value", "Money"),
                        MessageReference.of("command.preview.balance_projection",
                                "current", "12", "projected", "8")), List.of());
    }

    private static void assertHeading(List<Component> names, String label, NamedTextColor color) {
        Component heading = names.stream().filter(component -> plain(component).equals(label))
                .findFirst().orElseThrow();
        Component text = textNode(heading, label);
        assertEquals(TextDecoration.State.TRUE, text.decoration(TextDecoration.BOLD));
        assertEquals(color, text.color());
    }

    private static void assertSemanticIndicator(
            List<List<Component>> lore,
            String indicator,
            NamedTextColor color) {
        Component line = lore.stream().flatMap(List::stream)
                .filter(component -> plain(component).startsWith(indicator))
                .findFirst().orElseThrow();
        assertEquals(color, textNode(line, indicator).color());
    }

    private static Component textNode(Component component, String text) {
        Component found = textNodeOrNull(component, text);
        if (found == null) {
            throw new java.util.NoSuchElementException(text);
        }
        return found;
    }

    private static Component textNodeOrNull(Component component, String text) {
        if (component instanceof TextComponent textComponent && textComponent.content().equals(text)) {
            return component;
        }
        return component.children().stream().map(child -> textNodeOrNull(child, text))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static boolean containsDecoration(Component component, TextDecoration decoration) {
        return component.decoration(decoration) == TextDecoration.State.TRUE
                || component.children().stream().anyMatch(child -> containsDecoration(child, decoration));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void preparePlayer(Player player) {
        when(player.getUniqueId()).thenReturn(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        when(player.getName()).thenReturn("phase9f-player");
        when(player.hasPermission(anyString())).thenReturn(true);
    }

    private static PaperMessageService messages(Path directory) throws Exception {
        return PaperMessageService.open(directory, PaperMessageService.class.getClassLoader(), ignored -> { });
    }

    private static net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource healthyStaffStatus() {
        var summary = new net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Summary(
                net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Health.HEALTHY,
                true, 2, 2);
        return new net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource() {
            @Override
            public Summary summary() {
                return summary;
            }

            @Override
            public java.util.concurrent.CompletionStage<Snapshot> inspect() {
                return CompletableFuture.completedFuture(new Snapshot(summary, List.of(
                        new Component("Vault", Health.HEALTHY, ""),
                        new Component("Persistence", Health.HEALTHY, "SQLite schema is current"))));
            }
        };
    }

    private static PaperTaskScheduler immediateScheduler() {
        return new PaperTaskScheduler() {
            @Override
            public <T> java.util.concurrent.CompletionStage<T> submit(
                    ExecutionThread thread,
                    java.util.function.Supplier<T> task) {
                try {
                    return CompletableFuture.completedFuture(task.get());
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            }
        };
    }

    private static final class DeferredScheduler implements PaperTaskScheduler {
        private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();

        @Override
        public <T> java.util.concurrent.CompletionStage<T> submit(
                ExecutionThread thread,
                java.util.function.Supplier<T> task) {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public <T> java.util.concurrent.CompletionStage<T> submitDeferred(
                ExecutionThread thread,
                java.util.function.Supplier<T> task) {
            CompletableFuture<T> result = new CompletableFuture<>();
            tasks.add(() -> {
                try {
                    result.complete(task.get());
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            });
            return result;
        }

        private int pending() {
            return tasks.size();
        }

        private void runNext() {
            tasks.remove().run();
        }
    }
}
