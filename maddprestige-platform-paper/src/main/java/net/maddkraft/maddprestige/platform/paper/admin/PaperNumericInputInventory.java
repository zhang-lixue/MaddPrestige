package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.maddkraft.maddprestige.core.admin.ui.GuiAudience;
import net.maddkraft.maddprestige.core.admin.ui.GuiDisplayItem;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.platform.paper.PaperThreadGuard;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;

/** Three-slot, dependency-free anvil text input backed by one actor-bound core GUI session. */
final class PaperNumericInputInventory implements PaperGuiViewHolder {
    private static final int INPUT_SLOT = 0;
    private static final int BACK_SLOT = 1;
    private static final int SUBMIT_SLOT = 2;

    private final UUID sessionId;
    private final GuiAudience audience;
    private final Player player;
    private final String initialValue;
    private final Map<Integer, UUID> actionIds;
    private final AnvilView inventoryView;
    private final AnvilInventory inventory;
    private final ItemStack submitItem;
    private final Predicate<String> inputValidator;
    private boolean retired;
    private boolean refreshQueued;

    PaperNumericInputInventory(Player player, GuiSessionView view, PaperMessageService messages) {
        this(player, view, messages, ignored -> true, MenuType.ANVIL::create);
    }

    PaperNumericInputInventory(
            Player player,
            GuiSessionView view,
            PaperMessageService messages,
            Predicate<String> inputValidator) {
        this(player, view, messages, inputValidator, MenuType.ANVIL::create);
    }

    PaperNumericInputInventory(
            Player player,
            GuiSessionView view,
            PaperMessageService messages,
            BiFunction<Player, Component, AnvilView> anvilFactory) {
        this(player, view, messages, ignored -> true, anvilFactory);
    }

    PaperNumericInputInventory(
            Player player,
            GuiSessionView view,
            PaperMessageService messages,
            Predicate<String> inputValidator,
            BiFunction<Player, Component, AnvilView> anvilFactory) {
        PaperThreadGuard.requireServerThread("Create numeric input GUI");
        this.player = java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(messages, "messages");
        this.inputValidator = java.util.Objects.requireNonNull(inputValidator, "input validator");
        java.util.Objects.requireNonNull(anvilFactory, "anvil factory");
        initialValue = view.textInput().orElseThrow(() ->
                new IllegalArgumentException("Numeric input view requires text-input metadata")).initialValue();
        sessionId = view.sessionId();
        audience = view.audience();
        inventoryView = java.util.Objects.requireNonNull(
                anvilFactory.apply(player, PaperGuiInventory.renderTitle(view, messages)), "anvil view");
        inventory = inventoryView.getTopInventory();
        LinkedHashMap<Integer, UUID> slots = new LinkedHashMap<>();
        GuiDisplayItem input = itemAt(view, INPUT_SLOT);
        GuiDisplayItem back = itemAt(view, BACK_SLOT);
        GuiDisplayItem submit = itemAt(view, SUBMIT_SLOT);
        inventory.setItem(INPUT_SLOT, PaperGuiInventory.renderDisplayItem(input, messages));
        inventory.setItem(BACK_SLOT, PaperGuiInventory.renderDisplayItem(back, messages));
        submitItem = PaperGuiInventory.renderDisplayItem(submit, messages);
        inventory.setItem(SUBMIT_SLOT, submitItem);
        back.actionId().ifPresent(action -> slots.put(BACK_SLOT, action));
        submit.actionId().ifPresent(action -> slots.put(SUBMIT_SLOT, action));
        actionIds = Map.copyOf(slots);
    }

    void open() {
        inventoryView.open();
    }

    boolean matches(InventoryView candidate) {
        return candidate == inventoryView || candidate.getTopInventory() == inventory;
    }

    void prepare(PrepareAnvilEvent event) {
        event.getView().setRepairCost(0);
        event.getView().setRepairItemCountCost(0);
        event.setResult(!retired && inputValidator.test(inputText(event.getView())) ? submitItem : null);
    }

    /**
     * Coalesces native rename packets into one post-event refresh. Paper applies the event result only after every
     * listener returns, so a coexisting anvil listener can otherwise leave this owned output slot stale until the
     * client sends another rename packet.
     */
    boolean queueRefresh() {
        if (retired || refreshQueued) {
            return false;
        }
        refreshQueued = true;
        return true;
    }

    /** Reconciles only this server-owned output slot from the latest native rename text. */
    void refreshResult() {
        refreshQueued = false;
        if (retired) {
            return;
        }
        inventoryView.setRepairCost(0);
        inventoryView.setRepairItemCountCost(0);
        if (inputValidator.test(inputText(inventoryView))) {
            inventory.setItem(SUBMIT_SLOT, submitItem);
        } else {
            inventory.clear(SUBMIT_SLOT);
        }
        // A valid-to-valid rename leaves the authoritative ItemStack unchanged. Paper can therefore skip its
        // ordinary slot-dirty broadcast even though the client temporarily cleared the native anvil output.
        player.updateInventory();
    }

    void cancelQueuedRefresh() {
        refreshQueued = false;
    }

    /** Clears only this server-owned top inventory before vanilla can return anvil inputs to the viewer. */
    void retirePresentationItems() {
        if (retired) {
            return;
        }
        retired = true;
        refreshQueued = false;
        inventory.clear(INPUT_SLOT);
        inventory.clear(BACK_SLOT);
        inventory.clear(SUBMIT_SLOT);
    }

    String inputText(InventoryView view) {
        if (!(view instanceof AnvilView anvil)) {
            throw new IllegalArgumentException("Numeric input action requires an anvil view");
        }
        return Optional.ofNullable(anvil.getRenameText()).orElse("");
    }

    boolean submitSlot(int rawSlot) {
        return !retired && rawSlot == SUBMIT_SLOT;
    }

    String initialValue() {
        return initialValue;
    }

    @Override
    public UUID sessionId() {
        return sessionId;
    }

    @Override
    public GuiAudience audience() {
        return audience;
    }

    @Override
    public Optional<UUID> actionAt(int rawSlot) {
        return retired ? Optional.empty() : Optional.ofNullable(actionIds.get(rawSlot));
    }

    @Override
    public @NotNull AnvilInventory getInventory() {
        return inventory;
    }

    private static GuiDisplayItem itemAt(GuiSessionView view, int slot) {
        return view.items().stream().filter(item -> item.slot() == slot).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Numeric input view is missing required slot " + slot));
    }
}
