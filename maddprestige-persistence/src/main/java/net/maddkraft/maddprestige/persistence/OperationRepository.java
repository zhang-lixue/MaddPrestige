package net.maddkraft.maddprestige.persistence;

import java.util.Optional;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.operation.OperationState;

public interface OperationRepository {
    void insertPrepared(OperationPlan plan);

    Optional<StoredOperation> find(OperationId operationId);

    void transition(OperationId operationId, OperationState expected, OperationState replacement);
}
