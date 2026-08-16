package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import java.util.Optional;

public record PrestigeAuthorizationResult(Optional<PrestigePlan> plan, Optional<String> rejection) {
    public PrestigeAuthorizationResult {
        plan = Objects.requireNonNull(plan, "plan");
        rejection = Objects.requireNonNull(rejection, "rejection");
        if (plan.isPresent() == rejection.isPresent()) {
            throw new IllegalArgumentException("Authorization result must contain exactly one outcome");
        }
    }

    public static PrestigeAuthorizationResult planned(PrestigePlan plan) {
        return new PrestigeAuthorizationResult(Optional.of(plan), Optional.empty());
    }

    public static PrestigeAuthorizationResult rejected(String reason) {
        return new PrestigeAuthorizationResult(Optional.empty(), Optional.of(reason));
    }
}
