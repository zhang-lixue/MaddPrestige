package net.maddkraft.maddprestige.platform.paper.integration;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;

/** Startup-only readiness probe over CraftEngine's public loaded-item registry. */
final class CraftEngineReadinessProbe {
    private CraftEngineReadinessProbe() {
    }

    static boolean ready() {
        return !CraftEngineItems.loadedItems().isEmpty();
    }
}
