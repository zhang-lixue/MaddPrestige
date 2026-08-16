package net.maddkraft.maddprestige.api.cost;

import java.util.List;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public interface CostProvider extends Provider {
    ActionCharacteristics characteristics(CostDefinition definition);

    ValidationReport validate(CostDefinition definition);

    CompletionStage<CostPreflight> preflight(PlannedCost proposed);

    /**
     * Validates all costs sharing this provider as one read-only unit. Providers must override this
     * method before accepting multiple costs that can draw from a coupled balance or quota.
     */
    default CompletionStage<List<CostPreflight>> preflightBatch(List<PlannedCost> proposed) {
        List<PlannedCost> immutable = List.copyOf(proposed);
        if (immutable.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        if (immutable.size() > 1) {
            return java.util.concurrent.CompletableFuture.completedFuture(immutable.stream()
                    .map(ignored -> CostPreflight.blocked(
                            "Provider has not declared aggregate preflight support for coupled costs"))
                    .toList());
        }
        return preflight(immutable.getFirst()).thenApply(List::of);
    }

    CompletionStage<ActionExecutionResult> execute(PlannedCost plannedCost);

    default CompletionStage<ActionExecutionResult> compensate(PlannedCost plannedCost) {
        return java.util.concurrent.CompletableFuture.completedFuture(
                ActionExecutionResult.failed("Cost provider does not support compensation"));
    }
}
