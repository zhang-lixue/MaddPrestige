package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementMetricCollector;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseFiveOutageHardeningTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);
    private static final List<ProviderHealthState> UNUSABLE = List.of(
            ProviderHealthState.NOT_INSTALLED,
            ProviderHealthState.INACTIVE,
            ProviderHealthState.DEGRADED,
            ProviderHealthState.UNSUPPORTED,
            ProviderHealthState.UNAVAILABLE,
            ProviderHealthState.UNHEALTHY);

    @Test
    @DisplayName("[A50] Phase 5 health usability is one fail-closed AVAILABLE/ACTIVE allowlist")
    void canonicalHealthPredicateIsFailClosed() {
        for (ProviderHealthState state : ProviderHealthState.values()) {
            MutableProviderHealth health = health(state);
            assertEquals(state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE,
                    health.isUsable(), state.name());
        }
    }

    @Test
    @DisplayName("[A50] Config activation and off-on toggles preserve every operational outage state")
    void configurationCannotHealOperationalOutages() {
        for (ProviderHealthState state : UNUSABLE) {
            ProviderRegistry registry = new ProviderRegistry();
            ProviderId id = new ProviderId("outage_" + state.name().toLowerCase(java.util.Locale.ROOT));
            MutableProviderHealth health = health(state);
            ProviderRegistrationGate gate = new ProviderRegistrationGate(registry, id, health);
            IntegrationProviderLifecycle lifecycle = lifecycle(registry, managed(registry, id, health, gate));
            long generation = registry.find(id).orElseThrow().generation();

            lifecycle.reconcileActiveProviders(Set.of(id));
            assertEquals(ActivationState.ACTIVE, registry.find(id).orElseThrow().activation(), state.name());
            assertEquals(state, health.get().state(), state.name());
            assertFalse(gate.allowsUse(), state.name());

            lifecycle.reconcileActiveProviders(Set.of());
            assertEquals(state, health.get().state(), state.name());
            lifecycle.reconcileActiveProviders(Set.of(id));
            assertEquals(state, health.get().state(), state.name());
            assertEquals(generation, registry.find(id).orElseThrow().generation(), state.name());
            assertFalse(gate.allowsUse(), state.name());
        }
    }

    @Test
    @DisplayName("[A50] Healthy desired bindings activate without generation churn")
    void healthyDesiredProvidersActivateNormally() {
        for (ProviderHealthState state : List.of(ProviderHealthState.AVAILABLE, ProviderHealthState.ACTIVE)) {
            ProviderRegistry registry = new ProviderRegistry();
            ProviderId id = new ProviderId("healthy_" + state.name().toLowerCase(java.util.Locale.ROOT));
            MutableProviderHealth health = health(state);
            ProviderRegistrationGate gate = new ProviderRegistrationGate(registry, id, health);
            IntegrationProviderLifecycle lifecycle = lifecycle(registry, managed(registry, id, health, gate));
            long generation = registry.find(id).orElseThrow().generation();

            lifecycle.reconcileActiveProviders(Set.of(id));
            lifecycle.reconcileActiveProviders(Set.of(id));
            assertTrue(gate.allowsUse(), state.name());
            assertEquals(state, health.get().state(), state.name());
            assertEquals(generation, registry.find(id).orElseThrow().generation(), state.name());
        }
    }

    @Test
    @DisplayName("[A50] Explicit recovery is exact-binding scoped; unregister/rebind advances generation")
    void recoveryAndRebindHaveAuthoritativeGenerationSemantics() {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId id = new ProviderId("recoverable_binding");
        MutableProviderHealth firstHealth = health(ProviderHealthState.UNHEALTHY);
        ProviderRegistrationGate firstGate = new ProviderRegistrationGate(registry, id, firstHealth);
        IntegrationProviderLifecycle lifecycle = lifecycle(registry,
                managed(registry, id, firstHealth, firstGate));
        lifecycle.reconcileActiveProviders(Set.of(id));
        ProviderRegistration first = lifecycle.registrations().get(id);

        lifecycle.dependencyRecovered(first, "successful health probe");
        assertEquals(ProviderHealthState.AVAILABLE, firstHealth.get().state());
        assertTrue(firstGate.allowsUse());
        assertEquals(first.generation(), registry.find(id).orElseThrow().generation());

        lifecycle.dependencyUnavailable("plugin disabled");
        assertFalse(firstGate.allowsUse());
        assertThrows(IllegalStateException.class,
                () -> lifecycle.dependencyRecovered(first, "stale probe result"));

        MutableProviderHealth secondHealth = health(ProviderHealthState.AVAILABLE);
        ProviderRegistrationGate secondGate = new ProviderRegistrationGate(registry, id, secondHealth);
        lifecycle.discover(List.of(managed(registry, id, secondHealth, secondGate)));
        lifecycle.reconcileActiveProviders(Set.of(id));
        ProviderRegistration second = lifecycle.registrations().get(id);
        assertEquals(first.generation() + 1, second.generation());
        assertFalse(firstGate.allowsUse());
        assertTrue(secondGate.allowsUse());
    }

    @Test
    @DisplayName("[A50] Compiled config activates but canonical collection blocks an unhealthy provider")
    void compiledPlanCannotMakeUnhealthyProviderExecutable() {
        var compilation = new PhaseFiveIntegrationCompiler().compile("mcmmo:\n  enabled: true");
        assertFalse(compilation.validation().hasErrors());
        PhaseFiveIntegrationPlan plan = PhaseFiveIntegrationPlan.from(compilation.configuration());

        ProviderRegistry registry = new ProviderRegistry();
        MutableProviderHealth health = health(ProviderHealthState.UNHEALTHY);
        CountingMetricProvider provider = new CountingMetricProvider(health);
        ProviderRegistrationGate gate = new ProviderRegistrationGate(registry, provider.descriptor().id(), health);
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        lifecycle.discover(List.of(new ManagedProvider(provider, health, gate)));
        lifecycle.reconcile(plan);
        ProviderRegistration registration = lifecycle.registrations().get(provider.descriptor().id());

        RequirementDefinition definition = RequirementDefinition.create(new RequirementId("mcmmo_power"),
                provider.descriptor().id(), CountingMetricProvider.METRIC, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.integer(1)), MeasurementScope.ABSOLUTE, CompletionMode.LIVE,
                ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false);
        Map<RequirementId, MetricSample> samples = new RequirementMetricCollector(registry, CLOCK)
                .collect(UUID.randomUUID(), new RequirementLeaf(definition),
                        Map.of(provider.descriptor().id(), registration.generation()))
                .toCompletableFuture().join();

        assertEquals(ActivationState.ACTIVE,
                registry.find(provider.descriptor().id()).orElseThrow().activation());
        assertEquals(ProviderHealthState.UNHEALTHY, health.get().state());
        assertEquals(MetricSampleStatus.UNAVAILABLE, samples.get(definition.id()).status());
        assertEquals(0, provider.reads.get());
    }

    private static IntegrationProviderLifecycle lifecycle(
            ProviderRegistry registry, ManagedProvider... providers) {
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        lifecycle.discover(List.of(providers));
        return lifecycle;
    }

    private static ManagedProvider managed(
            ProviderRegistry registry,
            ProviderId id,
            MutableProviderHealth health,
            ProviderRegistrationGate gate) {
        return new ManagedProvider(provider(id, health), health, gate);
    }

    private static Provider provider(ProviderId id, MutableProviderHealth health) {
        return new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return new ProviderDescriptor(id, "maddprestige", "phase5", "test", List.of(), List.of());
            }

            @Override
            public ProviderHealth health() {
                return health.get();
            }
        };
    }

    private static MutableProviderHealth health(ProviderHealthState state) {
        return new MutableProviderHealth(CLOCK, state, "test." + state.name().toLowerCase(java.util.Locale.ROOT),
                "test");
    }

    private static final class CountingMetricProvider implements MetricProvider {
        private static final ProviderId ID = new ProviderId("mcmmo");
        private static final MetricId METRIC = new MetricId("power_level");
        private final MutableProviderHealth health;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingMetricProvider(MutableProviderHealth health) {
            this.health = health;
        }

        @Override
        public Collection<MetricDescriptor> metrics() {
            return List.of(new MetricDescriptor(ID, METRIC, MetricValueType.INTEGER,
                    MetricOperator.compatibleWith(MetricValueType.INTEGER), Set.of(MetricReadMode.CURRENT), false,
                    MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), "Power",
                    "Test power", "levels", "test"));
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId, List<MetricQuery> queries, long providerGeneration) {
            reads.incrementAndGet();
            LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
            queries.forEach(query -> result.put(query, MetricSample.available(MetricValue.integer(1),
                    providerGeneration, CLOCK.instant(), "test")));
            return CompletableFuture.completedFuture(Map.copyOf(result));
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(ID, "maddprestige", "phase5", "test", List.of(), List.of());
        }

        @Override
        public ProviderHealth health() {
            return health.get();
        }
    }
}
