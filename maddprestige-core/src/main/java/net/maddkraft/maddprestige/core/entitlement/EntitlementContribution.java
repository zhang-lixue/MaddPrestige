package net.maddkraft.maddprestige.core.entitlement;

import java.util.Objects;

public record EntitlementContribution(String sourceId, int priority, EntitlementValue value) {
    public EntitlementContribution {
        sourceId = Objects.requireNonNull(sourceId, "source ID");
        value = Objects.requireNonNull(value, "value");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("Entitlement source ID cannot be blank");
        }
    }
}
