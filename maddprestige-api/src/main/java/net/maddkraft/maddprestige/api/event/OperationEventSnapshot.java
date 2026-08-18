package net.maddkraft.maddprestige.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationStatus;

/**
 * Immutable lifecycle payload for one authorized progression operation.
 *
 * @param correlationId request identity allocated before PRE delivery; never a journal key
 * @param durableOperationId empty for PRE and required for POST
 * @param playerId player whose progression is being evaluated or was mutated
 * @param kind operation kind
 * @param sourceStage logical source stage used by authorization, including the canonical virtual initial stage for
 *                    an unmaterialized player; empty only when authorization has no source stage
 * @param targetStage resolved target stage, or empty when no target exists
 * @param configRevision exact authoritative revision used for authorization
 * @param terminalStatus empty for PRE and required for POST
 * @param occurredAt non-null delivery snapshot time
 */
public record OperationEventSnapshot(
        UUID correlationId,
        Optional<OperationId> durableOperationId,
        UUID playerId,
        OperationKind kind,
        Optional<StageId> sourceStage,
        Optional<StageId> targetStage,
        ConfigRevisionId configRevision,
        Optional<OperationStatus> terminalStatus,
        Instant occurredAt) {
    public OperationEventSnapshot {
        correlationId = Objects.requireNonNull(correlationId, "correlation ID");
        durableOperationId = Objects.requireNonNull(durableOperationId, "durable operation ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        kind = Objects.requireNonNull(kind, "operation kind");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        terminalStatus = Objects.requireNonNull(terminalStatus, "terminal status");
        occurredAt = Objects.requireNonNull(occurredAt, "occurrence time");
        if (terminalStatus.isEmpty() && durableOperationId.isPresent()) {
            throw new IllegalArgumentException("A PRE event cannot claim a durable operation identity");
        }
        if (terminalStatus.isPresent() && durableOperationId.isEmpty()) {
            throw new IllegalArgumentException("A POST event requires its durable operation identity");
        }
    }
}
