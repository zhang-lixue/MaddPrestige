package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.List;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.craftengine.CraftEngineItemMetricProvider;
import net.maddkraft.maddprestige.integrations.craftengine.CraftEngineItemRewardProvider;
import net.maddkraft.maddprestige.integrations.craftengine.OfficialCraftEngineItemAccess;
import org.bukkit.Server;

/** Loaded only after the exact CraftEngine dependency is present, enabled, and initialized. */
final class CraftEngineIntegrationBootstrap {
    private CraftEngineIntegrationBootstrap() {
    }

    static List<ManagedProvider> create(Server server, ProviderRegistry registry,
            IntegrationTaskScheduler scheduler, Clock clock, int maximumQuantity, String version) {
        OfficialCraftEngineItemAccess access = new OfficialCraftEngineItemAccess();
        MutableProviderHealth metricHealth = health(clock);
        MutableProviderHealth rewardHealth = health(clock);
        CraftEngineItemMetricProvider metric = new CraftEngineItemMetricProvider(
                server, access, scheduler, metricHealth, clock, version);
        CraftEngineItemRewardProvider reward = new CraftEngineItemRewardProvider(
                server, access, scheduler, rewardHealth, maximumQuantity, version);
        return List.of(new ManagedProvider(metric, metricHealth,
                        new ProviderRegistrationGate(registry, metric.descriptor().id(), metricHealth)),
                new ManagedProvider(reward, rewardHealth,
                        new ProviderRegistrationGate(registry, reward.descriptor().id(), rewardHealth)));
    }

    private static MutableProviderHealth health(Clock clock) {
        return new MutableProviderHealth(clock, ProviderHealthState.AVAILABLE,
                "craftengine.available", "Exact public API binding acquired after item reload");
    }
}
