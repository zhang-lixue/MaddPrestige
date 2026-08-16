package net.maddkraft.maddprestige.core.requirement;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class RequirementCompletionTransitionService {
    private final RequirementStateWriter states;
    private final Clock clock;

    public RequirementCompletionTransitionService(RequirementStateWriter states, Clock clock) {
        this.states = Objects.requireNonNull(states, "requirement state writer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<RequirementLatch> persistEligible(RequirementEvaluationResult result) {
        return result.eligibleLatches().stream()
                .map(key -> states.recordLatch(new RequirementLatch(key, clock.instant())))
                .toList();
    }
}
