package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;

public record PrestigeExecutionResult(
        OperationId operationId,
        PrestigeExecutionStatus status,
        String detail) {
    public PrestigeExecutionResult {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
