package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompilation;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseFiveCorrectionPassTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);
    private final PhaseFiveIntegrationCompiler compiler = new PhaseFiveIntegrationCompiler();

    @Test
    @DisplayName("Phase 5 root integration objects reject scalar, sequence, and null shapes")
    void rejectsMalformedRootMappingShapes() {
        List<Malformed> cases = List.of(
                new Malformed("vault: true", "integrations.vault"),
                new Malformed("vault: 5", "integrations.vault"),
                new Malformed("mcmmo: \"enabled\"", "integrations.mcmmo"),
                new Malformed("placeholderapi: []", "integrations.placeholderapi"),
                new Malformed("economyshopgui: false", "integrations.economyshopgui"),
                new Malformed("quickshop: \"yes\"", "integrations.quickshop"),
                new Malformed("vault: null", "integrations.vault"));
        for (Malformed malformed : cases) {
            assertInvalid(malformed.yaml(), malformed.path(), "Expected mapping");
        }
    }

    @Test
    @DisplayName("Every nested Phase 5 object rejects a present non-mapping value")
    void rejectsMalformedNestedMappingShapes() {
        List<Malformed> cases = List.of(
                new Malformed("placeholderapi:\n  output: true", "integrations.placeholderapi.output"),
                new Malformed("placeholderapi:\n  inputs: true", "integrations.placeholderapi.inputs"),
                new Malformed("placeholderapi:\n  inputs: []", "integrations.placeholderapi.inputs"),
                new Malformed("placeholderapi:\n  inputs:\n    tokens: invalid",
                        "integrations.placeholderapi.inputs[\"tokens\"]"),
                new Malformed("economyshopgui:\n  progression-credit: false",
                        "integrations.economyshopgui.progression-credit"),
                new Malformed("quickshop:\n  progression-credit: true",
                        "integrations.quickshop.progression-credit"));
        for (Malformed malformed : cases) {
            assertInvalid(malformed.yaml(), malformed.path(), "Expected mapping");
        }
    }

    @Test
    @DisplayName("Phase 5 scalar fields reject string coercion and report the exact path")
    void rejectsScalarCoercion() {
        assertInvalid("schema-version: \"5\"", "integrations.schema-version", "Expected integer");
        assertInvalid("vault:\n  enabled: \"true\"", "integrations.vault.enabled", "Expected boolean");
    }

    @Test
    @DisplayName("[A53] Placeholder input keys must be canonical string MetricIds")
    void validatesPlaceholderInputKeys() {
        assertInvalid(input("7", "'%tokens_balance%'"), "integrations.placeholderapi.inputs[7]",
                "string metric ID key");
        assertInvalid(input("true", "'%tokens_balance%'"), "integrations.placeholderapi.inputs[true]",
                "string metric ID key");
        assertInvalid(input("'Bad ID'", "'%tokens_balance%'"),
                "integrations.placeholderapi.inputs[\"Bad ID\"]", "metric ID must match");

        var valid = compiler.compile(input("tokens_2", "'%tokens_balance%'"));
        assertFalse(valid.validation().hasErrors());
        assertEquals("%tokens_balance%", valid.configuration().placeholderInputs().get("tokens_2").placeholder());
    }

    @Test
    @DisplayName("[A53] Placeholder recursion detection follows case-insensitive expansion-token semantics")
    void rejectsEveryMaddPrestigeExpansionCase() {
        for (String placeholder : List.of("%maddprestige_stage%", "%MADDPRESTIGE_stage%",
                "%MaddPrestige_stage%", "%mAdDpReStIgE_stage%", "%external_value% %MADDPRESTIGE_stage%")) {
            assertInvalid(input("loop", "'" + placeholder + "'"),
                    "integrations.placeholderapi.inputs[\"loop\"].placeholder",
                    "recursively consume MaddPrestige");
        }

        for (String placeholder : List.of("%someplugin_maddprestige_value%", "%external_value%",
                "prefix %first_value% middle %second_value% suffix")) {
            var valid = compiler.compile(input("external_metric", "'" + placeholder + "'"));
            assertFalse(valid.validation().hasErrors(), placeholder);
        }
    }

    @Test
    @DisplayName("[A54] EconomyShopGUI and QuickShop progression knobs fail closed")
    void rejectsUnsupportedShopProgression() {
        assertInvalid("economyshopgui:\n  progression-credit:\n    enabled: true",
                "integrations.economyshopgui.progression-credit.enabled", "unsupported");
        assertInvalid("quickshop:\n  progression-credit:\n    enabled: true",
                "integrations.quickshop.progression-credit.enabled", "unsupported");

        var compatibility = compiler.compile("""
                economyshopgui:
                  compatibility-enabled: true
                  progression-credit:
                    enabled: false
                """);
        assertFalse(compatibility.validation().hasErrors());
        assertTrue(PhaseFiveIntegrationPlan.from(compatibility.configuration()).economyShopGuiCompatibility());
    }

    @Test
    @DisplayName("[A50] Exact reconciliation supports active to dormant and dormant to active")
    void reconcilesSingleProviderBothDirections() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId id = new ProviderId("first_integration");
        MutableProviderHealth state = health();
        IntegrationProviderLifecycle lifecycle = lifecycle(registry, managed(registry, id, state));

        lifecycle.reconcileActiveProviders(Set.of(id));
        assertEquals(ActivationState.ACTIVE, registry.find(id).orElseThrow().activation());
        lifecycle.reconcileActiveProviders(Set.of());
        assertEquals(ActivationState.INACTIVE, registry.find(id).orElseThrow().activation());
        assertEquals(ProviderHealthState.AVAILABLE, state.get().state());
        lifecycle.reconcileActiveProviders(Set.of(id));
        assertEquals(ActivationState.ACTIVE, registry.find(id).orElseThrow().activation());
    }

    @Test
    @DisplayName("[A50] Shrinking, expanding, and repeated exact sets preserve unchanged generations")
    void reconcilesChangingSetsWithoutGenerationChurn() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId first = new ProviderId("integration_a");
        ProviderId second = new ProviderId("integration_b");
        ProviderId third = new ProviderId("integration_c");
        IntegrationProviderLifecycle lifecycle = lifecycle(registry, managed(registry, first, health()),
                managed(registry, second, health()), managed(registry, third, health()));

        lifecycle.reconcileActiveProviders(Set.of(first, second, third));
        long firstGeneration = registry.find(first).orElseThrow().generation();
        lifecycle.reconcileActiveProviders(Set.of(first));
        assertEquals(ActivationState.ACTIVE, registry.find(first).orElseThrow().activation());
        assertEquals(ActivationState.INACTIVE, registry.find(second).orElseThrow().activation());
        assertEquals(ActivationState.INACTIVE, registry.find(third).orElseThrow().activation());
        lifecycle.reconcileActiveProviders(Set.of(first, second));
        assertEquals(firstGeneration, registry.find(first).orElseThrow().generation());
        assertEquals(ActivationState.ACTIVE, registry.find(second).orElseThrow().activation());
        lifecycle.reconcileActiveProviders(Set.of(first, second));
        assertEquals(firstGeneration, registry.find(first).orElseThrow().generation());
    }

    @Test
    @DisplayName("[A50] An unrelated unhealthy dormant provider does not block required reconciliation")
    void ignoresUnhealthyDormantProvider() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId required = new ProviderId("required_integration");
        ProviderId unrelated = new ProviderId("unrelated_integration");
        MutableProviderHealth requiredHealth = health();
        MutableProviderHealth unrelatedHealth = health();
        IntegrationProviderLifecycle lifecycle = lifecycle(registry, managed(registry, required, requiredHealth),
                managed(registry, unrelated, unrelatedHealth));
        unrelatedHealth.transition(ProviderHealthState.UNHEALTHY, "test.unhealthy", "unrelated outage");

        lifecycle.reconcileActiveProviders(Set.of(required));
        assertEquals(ProviderHealthState.AVAILABLE, requiredHealth.get().state());
        assertEquals(ProviderHealthState.UNHEALTHY, unrelatedHealth.get().state());
        assertEquals(ActivationState.INACTIVE, registry.find(unrelated).orElseThrow().activation());
    }

    @Test
    @DisplayName("[A50] HOT_RELOAD plan removal deactivates the no-longer-reachable integration")
    void configurationReloadReconcilesExactPlan() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId mcMmo = new ProviderId("mcmmo");
        IntegrationProviderLifecycle lifecycle = lifecycle(registry, managed(registry, mcMmo, health()));
        PhaseFiveIntegrationCompilation enabled = compiler.compile("mcmmo:\n  enabled: true");
        PhaseFiveIntegrationCompilation disabled = compiler.compile("");

        lifecycle.reconcile(PhaseFiveIntegrationPlan.from(enabled.configuration()));
        long generation = registry.find(mcMmo).orElseThrow().generation();
        assertEquals(ActivationState.ACTIVE, registry.find(mcMmo).orElseThrow().activation());
        lifecycle.reconcile(PhaseFiveIntegrationPlan.from(disabled.configuration()));
        assertEquals(ActivationState.INACTIVE, registry.find(mcMmo).orElseThrow().activation());
        assertEquals(generation, registry.find(mcMmo).orElseThrow().generation());
    }

    private void assertInvalid(String yaml, String path, String explanationFragment) {
        PhaseFiveIntegrationCompilation result = compiler.compile(yaml);
        assertTrue(result.validation().hasErrors(), yaml);
        ValidationFinding finding = result.validation().findings().getFirst();
        assertEquals(path, finding.path(), yaml);
        assertTrue(finding.explanation().contains(explanationFragment), finding.explanation());
        assertTrue(PhaseFiveIntegrationPlan.from(result.configuration()).reachableProviders().isEmpty());
    }

    private static String input(String key, String placeholder) {
        return """
                placeholderapi:
                  inputs:
                    %s:
                      placeholder: %s
                      value-type: COUNT
                      maximum-age: PT5S
                """.formatted(key, placeholder);
    }

    private static IntegrationProviderLifecycle lifecycle(ProviderRegistry registry, ManagedProvider... providers) {
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        lifecycle.discover(List.of(providers));
        return lifecycle;
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

    private record Malformed(String yaml, String path) {
    }
}
