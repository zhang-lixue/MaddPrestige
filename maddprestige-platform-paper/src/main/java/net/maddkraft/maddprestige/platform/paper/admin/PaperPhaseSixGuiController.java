package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Objects;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiService;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.PaperThreadGuard;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Paper rendering/event boundary for server-owned Phase 6 GUI sessions. */
public final class PaperPhaseSixGuiController implements Listener {
    private final PlayerGuiService playerGui;
    private final PaperGuiInventoryGuard guard;
    private final PaperTaskScheduler scheduler;
    private final PaperMessageService messages;

    public PaperPhaseSixGuiController(
            PlayerGuiService playerGui,
            PaperGuiInventoryGuard guard,
            PaperTaskScheduler scheduler,
            PaperMessageService messages) {
        this.playerGui = Objects.requireNonNull(playerGui, "player GUI");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void open(Player player, GuiSessionView view) {
        PaperThreadGuard.requireServerThread("Open Phase 6 GUI");
        try {
            player.openInventory(new PaperGuiInventory(view, messages).getInventory());
        } catch (RuntimeException failure) {
            playerGui.closeView(view.sessionId());
            throw failure;
        }
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

    @EventHandler(ignoreCancelled = false)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PaperGuiInventory holder) {
            playerGui.closeView(holder.sessionId());
        }
    }

    private void dispatch(Player player, PaperGuiInventory holder, java.util.UUID actionId) {
        try {
            playerGui.click(PaperPermissionSubjects.from(player), holder.sessionId(), actionId)
                    .whenComplete((result, failure) -> deferDelivery(player, result, failure));
        } catch (AdministrationException exception) {
            SemanticPresentation.administration(exception).stream().map(messages::render)
                    .forEach(player::sendMessage);
        }
    }

    private void deferDelivery(
            Player player,
            net.maddkraft.maddprestige.core.admin.ui.PlayerGuiInteractionResult result,
            Throwable failure) {
        scheduler.submitDeferred(ExecutionThread.PAPER_SERVER_THREAD, () -> {
            try {
                deliver(player, result, failure);
            } catch (RuntimeException deliveryFailure) {
                reportFailure(player, deliveryFailure);
            }
            return null;
        });
    }

    private void deliver(
            Player player,
            net.maddkraft.maddprestige.core.admin.ui.PlayerGuiInteractionResult result,
            Throwable failure) {
        if (failure != null) {
            reportFailure(player, failure);
        } else if (player.isOnline()) {
            result.messages().stream().map(messages::render).forEach(player::sendMessage);
            result.nextView().ifPresent(view -> open(player, view));
            if (result.close()) {
                player.closeInventory();
            }
        }
    }

    private void reportFailure(Player player, Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof AdministrationException exception) {
            SemanticPresentation.administration(exception).stream().map(messages::render)
                    .forEach(player::sendMessage);
        } else {
            player.sendMessage(messages.render("gui.action.failed"));
        }
        player.closeInventory();
    }
}
