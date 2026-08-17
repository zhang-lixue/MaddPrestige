package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.List;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.mcmmo.McMmoAdjustedXpListener;
import net.maddkraft.maddprestige.integrations.mcmmo.McMmoMetricProvider;
import net.maddkraft.maddprestige.integrations.mcmmo.OfficialMcMmoExperienceAccess;

/** Loaded only after the exact mcMMO dependency is present and enabled. */
final class McMmoIntegrationBootstrap {
    private McMmoIntegrationBootstrap() {
    }

    static Binding create(ProviderRegistry registry, IntegrationTaskScheduler scheduler,
            ManualMetricHandle adjustedXp, Clock clock, String version) {
        MutableProviderHealth health = new MutableProviderHealth(clock, ProviderHealthState.AVAILABLE,
                "mcmmo.available", "Exact public mcMMO API binding acquired");
        McMmoMetricProvider provider = new McMmoMetricProvider(
                new OfficialMcMmoExperienceAccess(), scheduler, health, clock, version);
        ProviderRegistrationGate gate = new ProviderRegistrationGate(registry, provider.descriptor().id(), health);
        return new Binding(List.of(new ManagedProvider(provider, health, gate)),
                new McMmoAdjustedXpListener(adjustedXp, gate, clock));
    }

    record Binding(List<ManagedProvider> providers, McMmoAdjustedXpListener listener) {
        Binding {
            providers = List.copyOf(providers);
            java.util.Objects.requireNonNull(listener, "listener");
        }
    }
}
