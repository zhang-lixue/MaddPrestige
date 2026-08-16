package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.ActionState;

public record StoredOperationAction(
        OperationId operationId,
        String actionId,
        ProviderId providerId,
        String actionType,
        boolean reversible,
        boolean idempotent,
        ActionState state,
        Optional<String> failureReason,
        Instant updatedAt) {
    public StoredOperationAction {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        actionId = Objects.requireNonNull(actionId, "action ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        actionType = Objects.requireNonNull(actionType, "action type");
        state = Objects.requireNonNull(state, "state");
        failureReason = Objects.requireNonNull(failureReason, "failure reason");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
        if (actionId.isBlank() || actionType.isBlank()) {
            throw new IllegalArgumentException("Stored action ID/type cannot be blank");
        }
    }
}
