package net.maddkraft.maddprestige.api.metric;

import java.util.Objects;
import java.util.Set;

public record MetricDimension(String id, boolean required, Set<String> allowedValues, String description) {
    public MetricDimension {
        id = Objects.requireNonNull(id, "dimension ID");
        allowedValues = Set.copyOf(Objects.requireNonNull(allowedValues, "allowed values"));
        description = Objects.requireNonNull(description, "description");
    }
}
