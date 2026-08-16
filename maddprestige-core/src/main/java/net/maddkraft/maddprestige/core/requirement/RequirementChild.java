package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record RequirementChild(RequirementNode node, ExactDecimal weight) {
    public RequirementChild {
        node = Objects.requireNonNull(node, "node");
        weight = Objects.requireNonNull(weight, "weight");
        if (weight.asBigDecimal().signum() < 0) {
            throw new IllegalArgumentException("Requirement child weight cannot be negative");
        }
    }

    public static RequirementChild unweighted(RequirementNode node) {
        return new RequirementChild(node, ExactDecimal.parse("1"));
    }
}
