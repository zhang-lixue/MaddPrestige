package net.maddkraft.maddprestige.api.metric;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.MetricId;

public record MetricQuery(MetricId metricId, MetricReadMode readMode, Map<String, String> filters) {
    public MetricQuery {
        metricId = Objects.requireNonNull(metricId, "metric ID");
        readMode = Objects.requireNonNull(readMode, "read mode");
        filters = Map.copyOf(Objects.requireNonNull(filters, "filters"));
    }
}
