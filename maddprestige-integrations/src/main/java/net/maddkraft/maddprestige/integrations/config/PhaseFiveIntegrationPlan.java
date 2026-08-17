package net.maddkraft.maddprestige.integrations.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;

/** Runtime-consumed activation decisions derived only from the validated canonical configuration. */
public record PhaseFiveIntegrationPlan(
        Set<ProviderId> reachableProviders,
        boolean placeholderOutput,
        boolean mcMmoEventSource,
        boolean economyShopGuiCompatibility,
        boolean quickShopCompatibility) {
    public PhaseFiveIntegrationPlan {
        reachableProviders = Set.copyOf(Objects.requireNonNull(reachableProviders, "reachable providers"));
    }

    public static PhaseFiveIntegrationPlan from(PhaseFiveIntegrationConfiguration configuration) {
        LinkedHashSet<ProviderId> providers = new LinkedHashSet<>();
        if (configuration.vaultEnabled()) {
            providers.add(new ProviderId("vault_economy_cost"));
            providers.add(new ProviderId("vault_economy_reward"));
            providers.add(new ProviderId("vault_balance"));
        }
        if (configuration.mcMmoEnabled()) {
            providers.add(new ProviderId("mcmmo"));
        }
        if (!configuration.placeholderInputs().isEmpty()) {
            providers.add(new ProviderId("placeholder_input"));
        }
        if (configuration.griefPreventionEnabled()) {
            providers.add(new ProviderId("griefprevention_claims"));
            providers.add(new ProviderId("griefprevention_claim_blocks_reward"));
        }
        if (configuration.worldGuardEnabled()) {
            providers.add(new ProviderId("worldguard_region"));
        }
        if (configuration.craftEngineEnabled()) {
            providers.add(new ProviderId("craftengine_item_count"));
            providers.add(new ProviderId("craftengine_item_reward"));
        }
        return new PhaseFiveIntegrationPlan(providers, configuration.placeholderOutputEnabled(),
                configuration.mcMmoEnabled(), configuration.economyShopGuiCompatibilityEnabled(),
                configuration.quickShopCompatibilityEnabled());
    }
}
