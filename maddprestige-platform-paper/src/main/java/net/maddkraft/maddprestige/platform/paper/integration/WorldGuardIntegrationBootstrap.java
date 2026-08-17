package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.List;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.worldguard.OfficialWorldGuardRegionAccess;
import net.maddkraft.maddprestige.integrations.worldguard.WorldGuardRegionMetricProvider;
import org.bukkit.Server;

/** Loaded only after the exact WorldGuard dependency is present and enabled. */
final class WorldGuardIntegrationBootstrap {
    private WorldGuardIntegrationBootstrap() {
    }

    static List<ManagedProvider> create(Server server, ProviderRegistry registry,
            IntegrationTaskScheduler scheduler, Clock clock, String version) {
        MutableProviderHealth health = new MutableProviderHealth(clock, ProviderHealthState.AVAILABLE,
                "worldguard.available", "Exact public API binding acquired");
        WorldGuardRegionMetricProvider provider = new WorldGuardRegionMetricProvider(
                new OfficialWorldGuardRegionAccess(server), scheduler, health, clock, version);
        return List.of(new ManagedProvider(provider, health,
                new ProviderRegistrationGate(registry, provider.descriptor().id(), health)));
    }
}
