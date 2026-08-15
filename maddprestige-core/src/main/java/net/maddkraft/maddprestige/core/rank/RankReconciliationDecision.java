package net.maddkraft.maddprestige.core.rank;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

public record RankReconciliationDecision(
        ReconciliationStatus status,
        ReconciliationAction action,
        Optional<StageId> targetStage,
        String reason) {
    public RankReconciliationDecision {
        status = Objects.requireNonNull(status, "status");
        action = Objects.requireNonNull(action, "action");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
