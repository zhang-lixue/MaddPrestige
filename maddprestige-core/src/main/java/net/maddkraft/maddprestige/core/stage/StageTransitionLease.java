package net.maddkraft.maddprestige.core.stage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.OperationState;

/** Durable diagnostic view of one journal-owned source-and-target transition lease. */
public record StageTransitionLease(
        OperationId operationId,
        Optional<StageId> sourceStage,
        StageId targetStage,
        ConfigRevisionId configurationRevision,
        Optional<OperationState> ownerState,
        boolean participationComplete,
        Instant acquiredAt) {
    public StageTransitionLease {
        Objects.requireNonNull(operationId, "operation id");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        Objects.requireNonNull(targetStage, "target stage");
        Objects.requireNonNull(configurationRevision, "configuration revision");
        ownerState = Objects.requireNonNull(ownerState, "owner state");
        Objects.requireNonNull(acquiredAt, "acquired at");
        if (participationComplete && sourceStage.isEmpty()) {
            throw new IllegalArgumentException("Complete participation requires a source stage");
        }
    }

    public boolean abnormal() {
        return !participationComplete || ownerState.isEmpty()
                || ownerState.filter(StageTransitionLease::terminal).isPresent();
    }

    private static boolean terminal(OperationState state) {
        return state == OperationState.COMPLETED || state == OperationState.COMPENSATED
                || state == OperationState.FAILED;
    }
}
