package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;

public record StageHistoryRecord(
        UUID playerId,
        StageId stageId,
        Instant enteredAt,
        OperationId operationId,
        Actor actor,
        String reason,
        ConfigRevisionId configRevision) {
    public StageHistoryRecord {
        playerId = Objects.requireNonNull(playerId, "player ID");
        stageId = Objects.requireNonNull(stageId, "stage ID");
        enteredAt = Objects.requireNonNull(enteredAt, "entered at");
        operationId = Objects.requireNonNull(operationId, "operation ID");
        actor = Objects.requireNonNull(actor, "actor");
        reason = Objects.requireNonNull(reason, "reason");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Stage-history reason cannot be blank");
        }
    }
}
