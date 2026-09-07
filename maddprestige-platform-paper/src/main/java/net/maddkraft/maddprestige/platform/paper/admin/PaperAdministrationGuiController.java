package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiService;
import net.maddkraft.maddprestige.core.admin.ui.GuiAudience;
import net.maddkraft.maddprestige.core.admin.ui.StaffGuiService;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.PaperThreadGuard;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

/** Paper rendering/event boundary for server-owned administration GUI sessions. */
public final class PaperAdministrationGuiController implements Listener {
    private final PlayerGuiService playerGui;
    private final StaffGuiService staffGui;
    private final PaperGuiInventoryGuard guard;
    private final PaperTaskScheduler scheduler;
    private final PaperMessageService messages;
    private final Consumer<Throwable> failureDiagnostics;
    private final Map<UUID, PaperNumericInputInventory> numericInputs = new HashMap<>();

    public PaperAdministrationGuiController(
            PlayerGuiService playerGui,
            PaperGuiInventoryGuard guard,
            PaperTaskScheduler scheduler,
            PaperMessageService messages) {
        this(playerGui, null, guard, scheduler, messages, ignored -> { });
    }

    public PaperAdministrationGuiController(
            PlayerGuiService playerGui,
            StaffGuiService staffGui,
            PaperGuiInventoryGuard guard,
            PaperTaskScheduler scheduler,
            PaperMessageService messages) {
        this(playerGui, staffGui, guard, scheduler, messages, ignored -> { });
    }

    public PaperAdministrationGuiController(
            PlayerGuiService playerGui,
            StaffGuiService staffGui,
            PaperGuiInventoryGuard guard,
            PaperTaskScheduler scheduler,
            PaperMessageService messages,
            Consumer<Throwable> failureDiagnostics) {
        this.playerGui = Objects.requireNonNull(playerGui, "player GUI");
        this.staffGui = staffGui;
        this.guard = Objects.requireNonNull(guard, "guard");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.failureDiagnostics = Objects.requireNonNull(failureDiagnostics, "failure diagnostics");
    }

