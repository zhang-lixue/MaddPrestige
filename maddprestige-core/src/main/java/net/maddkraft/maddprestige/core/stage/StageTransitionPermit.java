package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageTransitionPermit(
        OperationId operationId,
        StageId sourceStage,
        StageId targetStage,
        ConfigRevisionId configurationRevision,
        UUID leaseToken) {
    public StageTransitionPermit {
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(sourceStage, "source stage");
        Objects.requireNonNull(targetStage, "target stage");
        Objects.requireNonNull(configurationRevision, "configuration revision");
        Objects.requireNonNull(leaseToken, "lease token");
    }
}
