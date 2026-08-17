package net.maddkraft.maddprestige.testkit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;
import net.maddkraft.maddprestige.core.stage.StageTransitionFence;
import net.maddkraft.maddprestige.core.stage.StageTransitionLease;
import net.maddkraft.maddprestige.core.stage.StageTransitionPermit;

/** Controllable test authority; production paths use the SQLite durable implementation. */
public final class InMemoryStageTransitionFence implements StageTransitionFence {
    private final Set<StageId> fencedStages = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<OperationId, StageTransitionPermit> leases = new ConcurrentHashMap<>();

    public void fence(StageId stageId) {
        fencedStages.add(stageId);
    }

    public void unfence(StageId stageId) {
        fencedStages.remove(stageId);
    }

    @Override
    public StageTransitionPermit acquire(
            OperationId operationId,
            StageId sourceStage,
            StageId targetStage,
            ConfigRevisionId configurationRevision,
            Instant acquiredAt) {
        if (fencedStages.contains(sourceStage) || fencedStages.contains(targetStage)) {
            throw new StageTransitionBlockedException("Test fence blocks source/target participation "
                    + sourceStage.value() + " → " + targetStage.value());
        }
        StageTransitionPermit candidate = new StageTransitionPermit(
                operationId, sourceStage, targetStage, configurationRevision, UUID.randomUUID());
        StageTransitionPermit existing = leases.putIfAbsent(operationId, candidate);
        if (existing == null) {
            return candidate;
        }
        if (!existing.sourceStage().equals(sourceStage) || !existing.targetStage().equals(targetStage)
                || !existing.configurationRevision().equals(configurationRevision)) {
            throw new StageTransitionBlockedException("Operation owns a different test lease");
        }
        return existing;
    }

    @Override
    public void release(StageTransitionPermit permit, Instant releasedAt) {
        leases.remove(permit.operationId(), permit);
    }

    @Override
    public void release(OperationId operationId, Instant releasedAt) {
        leases.remove(operationId);
    }

    @Override
    public int releaseTerminalLeases(Instant releasedAt) {
        return 0;
    }

    @Override
    public List<StageTransitionLease> leases(int limit) {
        return leases.values().stream().limit(limit).map(permit -> new StageTransitionLease(
                permit.operationId(), Optional.of(permit.sourceStage()), permit.targetStage(),
                permit.configurationRevision(), Optional.of(net.maddkraft.maddprestige.api.operation.OperationState.PREPARED),
                true, Instant.EPOCH)).toList();
    }

    public boolean hasLease(OperationId operationId) {
        return leases.containsKey(operationId);
    }
}
