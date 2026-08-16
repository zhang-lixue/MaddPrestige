package net.maddkraft.maddprestige.core.entitlement;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.EntitlementId;

public record EntitlementDefinition(
        EntitlementId id,
        EntitlementValueType type,
        EntitlementMergeStrategy strategy) {
    public EntitlementDefinition {
        id = Objects.requireNonNull(id, "entitlement ID");
        type = Objects.requireNonNull(type, "value type");
        strategy = Objects.requireNonNull(strategy, "merge strategy");
        if (strategy == EntitlementMergeStrategy.BOOLEAN_OR && type != EntitlementValueType.BOOLEAN) {
            throw new IllegalArgumentException("BOOLEAN_OR requires BOOLEAN values");
        }
        if (strategy != EntitlementMergeStrategy.BOOLEAN_OR && type == EntitlementValueType.BOOLEAN
                && strategy != EntitlementMergeStrategy.OVERRIDE) {
            throw new IllegalArgumentException("Boolean values support BOOLEAN_OR or OVERRIDE only");
        }
    }
}
