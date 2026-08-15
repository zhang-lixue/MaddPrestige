package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.OperationState;

public record StoredOperation(
        OperationId operationId,
        String operationType,
        UUID target,
        String idempotencyKey,
        OperationState state,
        Instant createdAt,
        Instant updatedAt) {
    public StoredOperation {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        operationType = Objects.requireNonNull(operationType, "operation type");
        target = Objects.requireNonNull(target, "target");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key");
        state = Objects.requireNonNull(state, "state");
        createdAt = Objects.requireNonNull(createdAt, "created at");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
    }
}
