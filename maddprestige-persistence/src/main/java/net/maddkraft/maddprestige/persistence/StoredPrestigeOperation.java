package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;

public record StoredPrestigeOperation(
        OperationId operationId,
        StageId sourceStage,
        StageId resetStage,
        long expectedPrestigeRevision,
        long currentBefore,
        long currentAfter,
        long lifetimeBefore,
        long lifetimeAfter,
        ScopeId scopeBefore,
        ScopeId scopeAfter,
        ConfigRevisionId stageProvenance,
        ConfigRevisionId prestigeProvenance,
        Instant plannedAt) {
    public StoredPrestigeOperation {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        resetStage = Objects.requireNonNull(resetStage, "reset stage");
        scopeBefore = Objects.requireNonNull(scopeBefore, "scope before");
        scopeAfter = Objects.requireNonNull(scopeAfter, "scope after");
        stageProvenance = Objects.requireNonNull(stageProvenance, "stage provenance");
        prestigeProvenance = Objects.requireNonNull(prestigeProvenance, "Prestige provenance");
        plannedAt = Objects.requireNonNull(plannedAt, "planned at");
    }
}
