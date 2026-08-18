package net.maddkraft.maddprestige.platform.paper.event;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.core.event.OperationLifecycleListener;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.Plugin;

/** Delivers stable operation events on the Paper thread and rejects same-player event reentrancy. */
public final class PaperOperationLifecycle implements OperationLifecycleListener {
    private final Plugin plugin;
    private final PaperTaskScheduler scheduler;
    private final Clock clock;
    private final Set<UUID> dispatching = ConcurrentHashMap.newKeySet();

    public PaperOperationLifecycle(Plugin plugin, PaperTaskScheduler scheduler, Clock clock) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /** True while this player's lifecycle listener stack is active. */
    public boolean isDispatching(UUID playerId) {
        return dispatching.contains(playerId);
    }

    @Override
    public boolean beforeRankUp(RankUpPlan plan) {
        return before(plan.playerId(), new PreRankUpEvent(rankSnapshot(plan, Optional.empty())));
    }

    @Override
    public void afterRankUp(RankUpPlan plan, RankUpExecutionResult result) {
        post(plan.playerId(), new PostRankUpEvent(rankSnapshot(plan, Optional.of(rankStatus(result.status())))));
    }

    @Override
    public boolean beforePrestige(PrestigePlan plan) {
        return before(plan.playerId(), new PrePrestigeEvent(prestigeSnapshot(plan, Optional.empty())));
    }

    @Override
    public void afterPrestige(PrestigePlan plan, PrestigeExecutionResult result) {
        post(plan.playerId(), new PostPrestigeEvent(prestigeSnapshot(plan,
                Optional.of(prestigeStatus(result.status())))));
    }

    private boolean before(UUID playerId, org.bukkit.event.Cancellable event) {
        if (!dispatching.add(playerId)) {
            return false;
        }
        try {
            scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, () -> {
                dispatch((Event) event, true);
                return null;
            }).toCompletableFuture().join();
            return !event.isCancelled();
        } finally {
            dispatching.remove(playerId);
        }
    }

    private void post(UUID playerId, org.bukkit.event.Event event) {
        if (!dispatching.add(playerId)) {
            return;
        }
        try {
            scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, () -> {
                dispatch(event, false);
                return null;
            }).toCompletableFuture().join();
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING, "Post-operation event listener failed safely", failure);
        } finally {
            dispatching.remove(playerId);
        }
    }

    private void dispatch(Event event, boolean failFast) {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            try {
                listener.callEvent(event);
            } catch (EventException | RuntimeException | LinkageError failure) {
                if (failFast) {
                    throw new IllegalStateException("Pre-operation event listener failed", failure);
                }
                plugin.getLogger().log(Level.WARNING, "Post-operation listener failed safely for "
                        + listener.getPlugin().getName(), failure);
            }
        }
    }

    private OperationEventSnapshot rankSnapshot(RankUpPlan plan, Optional<OperationStatus> status) {
        return new OperationEventSnapshot(plan.requestId(), status.map(ignored -> plan.operationId()),
                plan.playerId(), OperationKind.RANK_UP,
                Optional.of(plan.sourceStage()), Optional.of(plan.targetStage()), plan.configRevision(), status,
                clock.instant());
    }

    private OperationEventSnapshot prestigeSnapshot(PrestigePlan plan, Optional<OperationStatus> status) {
        return new OperationEventSnapshot(plan.requestId(), status.map(ignored -> plan.operationId()),
                plan.playerId(), OperationKind.PRESTIGE,
                Optional.of(plan.simulation().sourceStage()), Optional.of(plan.simulation().resetStage()),
                plan.configRevision(), status, clock.instant());
    }

    private static OperationStatus rankStatus(RankUpExecutionStatus status) {
        return switch (status) {
            case COMPLETED -> OperationStatus.COMPLETED;
            case BLOCKED, STALE_GENERATION, DUPLICATE -> OperationStatus.BLOCKED;
            case NEEDS_RECONCILIATION -> OperationStatus.NEEDS_RECONCILIATION;
            case FAILED, COMPENSATED -> OperationStatus.FAILED;
        };
    }

    private static OperationStatus prestigeStatus(PrestigeExecutionStatus status) {
        return switch (status) {
            case COMPLETED -> OperationStatus.COMPLETED;
            case UNAUTHORIZED, STALE_CONFIGURATION, STALE_GENERATION, DUPLICATE -> OperationStatus.BLOCKED;
            case NEEDS_RECONCILIATION -> OperationStatus.NEEDS_RECONCILIATION;
            case FAILED, COMPENSATED -> OperationStatus.FAILED;
        };
    }
}
