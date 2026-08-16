package net.maddkraft.maddprestige.testkit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public final class FakeRewardProvider extends FakeProvider implements RewardProvider {
    private final Set<String> applied = ConcurrentHashMap.newKeySet();
    private volatile ActionExecutionResult nextExecution;
    private volatile RuntimeException nextFailure;
    private volatile Supplier<CompletionStage<RewardPreflight>> nextPreflight;

    public FakeRewardProvider(ProviderId providerId) {
        super(providerId, "reward", List.of(new CapabilityDescriptor("fake-reward", "reward",
                "Fake reward", Map.of())));
    }

    public int executionCount() {
        return applied.size();
    }

    public void nextExecution(ActionExecutionResult result) {
        nextExecution = result;
    }

    public void failNextExecution(RuntimeException failure) {
        nextFailure = failure;
    }

    public void throwNextPreflight(RuntimeException failure) {
        nextPreflight = () -> {
            throw failure;
        };
    }

    public void failNextPreflight(RuntimeException failure) {
        nextPreflight = () -> CompletableFuture.failedFuture(failure);
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return new ActionCharacteristics(true, false, true, true);
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        return ValidationReport.VALID;
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        Supplier<CompletionStage<RewardPreflight>> forced = nextPreflight;
        nextPreflight = null;
        if (forced != null) {
            return forced.get();
        }
        if (health().state() != ProviderHealthState.AVAILABLE && health().state() != ProviderHealthState.ACTIVE) {
            return CompletableFuture.completedFuture(RewardPreflight.unavailable("Fake reward provider unavailable"));
        }
        return CompletableFuture.completedFuture(RewardPreflight.ready(proposed));
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        RuntimeException failure = nextFailure;
        nextFailure = null;
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        ActionExecutionResult forced = nextExecution;
        nextExecution = null;
        if (forced != null) {
            if (forced.status() == net.maddkraft.maddprestige.api.action.ActionExecutionStatus.APPLIED) {
                applied.add(key(plannedReward));
            }
            return CompletableFuture.completedFuture(forced);
        }
        return CompletableFuture.completedFuture(applied.add(key(plannedReward))
                ? ActionExecutionResult.applied() : ActionExecutionResult.unchanged());
    }

    private static String key(PlannedReward reward) {
        return reward.operationId() + ":" + reward.actionId();
    }
}
