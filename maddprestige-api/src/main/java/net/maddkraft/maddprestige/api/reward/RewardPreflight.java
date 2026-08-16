package net.maddkraft.maddprestige.api.reward;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.action.PreflightStatus;

public record RewardPreflight(PreflightStatus status, Optional<PlannedReward> plannedReward, String detail) {
    public RewardPreflight {
        status = Objects.requireNonNull(status, "preflight status");
        plannedReward = Objects.requireNonNull(plannedReward, "planned reward");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == PreflightStatus.READY) != plannedReward.isPresent()) {
            throw new IllegalArgumentException("Only ready reward preflight carries a plan");
        }
    }

    public static RewardPreflight ready(PlannedReward plannedReward) {
        return new RewardPreflight(PreflightStatus.READY, Optional.of(plannedReward), "ready");
    }

    public static RewardPreflight unavailable(String detail) {
        return new RewardPreflight(PreflightStatus.UNAVAILABLE, Optional.empty(), detail);
    }

    public static RewardPreflight invalid(String detail) {
        return new RewardPreflight(PreflightStatus.INVALID, Optional.empty(), detail);
    }
}
