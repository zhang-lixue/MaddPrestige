package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record RequirementTarget(MetricValue lower, Optional<MetricValue> upper) {
    public RequirementTarget {
        lower = Objects.requireNonNull(lower, "lower target");
        upper = Objects.requireNonNull(upper, "upper target");
        if (upper.isPresent()) {
            MetricValue value = upper.orElseThrow();
            if (value.type() != lower.type()) {
                throw new IllegalArgumentException("Range target values must have the same type");
            }
            if (lower.compareTo(value) > 0) {
                throw new IllegalArgumentException("Range lower target cannot exceed upper target");
            }
        }
    }

    public static RequirementTarget single(MetricValue value) {
        return new RequirementTarget(value, Optional.empty());
    }

    public static RequirementTarget range(MetricValue lower, MetricValue upper) {
        return new RequirementTarget(lower, Optional.of(upper));
    }
}
