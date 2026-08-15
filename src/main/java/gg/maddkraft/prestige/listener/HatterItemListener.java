package gg.maddkraft.prestige.listener;

import gg.maddkraft.prestige.service.HatterItemService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

public final class HatterItemListener implements Listener {
    private final HatterItemService items;

    public HatterItemListener(HatterItemService items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!items.isHatterItem(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("The MaddHatter's Top Hat is soulbound."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean hat = items.isHatterItem(event.getCurrentItem()) || items.isHatterItem(event.getCursor());
        if (!hat) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesContainer = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        boolean shiftTransfer = event.isShiftClick() && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory());
        if (touchesContainer || shiftTransfer) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!items.isHatterItem(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        for (ItemStack ingredient : event.getInventory().getContents()) {
            if (items.isHatterItem(ingredient)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(items::isHatterItem);
    }
}
