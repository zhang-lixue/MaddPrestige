package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.List;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionClaimBlockRewardProvider;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionMetricProvider;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionProviderDescriptors;
import net.maddkraft.maddprestige.integrations.griefprevention.OfficialGriefPreventionAccess;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import org.bukkit.plugin.Plugin;

/** Loaded only after the exact GriefPrevention dependency is present and enabled. */
final class GriefPreventionIntegrationBootstrap {
    private GriefPreventionIntegrationBootstrap() {
    }

    static List<ManagedProvider> create(Plugin plugin, ProviderRegistry registry,
            IntegrationTaskScheduler scheduler, Clock clock, String version) {
        OfficialGriefPreventionAccess access = new OfficialGriefPreventionAccess((GriefPrevention) plugin);
        MutableProviderHealth metricHealth = health(clock);
        MutableProviderHealth rewardHealth = health(clock);
        return List.of(managed(new GriefPreventionMetricProvider(access, scheduler, metricHealth, clock, version),
                        registry, metricHealth),
                managed(new GriefPreventionClaimBlockRewardProvider(access, scheduler, rewardHealth, version),
                        registry, rewardHealth));
    }

    private static ManagedProvider managed(net.maddkraft.maddprestige.api.provider.Provider provider,
            ProviderRegistry registry, MutableProviderHealth health) {
        return new ManagedProvider(provider, health,
                new ProviderRegistrationGate(registry, provider.descriptor().id(), health));
    }

    private static MutableProviderHealth health(Clock clock) {
        return new MutableProviderHealth(clock, ProviderHealthState.AVAILABLE,
                "griefprevention.available", "Exact public API binding acquired");
    }
}
