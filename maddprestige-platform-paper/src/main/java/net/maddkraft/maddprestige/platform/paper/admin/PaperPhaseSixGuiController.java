package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.PaperThreadGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Paper rendering/event boundary for server-owned Phase 6 GUI sessions. */
public final class PaperPhaseSixGuiController implements Listener {
    private final GuiSessionService sessions;
    private final PaperGuiInventoryGuard guard;
    private final PaperTaskScheduler scheduler;

    public PaperPhaseSixGuiController(
            GuiSessionService sessions,
            PaperGuiInventoryGuard guard,
            PaperTaskScheduler scheduler) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void open(Player player, GuiSessionView view) {
        PaperThreadGuard.requireServerThread("Open Phase 6 GUI");
        player.openInventory(new PaperGuiInventory(view).getInventory());
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PaperGuiInventory holder)) {
            return;
        }
        var decision = guard.click(event.getClick(), event.getAction(), event.getRawSlot(),
                event.getView().getTopInventory().getSize());
        event.setCancelled(decision.cancelEvent());
        if (!decision.dispatchServerAction() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        holder.actionAt(event.getRawSlot()).ifPresent(actionId -> dispatch(player, holder, actionId));
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PaperGuiInventory)) {
            return;
        }
        var decision = guard.drag(event.getRawSlots(), event.getView().getTopInventory().getSize());
        event.setCancelled(decision.cancelEvent());
    }

    private void dispatch(Player player, PaperGuiInventory holder, java.util.UUID actionId) {
        try {
            sessions.click(PaperPermissionSubjects.from(player), holder.sessionId(), actionId)
                    .whenComplete((message, failure) -> scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, () -> {
                        if (failure == null) {
                            player.sendMessage(Component.text(message));
                        } else {
                            player.sendMessage(Component.text("The GUI action failed safely; reopen the current view."));
                        }
                        return null;
                    }));
        } catch (AdministrationException exception) {
            player.sendMessage(Component.text("[" + exception.code() + "] " + exception.getMessage()
                    + " Next: " + exception.remediation()));
        }
    }
}
