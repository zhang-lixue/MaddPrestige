package net.maddkraft.maddprestige.core.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequirementMetricCollectorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A09][A63] One provider batch serves repeated leaves and absent generation pins fail closed")
    void batchesByProviderAndRequiresExplicitPin() {
        ProviderRegistry registry = new ProviderRegistry();
        CountingMetricProvider provider = new CountingMetricProvider();
        var registration = registry.register("test-owner", provider);
        registry.activate(registration);
        RequirementLeaf first = leaf("first");
        RequirementLeaf second = leaf("second");
        RequirementNode tree = RequirementGroup.all(new RequirementId("all"), List.of(
                RequirementChild.unweighted(first), RequirementChild.unweighted(second)));
        RequirementMetricCollector collector = new RequirementMetricCollector(registry, CLOCK);

        var samples = collector.collect(UUID.randomUUID(), tree,
                Map.of(provider.descriptor().id(), registration.generation())).toCompletableFuture().join();
        assertEquals(1, provider.reads.get());
        assertEquals(1, provider.queries.get(), "identical leaf queries must be coalesced");
        assertEquals(2, samples.size());
        assertTrue(samples.values().stream().allMatch(sample -> sample.status() == MetricSampleStatus.AVAILABLE));

        var unpinned = collector.collect(UUID.randomUUID(), tree, Map.of()).toCompletableFuture().join();
        assertEquals(1, provider.reads.get(), "missing generation pin must not contact the provider");
        assertTrue(unpinned.values().stream().allMatch(sample -> sample.status() == MetricSampleStatus.UNAVAILABLE));
    }

    @Test
    @DisplayName("[A09][A49] Synchronous and exceptional metric provider failures normalize to UNAVAILABLE")
    void normalizesBothMetricFailureShapes() {
        for (FailureMode mode : List.of(FailureMode.SYNCHRONOUS, FailureMode.ASYNCHRONOUS)) {
            ProviderRegistry registry = new ProviderRegistry();
            CountingMetricProvider provider = new CountingMetricProvider();
            provider.failureMode = mode;
            var registration = registry.register("test-owner", provider);
            registry.activate(registration);

            Map<RequirementId, MetricSample> samples = new RequirementMetricCollector(registry, CLOCK)
                    .collect(UUID.randomUUID(), leaf("failure_" + mode.name().toLowerCase()),
                            Map.of(CountingMetricProvider.ID, registration.generation()))
                    .toCompletableFuture().join();

            MetricSample sample = samples.values().iterator().next();
            assertEquals(MetricSampleStatus.UNAVAILABLE, sample.status(), mode.name());
            assertEquals(registration.generation(), sample.providerGeneration(), mode.name());
            assertEquals(CountingMetricProvider.ID.value(), sample.provenance(), mode.name());
        }
    }

    private static RequirementLeaf leaf(String id) {
        return new RequirementLeaf(RequirementDefinition.create(new RequirementId(id),
                CountingMetricProvider.ID, CountingMetricProvider.METRIC, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.count(1)), MeasurementScope.ABSOLUTE, CompletionMode.LIVE,
                ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false));
    }

    private static final class CountingMetricProvider implements MetricProvider {
        private static final ProviderId ID = new ProviderId("batch-metrics");
        private static final MetricId METRIC = new MetricId("count");
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger queries = new AtomicInteger();
        private final MetricDescriptor metric = new MetricDescriptor(ID, METRIC, MetricValueType.COUNT,
                MetricOperator.compatibleWith(MetricValueType.COUNT), Set.of(MetricReadMode.CURRENT), true,
                MetricMonotonicity.MONOTONIC, MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Count", "Count",
                "count", "test");
        private FailureMode failureMode = FailureMode.NONE;

        @Override
        public Collection<MetricDescriptor> metrics() {
            return List.of(metric);
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId, List<MetricQuery> requested, long providerGeneration) {
            reads.incrementAndGet();
            if (failureMode == FailureMode.SYNCHRONOUS) {
                throw new IllegalStateException("synchronous outage");
            }
            if (failureMode == FailureMode.ASYNCHRONOUS) {
                return CompletableFuture.failedFuture(new IllegalStateException("asynchronous outage"));
            }
            queries.addAndGet(requested.size());
            LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
            requested.forEach(query -> result.put(query,
                    MetricSample.available(MetricValue.count(1), providerGeneration, CLOCK.instant(), "test")));
            return CompletableFuture.completedFuture(Map.copyOf(result));
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(ID, "test-owner", "1", "1", List.of(), List.of());
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "ready", "ready", CLOCK.instant());
        }
    }

    private enum FailureMode {
        NONE,
        SYNCHRONOUS,
        ASYNCHRONOUS
    }
}
