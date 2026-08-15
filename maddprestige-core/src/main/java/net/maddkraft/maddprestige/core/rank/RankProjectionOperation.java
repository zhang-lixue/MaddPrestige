package net.maddkraft.maddprestige.core.rank;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;

public record RankProjectionOperation(
        OperationPlan plan,
        RankProjectionRequest projectionRequest,
        PlayerStageState expectedPlayerState,
        StageId targetStage,
        boolean updateInternalStage) {
    public RankProjectionOperation {
        plan = Objects.requireNonNull(plan, "operation plan");
        projectionRequest = Objects.requireNonNull(projectionRequest, "projection request");
        expectedPlayerState = Objects.requireNonNull(expectedPlayerState, "expected player state");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        if (!plan.id().equals(projectionRequest.operationId())
                || !plan.configRevision().equals(projectionRequest.configRevision())
                || !plan.target().equals(projectionRequest.playerId())) {
            throw new IllegalArgumentException("Projection operation pinning fields must match its operation plan");
        }
    }
}