    public void open(Player player, GuiSessionView view) {
        PaperThreadGuard.requireServerThread("Open MaddPrestige GUI");
        retireNumericInput(player.getUniqueId());
        PaperNumericInputInventory candidate = null;
        try {
            if (view.textInput().isPresent()) {
                UUID submitAction = numericSubmitAction(view);
                candidate = new PaperNumericInputInventory(player, view, messages,
                        input -> staffGui != null && staffGui.acceptsNumericInput(
                                PaperPermissionSubjects.from(player), view.sessionId(), submitAction, input));
                numericInputs.put(player.getUniqueId(), candidate);
                candidate.open();
            } else {
                player.openInventory(new PaperGuiInventory(view, messages).getInventory());
            }
        } catch (RuntimeException failure) {
            if (candidate != null) {
                numericInputs.remove(player.getUniqueId(), candidate);
                candidate.retirePresentationItems();
            }
            closeView(view.audience(), view.sessionId());
            throw failure;
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Optional<PaperGuiViewHolder> resolved = holder(event.getView());
        if (resolved.isEmpty()) {
            return;
        }
        PaperGuiViewHolder holder = resolved.orElseThrow();
        var decision = guard.click(event.getClick(), event.getAction(), event.getRawSlot(),
                event.getView().getTopInventory().getSize());
        event.setCancelled(decision.cancelEvent());
        if (!decision.dispatchServerAction() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        holder.actionAt(event.getRawSlot()).ifPresent(actionId -> {
            if (holder instanceof PaperNumericInputInventory input && input.submitSlot(event.getRawSlot())) {
                dispatchInput(player, input, actionId, input.inputText(event.getView()));
            } else {
                dispatch(player, holder, actionId);
            }
        });
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (holder(event.getView()).isEmpty()) {
            return;
        }
        var decision = guard.drag(event.getRawSlots(), event.getView().getTopInventory().getSize());
        event.setCancelled(decision.cancelEvent());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClose(InventoryCloseEvent event) {
        Optional<PaperGuiViewHolder> resolved = holder(event.getView());
        if (resolved.isEmpty()
                && event.getInventory().getHolder() instanceof PaperGuiViewHolder inventoryHolder) {
            resolved = Optional.of(inventoryHolder);
        }
        resolved.ifPresent(holder -> {
            if (holder instanceof PaperNumericInputInventory input
                    && event.getPlayer() instanceof Player player) {
                input.retirePresentationItems();
                numericInputs.remove(player.getUniqueId(), input);
            }
            closeView(holder.audience(), holder.sessionId());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onQuit(PlayerQuitEvent event) {
        retireNumericInput(event.getPlayer().getUniqueId());
    }

    /** Retires owned anvil contents before an accepted kick can close and return the native container. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        retireNumericInput(event.getPlayer().getUniqueId());
    }

    // This server-owned anvil result must be the final high-priority projection. Coexisting item plugins may
    // legitimately rewrite ordinary anvil results at HIGHEST; this handler is registered after them and only
    // claims the exact actor-bound native input view tracked in numericInputs.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        holder(event.getView()).filter(PaperNumericInputInventory.class::isInstance)
                .map(PaperNumericInputInventory.class::cast).ifPresent(input -> {
                    input.prepare(event);
                    if (event.getView().getPlayer() instanceof Player player && input.queueRefresh()) {
                        scheduler.submitDeferred(ExecutionThread.PAPER_SERVER_THREAD, () -> {
                            refreshNumericResult(player, input);
                            return null;
                        });
                    }
                });
    }

    private Optional<PaperGuiViewHolder> holder(InventoryView view) {
        if (view == null) {
            return Optional.empty();
        }
        if (view.getTopInventory().getHolder() instanceof PaperGuiViewHolder holder) {
            return Optional.of(holder);
        }
        if (view.getPlayer() instanceof Player player) {
            PaperNumericInputInventory input = numericInputs.get(player.getUniqueId());
            if (input != null && input.matches(view)) {
                return Optional.of(input);
            }
        }
        return Optional.empty();
    }

    private void dispatch(Player player, PaperGuiViewHolder holder, java.util.UUID actionId) {
        try {
            interaction(player, holder, actionId)
                    .whenComplete((result, failure) -> deferDelivery(player, result, failure));
        } catch (AdministrationException exception) {
            SemanticPresentation.administration(exception).stream().map(messages::render)
                    .forEach(player::sendMessage);
        }
    }

    private void dispatchInput(
            Player player,
            PaperNumericInputInventory holder,
            java.util.UUID actionId,
            String input) {
        try {
            staffGui.submitNumericInput(PaperPermissionSubjects.from(player),
                    holder.sessionId(), actionId, input)
                    .whenComplete((result, failure) -> deferDelivery(player, result, failure));
        } catch (AdministrationException exception) {
            SemanticPresentation.administration(exception).stream().map(messages::render)
                    .forEach(player::sendMessage);
        }
    }

    private java.util.concurrent.CompletionStage<
            net.maddkraft.maddprestige.core.admin.ui.PlayerGuiInteractionResult> interaction(
            Player player,
            PaperGuiViewHolder holder,
            java.util.UUID actionId) {
        if (GuiAudience.STAFF.equals(holder.audience())) {
            if (staffGui == null) {
                throw new IllegalStateException("Staff GUI is unavailable");
            }
            return staffGui.click(PaperPermissionSubjects.from(player), holder.sessionId(), actionId);
        }
        return playerGui.click(PaperPermissionSubjects.from(player), holder.sessionId(), actionId);
    }

    private void closeView(GuiAudience audience, java.util.UUID sessionId) {
        if (GuiAudience.STAFF.equals(audience) && staffGui != null) {
            staffGui.closeView(sessionId);
        } else {
            playerGui.closeView(sessionId);
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
            result.messages().stream().map(this::renderResultMessage).forEach(player::sendMessage);
            result.nextView().ifPresent(view -> open(player, view));
            if (result.close()) {
                retireNumericInput(player.getUniqueId());
                player.closeInventory();
            }
        }
    }

    private Component renderResultMessage(MessageReference reference) {
        Component rendered = messages.render(reference);
        if (!reference.key().equals("gui.staff.player.uuid.copy")) {
            return rendered;
        }
        String value = reference.argument("uuid").orElseThrow(() ->
                new IllegalArgumentException("Copy UUID message requires a server-owned UUID"));
        java.util.UUID.fromString(value);
        return rendered.clickEvent(ClickEvent.copyToClipboard(value));
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
            try {
                failureDiagnostics.accept(failure);
            } catch (RuntimeException ignored) {
                // Diagnostics must never replace the visible fail-closed response.
            }
            player.sendMessage(messages.render("gui.action.failed"));
        }
        retireNumericInput(player.getUniqueId());
        player.closeInventory();
    }

    /** Clears all live native-input presentation state before plugin shutdown can close those anvil menus. */
    public void shutdown() {
        PaperThreadGuard.requireServerThread("Close MaddPrestige GUI inputs");
        List.copyOf(numericInputs.keySet()).forEach(this::retireNumericInput);
    }

    private void retireNumericInput(UUID playerId) {
        PaperNumericInputInventory input = numericInputs.remove(playerId);
        if (input == null) {
            return;
        }
        input.retirePresentationItems();
        closeView(input.audience(), input.sessionId());
    }

    private void refreshNumericResult(Player player, PaperNumericInputInventory input) {
        if (player.isOnline()
                && numericInputs.get(player.getUniqueId()) == input
                && input.matches(player.getOpenInventory())) {
            input.refreshResult();
        } else {
            input.cancelQueuedRefresh();
        }
    }

    private static UUID numericSubmitAction(GuiSessionView view) {
        return view.items().stream().filter(item -> item.slot() == 2).findFirst()
                .flatMap(net.maddkraft.maddprestige.core.admin.ui.GuiDisplayItem::actionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Numeric input view has no server-owned submit action"));
    }
}
