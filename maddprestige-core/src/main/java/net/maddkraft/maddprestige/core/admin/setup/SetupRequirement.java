package net.maddkraft.maddprestige.core.admin.setup;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;

public record SetupRequirement(
        RequirementId id,
        ProviderId providerId,
        MetricId metricId,
        String operator,
        String target,
        String scope,
        String completion) {
    public SetupRequirement {
        id = Objects.requireNonNull(id, "requirement ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
        operator = bounded(operator, "operator");
        target = bounded(target, "target");
        scope = bounded(scope, "scope");
        completion = bounded(completion, "completion");
    }

    private static String bounded(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Setup " + label + " must contain 1-128 printable characters");
        }
        return value;
    }
}
