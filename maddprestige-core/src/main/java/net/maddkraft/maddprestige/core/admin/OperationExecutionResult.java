package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;

public record OperationExecutionResult(OperationId operationId, String status, String detail) {
    public OperationExecutionResult {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
