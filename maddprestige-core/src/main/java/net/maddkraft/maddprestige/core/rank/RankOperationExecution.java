package net.maddkraft.maddprestige.core.rank;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;

public record RankOperationExecution(
        OperationId operationId,
        RankOperationExecutionStatus status,
        String detail) {
    public RankOperationExecution {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
