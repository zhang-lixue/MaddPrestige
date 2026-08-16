package net.maddkraft.maddprestige.testkit;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;

public final class FakeProgressionProvider extends FakeProvider implements MetricProvider {
    private final Map<MetricKey, MetricValue> metrics = new ConcurrentHashMap<>();
    private final Map<MetricId, MetricDescriptor> descriptors = new ConcurrentHashMap<>();

    public FakeProgressionProvider() {
        super(new ProviderId("fake_progression"), "progression",
                List.of(new CapabilityDescriptor("typed-metrics", "progression", "Fake exact progression metrics", Map.of())));
        register(new MetricDescriptor(descriptor().id(), new MetricId("value"), MetricValueType.EXACT_DECIMAL,
                MetricOperator.compatibleWith(MetricValueType.EXACT_DECIMAL),
                Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), true, MetricMonotonicity.MONOTONIC,
                MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Value", "Fake exact value", "", "test"));
    }

    public void set(UUID player, MetricId metric, ExactDecimal value) {
        metrics.put(new MetricKey(player, metric), MetricValue.decimal(value.toString()));
    }

    public void set(UUID player, MetricId metric, MetricValue value) {
        metrics.put(new MetricKey(player, metric), value);
    }

    public void register(MetricDescriptor descriptor) {
        if (!descriptor.providerId().equals(descriptor().id())) {
            throw new IllegalArgumentException("Fake metric provider ID differs");
        }
        if (descriptors.putIfAbsent(descriptor.metricId(), descriptor) != null) {
            throw new IllegalArgumentException("Fake metric already registered");
        }
    }

    public Result<ExactDecimal> sample(UUID player, MetricId metric) {
        ProviderHealthState state = health().state();
        if (state != ProviderHealthState.AVAILABLE && state != ProviderHealthState.ACTIVE) {
            return Result.failure(StructuredError.unavailable("provider.metric.unavailable",
                    "Fake progression provider is " + state));
        }
        MetricValue value = metrics.get(new MetricKey(player, metric));
        if (value == null) {
            return Result.failure(StructuredError.unavailable("provider.metric.missing", "Metric has no fake sample"));
        }
        return Result.success(ExactDecimal.parse(value.canonical()));
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return List.copyOf(descriptors.values());
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
        for (MetricQuery query : queries) {
            MetricValue value = metrics.get(new MetricKey(playerId, query.metricId()));
            if (value == null || !descriptors.containsKey(query.metricId())) {
                result.put(query, MetricSample.unavailable(providerGeneration, Instant.EPOCH, "fake",
                        "Metric has no fake sample"));
            } else {
                result.put(query, MetricSample.available(value, providerGeneration, Instant.EPOCH, "fake"));
            }
        }
        return CompletableFuture.completedFuture(Map.copyOf(result));
    }

    private record MetricKey(UUID player, MetricId metric) {
    }
}
