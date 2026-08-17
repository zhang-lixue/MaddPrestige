package net.maddkraft.maddprestige.platform.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseSevenOptionalCompositionTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[OR7-01] Production composition consumes every accepted Phase 5 and Phase 7 setting")
    void productionCompositionConsumesEverySetting() {
        var compiled = new PhaseFiveIntegrationCompiler().compile("""
                schema-version: 7
                vault: {enabled: true}
                mcmmo: {enabled: true}
                placeholderapi:
                  output: {enabled: true}
                  inputs:
                    external_balance:
                      placeholder: '%external_balance%'
                      value-type: COUNT
                      maximum-age: PT5S
                economyshopgui:
                  compatibility-enabled: true
                  progression-credit: {enabled: false}
                quickshop:
                  compatibility-enabled: true
                  progression-credit: {enabled: false}
                griefprevention: {enabled: true}
                worldguard: {enabled: true}
                craftengine: {enabled: true, reward-maximum-quantity: 128}
                """);
        assertFalse(compiled.validation().hasErrors(), compiled.validation().findings().toString());
        var plan = PhaseSevenOptionalIntegrationManager.composition(compiled.configuration());
        assertEquals(Set.of(
                "vault_economy_cost", "vault_economy_reward", "vault_balance", "mcmmo",
                "placeholder_input", "griefprevention_claims", "griefprevention_claim_blocks_reward",
                "worldguard_region", "craftengine_item_count", "craftengine_item_reward"),
                plan.reachableProviders().stream().map(ProviderId::value).collect(
                        java.util.stream.Collectors.toUnmodifiableSet()));
        assertTrue(plan.placeholderOutput());
        assertTrue(plan.mcMmoEventSource());
        assertTrue(plan.economyShopGuiCompatibility());
        assertTrue(plan.quickShopCompatibility());
        assertEquals(Map.of(
                "Vault", "2.20.2", "mcMMO", "2.2.053", "PlaceholderAPI", "2.12.3",
                "EconomyShopGUI", "7.2.0", "QuickShop-Hikari", "6.2.0.11",
                "GriefPrevention", "16.18.7", "WorldGuard", "7.0.18+2392-fa605e6",
                "CraftEngine", "26.7.4"),
                PhaseSevenOptionalIntegrationManager.qualifiedDependencies());
    }

    @Test
    @DisplayName("[OR7-03] CraftEngine enable waits; reload, disable, re-enable, and reload are explicit")
    void craftEngineRegistryLifecycleIsReloadAuthoritative() {
        CraftEngineRegistryLifecycle lifecycle = new CraftEngineRegistryLifecycle();
        assertEquals(CraftEngineRegistryLifecycle.State.ABSENT, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.NONE, lifecycle.dependencyPresent());
        assertEquals(CraftEngineRegistryLifecycle.State.WAITING_FOR_REGISTRY, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.NONE, lifecycle.startupProbe(false));
        assertEquals(CraftEngineRegistryLifecycle.State.WAITING_FOR_REGISTRY, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.BIND, lifecycle.reloadCompleted());
        assertEquals(CraftEngineRegistryLifecycle.State.AVAILABLE, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.REBIND, lifecycle.reloadCompleted());
        assertEquals(CraftEngineRegistryLifecycle.Transition.UNBIND, lifecycle.dependencyDisabled());
        assertEquals(CraftEngineRegistryLifecycle.State.DISABLED_OR_UNAVAILABLE, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.NONE, lifecycle.dependencyPresent());
        assertEquals(CraftEngineRegistryLifecycle.State.WAITING_FOR_REGISTRY, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.BIND, lifecycle.reloadCompleted());
    }

    @Test
    @DisplayName("[OR7-03] Nonempty startup public-registry probe can recover a missed first reload only")
    void startupProbeRequiresNonemptyRegistryAndWaitingState() {
        CraftEngineRegistryLifecycle lifecycle = new CraftEngineRegistryLifecycle();
        lifecycle.dependencyPresent();
        assertEquals(CraftEngineRegistryLifecycle.Transition.NONE, lifecycle.startupProbe(false));
        assertEquals(CraftEngineRegistryLifecycle.Transition.BIND, lifecycle.startupProbe(true));
        assertEquals(CraftEngineRegistryLifecycle.State.AVAILABLE, lifecycle.state());
        assertEquals(CraftEngineRegistryLifecycle.Transition.NONE, lifecycle.startupProbe(true));
    }

    @Test
    @DisplayName("[OR7-03] Same-ID definition reload advances generation and rejects stale authority")
    void sameIdReloadReplacesDefinitionAndRejectsStaleRegistration() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId id = new ProviderId("craftengine_item_count");
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        Provider first = provider(id, "definition-a");
        ManagedProvider firstManaged = managed(registry, first);
        lifecycle.discover(List.of(firstManaged));
        lifecycle.reconcileActiveProviders(Set.of(id));
        var firstRegistration = lifecycle.registrations().get(id);

        Provider replacement = provider(id, "definition-b");
        lifecycle.rebind(List.of(managed(registry, replacement)), Set.of(id));
        var replacementRegistration = lifecycle.registrations().get(id);

        assertEquals(firstRegistration.generation() + 1, replacementRegistration.generation());
        assertFalse(registry.acceptsEvent(firstRegistration));
        assertTrue(registry.acceptsEvent(replacementRegistration));
        assertEquals("definition-b", registry.provider(id).orElseThrow().descriptor()
                .capabilities().getFirst().attributes().get("definition"));

        lifecycle.reconcileActiveProviders(Set.of());
        assertFalse(registry.acceptsEvent(replacementRegistration));
        lifecycle.dependencyUnavailable("item registry removed the binding");
        assertTrue(registry.find(id).isEmpty());
    }

    private static Provider provider(ProviderId id, String definition) {
        MutableProviderHealth health = health();
        return new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return new ProviderDescriptor(id, "maddprestige", "phase7", "26.7.4", List.of(),
                        List.of(new CapabilityDescriptor("item", "metric", "fixture",
                                Map.of("definition", definition))));
            }

            @Override
            public ProviderHealth health() {
                return health.get();
            }
        };
    }

    private static ManagedProvider managed(ProviderRegistry registry, Provider provider) {
        MutableProviderHealth health = health();
        Provider wrapped = new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return provider.descriptor();
            }

            @Override
            public ProviderHealth health() {
                return health.get();
            }
        };
        return new ManagedProvider(wrapped, health,
                new ProviderRegistrationGate(registry, wrapped.descriptor().id(), health));
    }

    private static MutableProviderHealth health() {
        return new MutableProviderHealth(CLOCK, ProviderHealthState.AVAILABLE, "test.available", "test");
    }
}
