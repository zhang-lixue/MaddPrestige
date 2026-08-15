package net.maddkraft.maddprestige.core.rank;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;

public record RankReconciliationResult(
        ReconciliationStatus status,
        String detail,
        Optional<OperationId> operationId,
        Optional<StageId> stageId) {
    public RankReconciliationResult {
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
        operationId = Objects.requireNonNull(operationId, "operation ID");
        stageId = Objects.requireNonNull(stageId, "stage ID");
    }
}
