package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;

public record PrestigeHistoryRecord(
        UUID playerId,
        OperationId operationId,
        String eventType,
        StageId sourceStage,
        StageId resetStage,
        long currentBefore,
        long currentAfter,
        long lifetimeBefore,
        long lifetimeAfter,
        String result,
        String costsSnapshot,
        String rewardsSnapshot,
        ConfigRevisionId configRevision,
        Instant occurredAt) {
    public PrestigeHistoryRecord {
        playerId = Objects.requireNonNull(playerId, "player ID");
        operationId = Objects.requireNonNull(operationId, "operation ID");
        eventType = Objects.requireNonNull(eventType, "event type");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        resetStage = Objects.requireNonNull(resetStage, "reset stage");
        result = Objects.requireNonNull(result, "result");
        costsSnapshot = Objects.requireNonNull(costsSnapshot, "cost snapshot");
        rewardsSnapshot = Objects.requireNonNull(rewardsSnapshot, "reward snapshot");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        occurredAt = Objects.requireNonNull(occurredAt, "occurred at");
    }

    public PrestigeHistoryRecord(
            UUID playerId,
            OperationId operationId,
            StageId sourceStage,
            StageId resetStage,
            long currentBefore,
            long currentAfter,
            long lifetimeBefore,
            long lifetimeAfter,
            String result,
            String costsSnapshot,
            String rewardsSnapshot,
            ConfigRevisionId configRevision,
            Instant occurredAt) {
        this(playerId, operationId, "STATE_COMMITTED", sourceStage, resetStage, currentBefore, currentAfter,
                lifetimeBefore, lifetimeAfter, result, costsSnapshot, rewardsSnapshot, configRevision, occurredAt);
    }
}
