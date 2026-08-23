package net.maddkraft.maddprestige.api.provider;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValueType;

/**
 * Generation-free metadata for one requirement metric.
 *
 * @param metricId canonical metric identity
 * @param valueType exact value representation returned for the metric
 * @param supportedOperators immutable non-empty compatible operator set, at most 16
 * @param supportedReads immutable non-empty read-mode set, at most 16
 * @param snapshotDeltaSupported whether the provider supports durable snapshot deltas
 * @param monotonicity declared monotonic behavior
 * @param resetPolicy declared reset behavior
 * @param dimensions immutable map keyed by each dimension's exact ID, at most 16 normalized-unique entries
 * @param displayNameKey canonical localization key, 1-128 characters
 * @param descriptionKey canonical localization key, 1-128 characters
 * @param unitCode canonical machine unit code, 1-64 characters
 * @param reliabilityCode canonical machine reliability code, 1-64 characters
 */
@Stable
public record ProviderMetricDefinition(
        MetricId metricId,
        MetricValueType valueType,
        Set<MetricOperator> supportedOperators,
        Set<MetricReadMode> supportedReads,
        boolean snapshotDeltaSupported,
        MetricMonotonicity monotonicity,
        MetricResetPolicy resetPolicy,
        Map<String, ProviderMetricDimension> dimensions,
        String displayNameKey,
        String descriptionKey,
        String unitCode,
        String reliabilityCode) {
    public ProviderMetricDefinition {
        metricId = Objects.requireNonNull(metricId, "metric ID");
        valueType = Objects.requireNonNull(valueType, "value type");
        supportedOperators = Set.copyOf(Objects.requireNonNull(supportedOperators, "supported operators"));
        supportedReads = Set.copyOf(Objects.requireNonNull(supportedReads, "supported reads"));
        monotonicity = Objects.requireNonNull(monotonicity, "monotonicity");
        resetPolicy = Objects.requireNonNull(resetPolicy, "reset policy");
        dimensions = Map.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        displayNameKey = machineKey(displayNameKey, 128, "display name key");
        descriptionKey = machineKey(descriptionKey, 128, "description key");
        unitCode = machineKey(unitCode, 64, "unit code");
        reliabilityCode = machineKey(reliabilityCode, 64, "reliability code");
        if (supportedOperators.isEmpty() || supportedOperators.size() > 16 || supportedReads.isEmpty()
                || supportedReads.size() > 16 || dimensions.size() > 16
                || !MetricOperator.compatibleWith(valueType).containsAll(supportedOperators)
                || dimensions.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))) {
            throw new IllegalArgumentException("Metric definition is outside stable bounds");
        }
        HashSet<String> normalizedDimensions = new HashSet<>();
        if (dimensions.keySet().stream().map(ProviderMetricDefinition::normalized)
                .anyMatch(value -> !normalizedDimensions.add(value))) {
            throw new IllegalArgumentException("Metric dimensions are ambiguous after normalization");
        }
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is outside stable bounds");
        }
        return value;
    }

    private static String normalized(String value) {
        return value.replace('-', '_').replace('.', '_');
    }

    private static String machineKey(String value, int maximum, String name) {
        value = bounded(value, maximum, name);
        if (!value.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(name + " is outside the stable grammar");
        }
        return value;
    }
}
