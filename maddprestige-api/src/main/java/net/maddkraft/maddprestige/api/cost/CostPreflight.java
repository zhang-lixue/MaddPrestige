package net.maddkraft.maddprestige.api.cost;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.action.PreflightStatus;

public record CostPreflight(PreflightStatus status, Optional<PlannedCost> plannedCost, String detail) {
    public CostPreflight {
        status = Objects.requireNonNull(status, "preflight status");
        plannedCost = Objects.requireNonNull(plannedCost, "planned cost");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == PreflightStatus.READY) != plannedCost.isPresent()) {
            throw new IllegalArgumentException("Only ready cost preflight carries a plan");
        }
    }

    public static CostPreflight ready(PlannedCost plannedCost) {
        return new CostPreflight(PreflightStatus.READY, Optional.of(plannedCost), "ready");
    }

    public static CostPreflight blocked(String detail) {
        return new CostPreflight(PreflightStatus.BLOCKED, Optional.empty(), detail);
    }

    public static CostPreflight unavailable(String detail) {
        return new CostPreflight(PreflightStatus.UNAVAILABLE, Optional.empty(), detail);
    }
}
