package net.maddkraft.maddprestige.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.core.milestone.MilestoneStateReader;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

public interface PrestigeLifecycleRepository extends MilestoneStateReader {
    void insertPrepared(PrestigePlan plan);

    void commitInternal(PrestigePlan plan, Instant now);

    boolean internalCommitObserved(OperationId operationId);

    void recordResult(OperationId operationId, String result);

    Optional<StoredPrestigeOperation> findPrepared(OperationId operationId);

    Optional<PlannedReward> findRecoveryReward(OperationId operationId, String actionId);

    Optional<PlannedCost> findRecoveryCost(OperationId operationId, String actionId);

    List<PrestigeHistoryRecord> history(UUID playerId, int limit);

    PrestigeHistoryPage history(UUID playerId, int offset, int limit);

    Optional<PrestigeHistoryRecord> historyEntry(UUID playerId, OperationId operationId);

    @Override
    boolean awarded(UUID playerId, MilestoneId milestoneId, String repeatabilityKey);
}
