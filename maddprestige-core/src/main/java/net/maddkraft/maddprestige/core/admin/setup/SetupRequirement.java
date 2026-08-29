package net.maddkraft.maddprestige.core.admin.setup;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricValueType;

public record SetupRequirement(
        RequirementId id,
        ProviderId providerId,
        MetricId metricId,
        String operator,
        String target,
        String scope,
        String completion,
        Optional<MetricValueType> valueType) {
    public SetupRequirement(
            RequirementId id,
            ProviderId providerId,
            MetricId metricId,
            String operator,
            String target,
            String scope,
            String completion) {
        this(id, providerId, metricId, operator, target, scope, completion, Optional.empty());
    }

    public SetupRequirement {
        id = Objects.requireNonNull(id, "requirement ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
        operator = bounded(operator, "operator");
        target = bounded(target, "target");
        scope = bounded(scope, "scope");
        completion = bounded(completion, "completion");
        valueType = Objects.requireNonNull(valueType, "value type");
    }

    private static String bounded(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Setup " + label + " must contain 1-128 printable characters");
        }
        return value;
    }
}
