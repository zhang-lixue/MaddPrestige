package net.maddkraft.maddprestige.platform.paper.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.admin.command.CommandResponse;
import net.maddkraft.maddprestige.core.admin.command.PhaseSixCommandService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.ui.GuiAudience;
import net.maddkraft.maddprestige.core.admin.ui.GuiDisplayItem;
import net.maddkraft.maddprestige.core.admin.ui.GuiItemIcon;
import net.maddkraft.maddprestige.core.admin.ui.GuiScreenKind;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiInteractionResult;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiService;
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
        assertEquals(Material.NETHER_STAR, PaperGuiInventory.material(GuiItemIcon.PRESTIGE));
        assertEquals(Material.WRITABLE_BOOK, PaperGuiInventory.material(GuiItemIcon.REQUIREMENTS));
        assertEquals(Material.GOLD_INGOT, PaperGuiInventory.material(GuiItemIcon.BALANCE));
        assertEquals(Material.RED_CONCRETE, PaperGuiInventory.material(GuiItemIcon.BLOCKED));
        assertEquals(Material.LIME_CONCRETE, PaperGuiInventory.material(GuiItemIcon.CONFIRM));
        assertEquals(Material.BARRIER, PaperGuiInventory.material(GuiItemIcon.CLOSE));
        assertEquals(Material.PURPLE_STAINED_GLASS_PANE,
                PaperGuiInventory.material(GuiItemIcon.BORDER_PURPLE));
        assertEquals(Material.CYAN_STAINED_GLASS_PANE,
                PaperGuiInventory.material(GuiItemIcon.BORDER_AQUA));
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
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(27);
        when(view.getTopInventory()).thenReturn(inventory);
        when(event.getView()).thenReturn(view);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getRawSlot()).thenReturn(22);
        when(event.getWhoClicked()).thenReturn(player);
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
