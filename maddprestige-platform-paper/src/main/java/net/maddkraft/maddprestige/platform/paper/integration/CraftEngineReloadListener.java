package net.maddkraft.maddprestige.platform.paper.integration;

import java.util.Objects;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Generation-changing rebind after every completed CraftEngine reload. */
final class CraftEngineReloadListener implements Listener {
    private final Runnable rebind;

    CraftEngineReloadListener(Runnable rebind) {
        this.rebind = Objects.requireNonNull(rebind, "rebind");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReload(CraftEngineReloadEvent event) {
        rebind.run();
    }
}
