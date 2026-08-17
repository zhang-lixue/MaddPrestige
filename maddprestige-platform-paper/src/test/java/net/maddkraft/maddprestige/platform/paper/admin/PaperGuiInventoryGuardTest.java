package net.maddkraft.maddprestige.platform.paper.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private void assertBlocked(ClickType click, InventoryAction action, int rawSlot) {
        var decision = guard.click(click, action, rawSlot, 9);
        assertTrue(decision.cancelEvent());
        assertFalse(decision.dispatchServerAction());
    }
}
