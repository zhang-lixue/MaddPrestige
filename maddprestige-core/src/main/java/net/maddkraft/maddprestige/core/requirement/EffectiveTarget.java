package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;

public record EffectiveTarget(RequirementTarget target, String formula) {
    public EffectiveTarget {
        target = Objects.requireNonNull(target, "target");
        formula = Objects.requireNonNull(formula, "formula");
    }
}
