package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.platform.paper.PaperThreadGuard;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Server-owned slot/action map. Item names and metadata never carry authority. */
public final class PaperGuiInventory implements InventoryHolder {
    private final UUID sessionId;
    private final Map<Integer, UUID> actionIds;
    private final Inventory inventory;

    public PaperGuiInventory(GuiSessionView view, PaperMessageService messages) {
        PaperThreadGuard.requireServerThread("Create Phase 6 GUI");
        java.util.Objects.requireNonNull(messages, "messages");
        sessionId = view.sessionId();
        int size = Math.max(9, Math.min(54, ((view.actions().size() + 8) / 9) * 9));
        inventory = Bukkit.createInventory(this, size, renderTitle(view, messages));
        LinkedHashMap<Integer, UUID> slots = new LinkedHashMap<>();
        for (int slot = 0; slot < view.actions().size() && slot < size; slot++) {
            var action = view.actions().get(slot);
            ItemStack item = new ItemStack(action.mutating() ? Material.REDSTONE_TORCH : Material.PAPER);
            item.editMeta(meta -> meta.displayName(renderActionLabel(action, messages)));
            inventory.setItem(slot, item);
            slots.put(slot, action.actionId());
        }
        actionIds = Map.copyOf(slots);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public static net.kyori.adventure.text.Component renderTitle(
            GuiSessionView view,
            PaperMessageService messages) {
        return messages.render(java.util.Objects.requireNonNull(view, "GUI view").title());
    }

    public static net.kyori.adventure.text.Component renderActionLabel(
            net.maddkraft.maddprestige.core.admin.ui.GuiAction action,
            PaperMessageService messages) {
        return messages.render(java.util.Objects.requireNonNull(action, "GUI action").label());
    }

    public java.util.Optional<UUID> actionAt(int rawSlot) {
        return java.util.Optional.ofNullable(actionIds.get(rawSlot));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
