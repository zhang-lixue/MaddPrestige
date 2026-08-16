package net.maddkraft.maddprestige.persistence.recovery;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.OperationState;

public record RecoveryOutcome(
        OperationId operationId,
        OperationState state,
        String decision,
        String detail) {
    public RecoveryOutcome {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        state = Objects.requireNonNull(state, "state");
        decision = Objects.requireNonNull(decision, "decision");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
