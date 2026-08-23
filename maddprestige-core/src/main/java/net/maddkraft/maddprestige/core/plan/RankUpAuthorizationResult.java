package net.maddkraft.maddprestige.core.plan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

public record RankUpAuthorizationResult(
        Optional<RankUpPlan> plan,
        List<String> blockers,
        List<AuthorizationBlocker> authorizationBlockers) {
    public RankUpAuthorizationResult {
        plan = Objects.requireNonNull(plan, "plan");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        authorizationBlockers = List.copyOf(Objects.requireNonNull(authorizationBlockers,
                "authorization blockers"));
        if (plan.isPresent() && !blockers.isEmpty()) {
            throw new IllegalArgumentException("A canonical plan and authorization-boundary blockers cannot coexist");
        }
        if (!authorizationBlockers.isEmpty()
                && !blockers.equals(AuthorizationBlocker.diagnostics(authorizationBlockers))) {
            throw new IllegalArgumentException("Diagnostic and structured authorization blockers must agree");
        }
    }

    public RankUpAuthorizationResult(Optional<RankUpPlan> plan, List<String> blockers) {
        this(plan, blockers, AuthorizationBlocker.unknownAll(blockers));
    }

    public static RankUpAuthorizationResult rejected(String blocker) {
        return new RankUpAuthorizationResult(Optional.empty(), List.of(blocker));
    }

    public static RankUpAuthorizationResult rejected(AuthorizationBlocker blocker) {
        return rejected(List.of(blocker));
    }

    public static RankUpAuthorizationResult rejected(List<AuthorizationBlocker> blockers) {
        return new RankUpAuthorizationResult(Optional.empty(), AuthorizationBlocker.diagnostics(blockers), blockers);
    }
}
