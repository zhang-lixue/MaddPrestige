package net.maddkraft.maddprestige.core.stage;

import java.time.Instant;
import java.util.List;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;

/**
 * Durable authority boundary shared by stage-changing operations and configuration stage remaps.
 */
public interface StageTransitionFence {
    StageTransitionPermit acquire(
            OperationId operationId,
            StageId sourceStage,
            StageId targetStage,
            ConfigRevisionId configurationRevision,
            Instant acquiredAt);

    void release(StageTransitionPermit permit, Instant releasedAt);

    void release(OperationId operationId, Instant releasedAt);

    int releaseTerminalLeases(Instant releasedAt);

    List<StageTransitionLease> leases(int limit);
}
