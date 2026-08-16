package net.maddkraft.maddprestige.api.metric;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record MetricDescriptor(
        ProviderId providerId,
        MetricId metricId,
        MetricValueType valueType,
        Set<MetricOperator> supportedOperators,
        Set<MetricReadMode> supportedReads,
        boolean snapshotDeltaSupported,
        MetricMonotonicity monotonicity,
        MetricResetPolicy resetPolicy,
        Map<String, MetricDimension> dimensions,
        String displayName,
        String description,
        String unit,
        String reliability) {
    public MetricDescriptor {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
        valueType = Objects.requireNonNull(valueType, "value type");
        supportedOperators = Set.copyOf(Objects.requireNonNull(supportedOperators, "supported operators"));
        supportedReads = Set.copyOf(Objects.requireNonNull(supportedReads, "supported reads"));
        monotonicity = Objects.requireNonNull(monotonicity, "monotonicity");
        resetPolicy = Objects.requireNonNull(resetPolicy, "reset policy");
        dimensions = Map.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        displayName = Objects.requireNonNull(displayName, "display name");
        description = Objects.requireNonNull(description, "description");
        unit = Objects.requireNonNull(unit, "unit");
        reliability = Objects.requireNonNull(reliability, "reliability");
        if (supportedOperators.isEmpty()) {
            throw new IllegalArgumentException("Metric must support at least one operator");
        }
        if (!MetricOperator.compatibleWith(valueType).containsAll(supportedOperators)) {
            throw new IllegalArgumentException("Metric declares an operator incompatible with " + valueType);
        }
    }
}
