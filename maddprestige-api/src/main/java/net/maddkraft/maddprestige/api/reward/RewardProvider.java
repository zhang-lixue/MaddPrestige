package net.maddkraft.maddprestige.api.reward;

import java.util.List;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public interface RewardProvider extends Provider {
    ActionCharacteristics characteristics(RewardDefinition definition);

    ValidationReport validate(RewardDefinition definition);

    CompletionStage<RewardPreflight> preflight(PlannedReward proposed);

    /** Performs operation-wide, read-only reward preflight while preserving proposal order. */
    default CompletionStage<List<RewardPreflight>> preflightBatch(List<PlannedReward> proposed) {
        List<PlannedReward> immutable = List.copyOf(proposed);
        List<java.util.concurrent.CompletableFuture<RewardPreflight>> futures = immutable.stream()
                .map(reward -> preflight(reward).toCompletableFuture()).toList();
        return java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(java.util.concurrent.CompletableFuture::join).toList());
    }

    CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward);
}
