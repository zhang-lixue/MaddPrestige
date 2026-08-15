package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.ActionState;

public record StoredOperationAction(
        OperationId operationId,
        String actionId,
        ActionState state,
        Optional<String> failureReason,
        Instant updatedAt) {
    public StoredOperationAction {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        actionId = Objects.requireNonNull(actionId, "action ID");
        state = Objects.requireNonNull(state, "state");
        failureReason = Objects.requireNonNull(failureReason, "failure reason");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
    }
}
