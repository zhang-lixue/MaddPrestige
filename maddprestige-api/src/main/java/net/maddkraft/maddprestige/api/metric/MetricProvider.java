package net.maddkraft.maddprestige.api.metric;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.provider.Provider;

public interface MetricProvider extends Provider {
    Collection<MetricDescriptor> metrics();

    default MetricDescriptor requireMetric(MetricId metricId) {
        return metrics().stream().filter(metric -> metric.metricId().equals(metricId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown metric: " + metricId.value()));
    }

    CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration);
}
