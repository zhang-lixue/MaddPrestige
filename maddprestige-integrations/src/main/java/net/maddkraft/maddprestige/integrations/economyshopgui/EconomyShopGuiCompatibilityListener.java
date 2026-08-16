package net.maddkraft.maddprestige.integrations.economyshopgui;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.util.Transaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** EconomyShopGUI event compatibility diagnostics with deliberately zero progression capability. */
public final class EconomyShopGuiCompatibilityListener implements Listener {
    private static final Set<Transaction.Type> SELL_TYPES = EnumSet.of(Transaction.Type.SELL_GUI_SCREEN,
            Transaction.Type.SELL_ALL_COMMAND, Transaction.Type.SELL_ALL_SCREEN, Transaction.Type.SELL_SCREEN,
            Transaction.Type.QUICK_SELL, Transaction.Type.SHOPSTAND_SELL_SCREEN, Transaction.Type.AUTO_SELL_CHEST,
            Transaction.Type.API_SELL);
    private static final Set<Transaction.Result> SUCCESS_RESULTS = EnumSet.of(Transaction.Result.SUCCESS,
            Transaction.Result.SUCCESS_COMMANDS_EXECUTED);
    private final BooleanSupplier acceptingEvents;
    private final AtomicLong observedEvents = new AtomicLong();
    private final AtomicLong successfulSaleEvents = new AtomicLong();

    public EconomyShopGuiCompatibilityListener(BooleanSupplier acceptingEvents) {
        this.acceptingEvents = Objects.requireNonNull(acceptingEvents, "event acceptance");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostTransaction(PostTransactionEvent event) {
        if (!acceptingEvents.getAsBoolean()) {
            return;
        }
        observedEvents.incrementAndGet();
        if (SELL_TYPES.contains(event.getTransactionType())
                && SUCCESS_RESULTS.contains(event.getTransactionResult())) {
            successfulSaleEvents.incrementAndGet();
        }
    }

    public long observedEvents() {
        return observedEvents.get();
    }

    public long successfulSaleEvents() {
        return successfulSaleEvents.get();
    }

    public long progressionCredits() {
        return 0L;
    }
}
