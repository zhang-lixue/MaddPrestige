package net.maddkraft.maddprestige.persistence;

import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.operation.ActionState;

public interface OperationRepository {
    void insertPrepared(OperationPlan plan);

    Optional<StoredOperation> find(OperationId operationId);

    Optional<StoredOperation> findByIdempotency(String operationType, UUID target, String idempotencyKey);

    Optional<StoredOperationAction> findAction(OperationId operationId, String actionId);

    void transition(OperationId operationId, OperationState expected, OperationState replacement);

    void transitionAction(
            OperationId operationId,
            String actionId,
            ActionState expected,
            ActionState replacement,
            Optional<String> failureReason);
}
