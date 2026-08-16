package net.maddkraft.maddprestige.core.manual;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricValueType;

public record ManualCounterDefinition(
        MetricId metricId,
        MetricValueType valueType,
        boolean incrementAllowed,
        boolean exactSetAllowed,
        String displayName,
        String description,
        Map<String, String> metadata) {
    public ManualCounterDefinition {
        metricId = Objects.requireNonNull(metricId, "metric ID");
        valueType = Objects.requireNonNull(valueType, "value type");
        displayName = Objects.requireNonNull(displayName, "display name");
        description = Objects.requireNonNull(description, "description");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        if (!incrementAllowed && !exactSetAllowed) {
            throw new IllegalArgumentException("Manual counter must support increment or exact set");
        }
        if (incrementAllowed && !valueType.isNumeric()) {
            throw new IllegalArgumentException("Only numeric manual counters can be incremented");
        }
    }
}
