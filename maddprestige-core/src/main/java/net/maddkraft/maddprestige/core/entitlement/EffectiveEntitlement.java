package net.maddkraft.maddprestige.core.entitlement;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.EntitlementId;

public record EffectiveEntitlement(
        EntitlementId id,
        EntitlementValue value,
        List<EntitlementContribution> orderedContributions,
        List<String> effectiveSources,
        String explanation) {
    public EffectiveEntitlement {
        id = Objects.requireNonNull(id, "entitlement ID");
        value = Objects.requireNonNull(value, "value");
        orderedContributions = List.copyOf(Objects.requireNonNull(orderedContributions, "contributions"));
        effectiveSources = List.copyOf(Objects.requireNonNull(effectiveSources, "effective sources"));
        explanation = Objects.requireNonNull(explanation, "explanation");
    }
}
