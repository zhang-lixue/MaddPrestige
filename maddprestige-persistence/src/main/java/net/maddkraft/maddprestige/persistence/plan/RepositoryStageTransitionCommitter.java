package net.maddkraft.maddprestige.persistence.plan;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.plan.StageTransitionCommitter;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.PlayerStageRepository;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.StageHistoryRecord;

/**
 * Production optimistic-CAS committer backed by the accepted Phase 2 player-stage repository. It compares the
 * observed source-row provenance separately from the active operation revision written on successful advancement.
 */
public final class RepositoryStageTransitionCommitter implements StageTransitionCommitter {
    private final PlayerStageRepository playerStages;
    private final Clock clock;

    public RepositoryStageTransitionCommitter(PlayerStageRepository playerStages, Clock clock) {
        this.playerStages = Objects.requireNonNull(playerStages, "player stage repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<ActionExecutionResult> commit(RankUpPlan plan) {
        Objects.requireNonNull(plan, "rank-up plan");
        var current = playerStages.find(plan.playerId());
        if (current.isEmpty() || current.orElseThrow().stateRevision() != plan.expectedStateRevision()
                || !current.orElseThrow().stageId().equals(plan.sourceStage())
                || !current.orElseThrow().configRevision().equals(plan.expectedPlayerConfigRevision())) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(
                    "Authoritative player stage state changed before commit"));
        }
        Optional<Long> rankGeneration = plan.rankProjectionRequest()
                .map(request -> request.providerGeneration());
        try {
            var replacement = current.orElseThrow().advanceTo(plan.targetStage(), plan.configRevision(),
                    rankGeneration, clock.instant());
            playerStages.updateAndAppendHistory(replacement, plan.expectedStateRevision(), new StageHistoryRecord(
                    plan.playerId(), plan.targetStage(), replacement.stageEnteredAt(), plan.operationId(),
                    plan.operationPlan().actor(), "Normal rank-up", plan.configRevision()));
            return CompletableFuture.completedFuture(ActionExecutionResult.applied());
        } catch (StalePlayerStageStateException exception) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(exception.getMessage()));
        } catch (PersistenceException exception) {
            return CompletableFuture.completedFuture(ActionExecutionResult.uncertain(
                    "Internal stage persistence outcome is uncertain: " + exception.getMessage()));
        }
    }
}
