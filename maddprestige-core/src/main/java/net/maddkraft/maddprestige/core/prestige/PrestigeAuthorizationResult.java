package net.maddkraft.maddprestige.core.prestige;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

public record PrestigeAuthorizationResult(
        Optional<PrestigePlan> plan,
        Optional<String> rejection,
        List<AuthorizationBlocker> authorizationBlockers) {
    public PrestigeAuthorizationResult {
        plan = Objects.requireNonNull(plan, "plan");
        rejection = Objects.requireNonNull(rejection, "rejection");
        authorizationBlockers = List.copyOf(Objects.requireNonNull(authorizationBlockers,
                "authorization blockers"));
        if (plan.isPresent() == rejection.isPresent()) {
            throw new IllegalArgumentException("Authorization result must contain exactly one outcome");
        }
        if (plan.isPresent() && !authorizationBlockers.isEmpty()) {
            throw new IllegalArgumentException("A planned result cannot carry boundary blockers");
        }
        if (!authorizationBlockers.isEmpty()
                && !rejection.orElseThrow().equals(String.join("; ",
                        AuthorizationBlocker.diagnostics(authorizationBlockers)))) {
            throw new IllegalArgumentException("Diagnostic and structured authorization blockers must agree");
        }
    }

    public PrestigeAuthorizationResult(Optional<PrestigePlan> plan, Optional<String> rejection) {
        this(plan, rejection, rejection.map(value -> List.of(AuthorizationBlocker.unknown(value))).orElse(List.of()));
    }

    public static PrestigeAuthorizationResult planned(PrestigePlan plan) {
        return new PrestigeAuthorizationResult(Optional.of(plan), Optional.empty(), List.of());
    }

    public static PrestigeAuthorizationResult rejected(String reason) {
        return new PrestigeAuthorizationResult(Optional.empty(), Optional.of(reason));
    }

    public static PrestigeAuthorizationResult rejected(AuthorizationBlocker blocker) {
        return rejected(List.of(blocker));
    }

    public static PrestigeAuthorizationResult rejected(List<AuthorizationBlocker> blockers) {
        String diagnostic = String.join("; ", AuthorizationBlocker.diagnostics(blockers));
        return new PrestigeAuthorizationResult(Optional.empty(), Optional.of(diagnostic), blockers);
    }
}
