package net.maddkraft.maddprestige.platform.paper.integration;

import java.util.function.BooleanSupplier;
import net.maddkraft.maddprestige.integrations.quickshop.QuickShopCompatibilityListener;
import org.bukkit.event.Listener;

/** Loaded only after the exact QuickShop-Hikari dependency is enabled. */
final class QuickShopIntegrationBootstrap {
    private QuickShopIntegrationBootstrap() {
    }

    static Listener create(BooleanSupplier acceptingEvents) {
        return new QuickShopCompatibilityListener(acceptingEvents);
    }
}
