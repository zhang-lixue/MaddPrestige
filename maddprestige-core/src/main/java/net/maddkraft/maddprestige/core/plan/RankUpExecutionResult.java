package net.maddkraft.maddprestige.core.plan;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;

public record RankUpExecutionResult(OperationId operationId, RankUpExecutionStatus status, String detail) {
    public RankUpExecutionResult {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
