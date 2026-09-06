package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Objects;
import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.InventoryView;

public final class PaperGuiInventoryGuard {
    public InventoryInteractionDecision click(
            ClickType click,
            InventoryAction action,
            int rawSlot,
            int topInventorySize) {
        Objects.requireNonNull(click, "click type");
        Objects.requireNonNull(action, "inventory action");
        if (topInventorySize < 0 || (rawSlot < -1 && rawSlot != InventoryView.OUTSIDE)) {
            throw new IllegalArgumentException("Inventory sizes/slots are invalid");
        }
        if (rawSlot < 0 || rawSlot >= topInventorySize) {
            return blocked("Clicks outside server-owned GUI slots never dispatch actions");
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return blocked("Shift, number-key, offhand, drop, double, and creative clicks are blocked");
        }
        if (action != InventoryAction.PICKUP_ALL && action != InventoryAction.PICKUP_HALF
                && action != InventoryAction.NOTHING) {
            return blocked("Inventory mutation/transfer actions are blocked");
        }
        return new InventoryInteractionDecision(true, true,
                "Cancel the inventory event and dispatch only the server-side session action");
    }

    public InventoryInteractionDecision drag(Set<Integer> rawSlots, int topInventorySize) {
        Objects.requireNonNull(rawSlots, "raw slots");
        if (topInventorySize < 0) {
            throw new IllegalArgumentException("Top inventory size cannot be negative");
        }
        return blocked(rawSlots.stream().anyMatch(slot -> slot >= 0 && slot < topInventorySize)
                ? "Dragging into the GUI is blocked" : "Dragging while an authoritative GUI is open is blocked");
    }

    private static InventoryInteractionDecision blocked(String reason) {
        return new InventoryInteractionDecision(true, false, reason);
    }
}
