package net.maddkraft.maddprestige.platform.paper.integration;

import java.util.function.BooleanSupplier;
import net.maddkraft.maddprestige.integrations.economyshopgui.EconomyShopGuiCompatibilityListener;
import org.bukkit.event.Listener;

/** Loaded only after the exact EconomyShopGUI dependency is enabled. */
final class EconomyShopGuiIntegrationBootstrap {
    private EconomyShopGuiIntegrationBootstrap() {
    }

    static Listener create(BooleanSupplier acceptingEvents) {
        return new EconomyShopGuiCompatibilityListener(acceptingEvents);
    }
}
