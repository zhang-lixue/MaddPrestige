package net.maddkraft.maddprestige.core.plan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RankUpAuthorizationResult(Optional<RankUpPlan> plan, List<String> blockers) {
    public RankUpAuthorizationResult {
        plan = Objects.requireNonNull(plan, "plan");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        if (plan.isPresent() && !blockers.isEmpty()) {
            throw new IllegalArgumentException("A canonical plan and authorization-boundary blockers cannot coexist");
        }
    }

    public static RankUpAuthorizationResult rejected(String blocker) {
        return new RankUpAuthorizationResult(Optional.empty(), List.of(blocker));
    }
}
