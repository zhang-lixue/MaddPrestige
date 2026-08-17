package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationConfiguration.PlaceholderInput;
import net.maddkraft.maddprestige.integrations.placeholder.OfficialPlaceholderResolver;
import net.maddkraft.maddprestige.integrations.placeholder.PlaceholderInputMetricProvider;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderExpansion;
import org.bukkit.Server;

/** Loaded only after the exact PlaceholderAPI dependency is present and enabled. */
final class PlaceholderApiIntegrationBootstrap {
    private PlaceholderApiIntegrationBootstrap() {
    }

    static Binding create(Server server, ProviderRegistry registry, IntegrationTaskScheduler scheduler,
            Map<String, PlaceholderInput> inputs, boolean outputEnabled,
            MaddPrestigePlaceholderCache outputCache, String ownerVersion, Clock clock, String version) {
        MutableProviderHealth health = new MutableProviderHealth(clock, ProviderHealthState.AVAILABLE,
                "placeholderapi.available", "Exact public PlaceholderAPI binding acquired");
        ProviderRegistrationGate gate = new ProviderRegistrationGate(
                registry, PlaceholderInputMetricProvider.PROVIDER_ID, health);
        PlaceholderInputMetricProvider provider = new PlaceholderInputMetricProvider(inputs,
                new OfficialPlaceholderResolver(server::getOfflinePlayer), scheduler, health, gate, clock,
                50_000, version);
        MaddPrestigePlaceholderExpansion expansion = outputEnabled
                ? new MaddPrestigePlaceholderExpansion(outputCache, ownerVersion) : null;
        Runnable activate = () -> {
            if (expansion != null && !expansion.register()) {
                throw new IllegalStateException("PlaceholderAPI rejected the MaddPrestige expansion");
            }
        };
        Runnable cleanup = () -> {
            if (expansion != null) {
                expansion.unregister();
            }
        };
        return new Binding(List.of(new ManagedProvider(provider, health, gate)), provider, activate, cleanup);
    }

    record Binding(List<ManagedProvider> providers, PlaceholderInputMetricProvider provider,
            Runnable activate, Runnable cleanup) {
        Binding {
            providers = List.copyOf(providers);
            java.util.Objects.requireNonNull(provider, "provider");
            java.util.Objects.requireNonNull(activate, "activation");
            java.util.Objects.requireNonNull(cleanup, "cleanup");
        }
    }
}
