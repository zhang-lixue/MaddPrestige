package net.maddkraft.maddprestige.integrations;

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
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationPlan;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationSchema;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseFiveConfigurationAndLifecycleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A50] Empty Phase 5 integration configuration is truthful and dormant")
    void defaultsAreDormant() {
        var result = new PhaseFiveIntegrationCompiler().compile("");
        assertFalse(result.validation().hasErrors());
        assertFalse(result.configuration().vaultEnabled());
        assertFalse(result.configuration().mcMmoEnabled());
        assertFalse(result.configuration().placeholderOutputEnabled());
        assertTrue(result.configuration().placeholderInputs().isEmpty());
        assertFalse(result.configuration().economyShopGuiCompatibilityEnabled());
        assertFalse(result.configuration().economyShopGuiProgressionCreditEnabled());
        assertFalse(result.configuration().quickShopCompatibilityEnabled());
        assertFalse(result.configuration().quickShopProgressionCreditEnabled());
        assertTrue(PhaseFiveIntegrationPlan.from(result.configuration()).reachableProviders().isEmpty());
        assertTrue(PhaseFiveIntegrationSchema.create().find(
                "integrations.quickshop.progression-credit.enabled").isPresent());
    }

    @Test
    @DisplayName("[A54] QuickShop progression credit is rejected rather than accepted inertly")
    void rejectsQuickShopProgressionCredit() {
        var result = new PhaseFiveIntegrationCompiler().compile("""
                schema-version: 5
                quickshop:
                  progression-credit:
                    enabled: true
                """);
        assertTrue(result.validation().hasErrors());
        assertFalse(result.configuration().quickShopProgressionCreditEnabled());

        var inert = new PhaseFiveIntegrationCompiler().compile("""
                schema-version: 5
                vault:
                  magical-mode: true
                """);
        assertTrue(inert.validation().hasErrors());
    }

    @Test
    @DisplayName("[A53] Placeholder inputs are typed/freshness-bound and recursive outputs are rejected")
    void compilesTypedInputsAndRejectsRecursion() {
        var valid = new PhaseFiveIntegrationCompiler().compile("""
                schema-version: 5
                placeholderapi:
                  inputs:
                    tokens:
                      placeholder: '%tokens_balance%'
                      value-type: COUNT
                      maximum-age: PT5S
                """);
        assertFalse(valid.validation().hasErrors());
        assertEquals("%tokens_balance%", valid.configuration().placeholderInputs().get("tokens").placeholder());

        var recursive = new PhaseFiveIntegrationCompiler().compile("""
                schema-version: 5
                placeholderapi:
                  inputs:
                    loop:
                      placeholder: '%maddprestige_stage%'
                      value-type: STRING
                      maximum-age: PT5S
                """);
        assertTrue(recursive.validation().hasErrors());
    }

    @Test
    @DisplayName("[A50] Dormant discovered provider remains isolated while referenced provider activates")
    void activatesOnlyReferencedProviders() {
        ProviderRegistry registry = new ProviderRegistry();
        MutableProviderHealth firstHealth = health();
        MutableProviderHealth dormantHealth = health();
        ProviderId firstId = new ProviderId("first_integration");
        ProviderId dormantId = new ProviderId("dormant_integration");
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        lifecycle.discover(List.of(managed(registry, firstId, firstHealth),
                managed(registry, dormantId, dormantHealth)));
        lifecycle.reconcileActiveProviders(Set.of(firstId));

        assertEquals(ActivationState.ACTIVE, registry.find(firstId).orElseThrow().activation());
        assertEquals(ActivationState.INACTIVE, registry.find(dormantId).orElseThrow().activation());
        assertEquals(ProviderHealthState.AVAILABLE, firstHealth.get().state());
        assertEquals(ProviderHealthState.AVAILABLE, dormantHealth.get().state());
    }

    @Test
    @DisplayName("[A50] Disable unregisters exact bindings and recovery creates a new provider generation")
    void disableAndRecoveryAdvanceGeneration() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId id = new ProviderId("recoverable_integration");
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        MutableProviderHealth first = health();
        lifecycle.discover(List.of(managed(registry, id, first)));
        lifecycle.reconcileActiveProviders(Set.of(id));
        long generation = registry.find(id).orElseThrow().generation();

        lifecycle.dependencyUnavailable("plugin disabled");
        assertTrue(registry.find(id).isEmpty());
        assertEquals(ProviderHealthState.UNAVAILABLE, first.get().state());

        MutableProviderHealth replacement = health();
        lifecycle.discover(List.of(managed(registry, id, replacement)));
        lifecycle.reconcileActiveProviders(Set.of(id));
        assertEquals(generation + 1, registry.find(id).orElseThrow().generation());
        assertEquals(ProviderHealthState.AVAILABLE, replacement.get().state());
    }

    private static ManagedProvider managed(
            ProviderRegistry registry, ProviderId id, MutableProviderHealth health) {
        Provider provider = new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return new ProviderDescriptor(id, "maddprestige", "phase5", "test", List.of(), List.of());
            }

            @Override
            public ProviderHealth health() {
                return health.get();
            }
        };
        return new ManagedProvider(provider, health, new ProviderRegistrationGate(registry, id, health));
    }

    private static MutableProviderHealth health() {
        return new MutableProviderHealth(CLOCK, ProviderHealthState.AVAILABLE, "test.available", "test");
    }
}
