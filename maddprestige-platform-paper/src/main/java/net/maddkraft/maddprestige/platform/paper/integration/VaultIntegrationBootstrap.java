package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.List;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.vault.VaultBalanceMetricProvider;
import net.maddkraft.maddprestige.integrations.vault.VaultEconomyBinding;
import net.maddkraft.maddprestige.integrations.vault.VaultEconomyCostProvider;
import net.maddkraft.maddprestige.integrations.vault.VaultEconomyRewardProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Loaded only after the exact Vault plugin and an enabled Economy service are available. */
final class VaultIntegrationBootstrap {
    private VaultIntegrationBootstrap() {
    }

    static List<ManagedProvider> create(Server server, ProviderRegistry registry,
            IntegrationTaskScheduler scheduler, Clock clock, String version) {
        RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null || !registration.getProvider().isEnabled()) {
            throw new IllegalStateException("Vault has no enabled Economy service");
        }
        Economy economy = registration.getProvider();
        MutableProviderHealth balanceHealth = health(clock);
        MutableProviderHealth costHealth = health(clock);
        MutableProviderHealth rewardHealth = health(clock);
        VaultEconomyBinding balance = binding(server, economy, scheduler, balanceHealth);
        VaultEconomyBinding cost = binding(server, economy, scheduler, costHealth);
        VaultEconomyBinding reward = binding(server, economy, scheduler, rewardHealth);
        return List.of(
                managed(new VaultBalanceMetricProvider(balance, clock, version), registry, balanceHealth),
                managed(new VaultEconomyCostProvider(cost, version), registry, costHealth),
                managed(new VaultEconomyRewardProvider(reward, version), registry, rewardHealth));
    }

    private static VaultEconomyBinding binding(Server server, Economy economy,
            IntegrationTaskScheduler scheduler, MutableProviderHealth health) {
        return new VaultEconomyBinding(economy, server::getOfflinePlayer, scheduler, health);
    }

    private static ManagedProvider managed(Provider provider, ProviderRegistry registry,
            MutableProviderHealth health) {
        return new ManagedProvider(provider, health,
                new ProviderRegistrationGate(registry, provider.descriptor().id(), health));
    }

    private static MutableProviderHealth health(Clock clock) {
        return new MutableProviderHealth(clock, ProviderHealthState.AVAILABLE,
                "vault.available", "Exact enabled Vault Economy service acquired");
    }
}
