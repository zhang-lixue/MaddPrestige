package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.OperationState;

public record RecoveryEvent(
        UUID recoveryId,
        OperationId operationId,
        OperationState previousState,
        OperationState resultingState,
        String decision,
        String detail,
        Instant occurredAt) {
    public RecoveryEvent {
        recoveryId = Objects.requireNonNull(recoveryId, "recovery ID");
        operationId = Objects.requireNonNull(operationId, "operation ID");
        previousState = Objects.requireNonNull(previousState, "previous state");
        resultingState = Objects.requireNonNull(resultingState, "resulting state");
        decision = Objects.requireNonNull(decision, "decision");
        detail = Objects.requireNonNull(detail, "detail");
        occurredAt = Objects.requireNonNull(occurredAt, "occurred at");
    }
}
