package net.maddkraft.maddprestige.integrations.quickshop;

import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Observes QuickShop's public success boundary for compatibility diagnostics only. It intentionally exposes no
 * progression handle, amount, reward, or metric capability because player-to-player traffic is wash-tradable.
 */
public final class QuickShopCompatibilityListener implements Listener {
    private final BooleanSupplier enabled;
    private final AtomicLong successfulTransactions = new AtomicLong();
    private final AtomicLong selfTransactions = new AtomicLong();

    public QuickShopCompatibilityListener(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPurchase(ShopSuccessPurchaseEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        observe(event.getPurchaser().getUniqueId(), event.getShop().getOwner().getUniqueId());
    }

    public void observe(UUID purchaser, UUID owner) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        successfulTransactions.incrementAndGet();
        if (purchaser != null && purchaser.equals(owner)) {
            selfTransactions.incrementAndGet();
        }
    }

    public long successfulTransactions() {
        return successfulTransactions.get();
    }

    public long selfTransactions() {
        return selfTransactions.get();
    }

    public long progressionCredits() {
        return 0;
    }
}
