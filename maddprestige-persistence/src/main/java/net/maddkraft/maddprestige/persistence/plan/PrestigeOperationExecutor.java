package net.maddkraft.maddprestige.persistence.plan;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.persistence.OperationRepository;
import net.maddkraft.maddprestige.persistence.PrestigeLifecycleRepository;

/** Deterministic journal-first Prestige executor. */
public final class PrestigeOperationExecutor {
    private static final String PROJECTION_ACTION = "rank-projection";
    private static final String COMMIT_ACTION = "prestige-state-commit";
    private final PrestigeLifecycleRepository lifecycle;
    private final OperationRepository operations;
    private final ProviderRegistry providers;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final Clock clock;

    public PrestigeOperationExecutor(
            PrestigeLifecycleRepository lifecycle,
            OperationRepository operations,
            ProviderRegistry providers,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Clock clock) {
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "Prestige lifecycle repository");
        this.operations = java.util.Objects.requireNonNull(operations, "operation repository");
        this.providers = java.util.Objects.requireNonNull(providers, "provider registry");
        this.activeRevision = java.util.Objects.requireNonNull(activeRevision, "active revision");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public PrestigeExecutionResult execute(PrestigePlan plan) {
        java.util.Objects.requireNonNull(plan, "Prestige plan");
        if (!plan.executionAllowed() || !plan.blockers().isEmpty() || !plan.authorization().matches(plan)) {
            return result(plan, PrestigeExecutionStatus.UNAUTHORIZED,
                    "Prestige authority is denied, missing, or does not seal this exact confirmation");
        }
        if (activeRevision.get().filter(plan.configRevision()::equals).isEmpty()) {
            return result(plan, PrestigeExecutionStatus.STALE_CONFIGURATION,
                    "Active configuration changed after confirmation");
        }
        if (!bindingsMatch(plan)) {
            return result(plan, PrestigeExecutionStatus.STALE_GENERATION,
                    "A pinned provider generation changed after confirmation");
        }
        var duplicate = operations.findByIdempotency("prestige", plan.playerId(),
                plan.operationPlan().idempotencyKey());
        if (duplicate.isPresent()) {
            return result(plan, PrestigeExecutionStatus.DUPLICATE,
                    "Prestige idempotency key already belongs to operation "
                            + duplicate.orElseThrow().operationId());
        }
        try {
            lifecycle.insertPrepared(plan);
        } catch (RuntimeException insertionFailure) {
            var raced = operations.findByIdempotency("prestige", plan.playerId(),
                    plan.operationPlan().idempotencyKey());
            if (raced.isPresent()) {
                return result(plan, PrestigeExecutionStatus.DUPLICATE,
                        "Concurrent Prestige request already persisted operation "
                                + raced.orElseThrow().operationId());
            }
            throw insertionFailure;
        }
        PrestigeExecutionResult projectionValidation = validateProjection(plan);
        if (projectionValidation != null) {
            return projectionValidation;
        }
        operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.EXECUTING);
        ArrayList<ExecutedCost> executedCosts = new ArrayList<>();
        for (PlannedCost cost : plan.costs()) {
            if (!bindingMatches(cost.definition().providerId(), cost.providerGeneration())) {
                return failCosts(plan, executedCosts, cost.actionId(),
                        "Cost provider generation changed before execution", true);
            }
            CostProvider provider;
            try {
                provider = provider(cost.definition().providerId(), CostProvider.class);
            } catch (RuntimeException exception) {
                return failCosts(plan, executedCosts, cost.actionId(),
                        "Pinned cost provider disappeared", false);
            }
            start(plan, cost.actionId());
            ActionExecutionResult outcome;
            try {
                outcome = provider.execute(cost).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                uncertain(plan, cost.actionId(), "Cost call failed exceptionally: " + rootMessage(exception),
                        OperationState.EXECUTING);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        "Cost effect is uncertain and will not be blindly replayed");
            }
            if (outcome.status() == ActionExecutionStatus.UNCERTAIN) {
                uncertain(plan, cost.actionId(), outcome.detail().orElse("Cost effect is uncertain"),
                        OperationState.EXECUTING);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        outcome.detail().orElse("Cost effect is uncertain"));
            }
            if (outcome.status() == ActionExecutionStatus.FAILED) {
                failAction(plan, cost.actionId(), outcome.detail().orElse("Cost failed"));
                return compensate(plan, executedCosts, outcome.detail().orElse("Cost failed"));
            }
            succeedAndVerify(plan, cost.actionId());
            executedCosts.add(new ExecutedCost(cost, provider));
        }
        if (!bindingsMatch(plan)) {
            String nextAction = plan.rankProjectionRequest().isPresent() ? PROJECTION_ACTION : COMMIT_ACTION;
            return failCosts(plan, executedCosts, nextAction,
                    "Full configuration/provider binding changed during cost execution", true);
        }
        PrestigeExecutionResult projected = executeProjection(plan, executedCosts);
        if (projected != null) {
            return projected;
        }
        start(plan, COMMIT_ACTION);
        try {
            lifecycle.commitInternal(plan, clock.instant());
        } catch (RuntimeException exception) {
            boolean observed;
            try {
                observed = lifecycle.internalCommitObserved(plan.operationId());
            } catch (RuntimeException reconciliationFailure) {
                uncertain(plan, COMMIT_ACTION, "Internal commit verification failed: "
                        + rootMessage(reconciliationFailure), OperationState.EXECUTING);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        "Internal commit outcome could not be verified");
            }
            if (!observed) {
                failAction(plan, COMMIT_ACTION, "Atomic internal commit failed: " + rootMessage(exception));
                if (plan.rankProjectionRequest().isPresent()) {
                    operations.transition(plan.operationId(), OperationState.EXECUTING,
                            OperationState.NEEDS_RECONCILIATION);
                    return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                            "External rank projection applied but authoritative Prestige commit failed");
                }
                return compensate(plan, executedCosts, "Atomic internal Prestige commit failed");
            }
        }
        succeedAndVerify(plan, COMMIT_ACTION);
        verifyCommittedInternalActions(plan);
        operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.STATE_COMMITTED);
        for (var reward : plan.rewards()) {
            if (!bindingMatches(reward.definition().providerId(), reward.providerGeneration())) {
                markPendingFailed(plan, reward.actionId(), "Reward provider generation changed after state commit");
                operations.transition(plan.operationId(), OperationState.STATE_COMMITTED,
                        OperationState.NEEDS_RECONCILIATION);
                lifecycle.recordResult(plan.operationId(), OperationState.NEEDS_RECONCILIATION.name());
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        "Reward generation changed after authoritative state commit");
            }
            RewardProvider provider;
            try {
                provider = provider(reward.definition().providerId(), RewardProvider.class);
            } catch (RuntimeException exception) {
                markPendingFailed(plan, reward.actionId(), "Pinned reward provider disappeared");
                if (reward.definition().failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    operations.transition(plan.operationId(), OperationState.STATE_COMMITTED,
                            OperationState.NEEDS_RECONCILIATION);
                    lifecycle.recordResult(plan.operationId(), OperationState.NEEDS_RECONCILIATION.name());
                    return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                            "Required reward provider disappeared after state commit");
                }
                continue;
            }
            start(plan, reward.actionId());
            ActionExecutionResult outcome;
            try {
                outcome = provider.execute(reward).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                uncertain(plan, reward.actionId(), "Reward call failed exceptionally: " + rootMessage(exception),
                        OperationState.STATE_COMMITTED);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        "Reward effect is uncertain and will not be blindly replayed");
            }
            if (outcome.status() == ActionExecutionStatus.UNCERTAIN) {
                uncertain(plan, reward.actionId(), outcome.detail().orElse("Reward effect is uncertain"),
                        OperationState.STATE_COMMITTED);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        outcome.detail().orElse("Reward effect is uncertain"));
            }
            if (outcome.status() == ActionExecutionStatus.FAILED) {
                failAction(plan, reward.actionId(), outcome.detail().orElse("Reward failed"));
                if (reward.definition().failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    operations.transition(plan.operationId(), OperationState.STATE_COMMITTED,
                            OperationState.NEEDS_RECONCILIATION);
                    lifecycle.recordResult(plan.operationId(), OperationState.NEEDS_RECONCILIATION.name());
                    return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                            "Required reward failed after authoritative state commit");
                }
            } else {
                succeedAndVerify(plan, reward.actionId());
            }
        }
        operations.transition(plan.operationId(), OperationState.STATE_COMMITTED, OperationState.COMPLETED);
        lifecycle.recordResult(plan.operationId(), OperationState.COMPLETED.name());
        return result(plan, PrestigeExecutionStatus.COMPLETED,
                "Costs and projection verified; Prestige/state/scope/history committed; rewards processed");
    }

    private PrestigeExecutionResult validateProjection(PrestigePlan plan) {
        if (plan.rankProjectionRequest().isEmpty()) {
            return null;
        }
        var request = plan.rankProjectionRequest().orElseThrow();
        ProviderId providerId = plan.rankProviderId().orElseThrow();
        if (!bindingMatches(providerId, request.providerGeneration())) {
            markPendingFailed(plan, PROJECTION_ACTION, "Rank provider generation changed before target validation");
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return result(plan, PrestigeExecutionStatus.STALE_GENERATION,
                    "Rank provider generation changed before any cost mutation");
        }
        try {
            RankAdapter adapter = provider(providerId, RankAdapter.class);
            Result<java.util.Set<String>> validation = adapter.validateTargets(request.managedGroups())
                    .toCompletableFuture().join();
            if (!validation.isSuccess()
                    || !validation.value().orElse(java.util.Set.of()).containsAll(request.managedGroups())) {
                String detail = resultDetail(validation, "One or more managed rank targets are unavailable");
                markPendingFailed(plan, PROJECTION_ACTION, detail);
                operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
                return result(plan, PrestigeExecutionStatus.FAILED, detail + "; no costs consumed");
            }
        } catch (RuntimeException exception) {
            markPendingFailed(plan, PROJECTION_ACTION, "Rank target validation failed: " + rootMessage(exception));
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return result(plan, PrestigeExecutionStatus.FAILED,
                    "Rank target validation failed before any cost mutation");
        }
        return null;
    }

    private PrestigeExecutionResult executeProjection(
            PrestigePlan plan,
            List<ExecutedCost> executedCosts) {
        if (plan.rankProjectionRequest().isEmpty()) {
            return null;
        }
        var request = plan.rankProjectionRequest().orElseThrow();
        ProviderId providerId = plan.rankProviderId().orElseThrow();
        if (!bindingMatches(providerId, request.providerGeneration())) {
            return failCosts(plan, executedCosts, PROJECTION_ACTION,
                    "Rank provider generation changed before projection", true);
        }
        RankAdapter adapter;
        try {
            adapter = provider(providerId, RankAdapter.class);
        } catch (RuntimeException exception) {
            return failCosts(plan, executedCosts, PROJECTION_ACTION, "Pinned RankAdapter disappeared", false);
        }
        start(plan, PROJECTION_ACTION);
        Result<net.maddkraft.maddprestige.api.rank.RankProjectionResult> outcome;
        try {
            outcome = adapter.project(request).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            uncertain(plan, PROJECTION_ACTION, "Rank projection call failed: " + rootMessage(exception),
                    OperationState.EXECUTING);
            return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                    "Rank projection effect is uncertain");
        }
        if (!outcome.isSuccess()) {
            String detail = resultDetail(outcome, "Rank projection failed");
            boolean uncertainEffect = outcome.errors().stream()
                    .anyMatch(error -> error.category() == ErrorCategory.UNCERTAIN);
            if (uncertainEffect) {
                uncertain(plan, PROJECTION_ACTION, detail, OperationState.EXECUTING);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION, detail);
            }
            failAction(plan, PROJECTION_ACTION, detail);
            return compensate(plan, executedCosts, detail);
        }
        if (!bindingsMatch(plan)) {
            uncertain(plan, PROJECTION_ACTION, "Provider/configuration binding changed during projection",
                    OperationState.EXECUTING);
            return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                    "Provider/configuration binding changed during projection");
        }
        java.util.HashSet<String> managed = new java.util.HashSet<>(
                outcome.value().orElseThrow().after().permanentContextFreeGroups());
        managed.retainAll(request.managedGroups());
        java.util.Set<String> expected = request.desiredGroup().map(java.util.Set::of)
                .orElseGet(java.util.Set::of);
        if (!managed.equals(expected)) {
            uncertain(plan, PROJECTION_ACTION, "Rank adapter did not return the exact managed projection",
                    OperationState.EXECUTING);
            return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                    "Rank projection postcondition is uncertain");
        }
        succeedAndVerify(plan, PROJECTION_ACTION);
        return null;
    }

    private PrestigeExecutionResult failCosts(
            PrestigePlan plan,
            List<ExecutedCost> executedCosts,
            String actionId,
            String detail,
            boolean stale) {
        markPendingFailed(plan, actionId, detail);
        if (executedCosts.isEmpty()) {
            operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.FAILED);
            return result(plan, stale ? PrestigeExecutionStatus.STALE_GENERATION : PrestigeExecutionStatus.FAILED,
                    detail);
        }
        return compensate(plan, executedCosts, detail);
    }

    private PrestigeExecutionResult compensate(
            PrestigePlan plan,
            List<ExecutedCost> executedCosts,
            String reason) {
        if (executedCosts.isEmpty()) {
            operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.FAILED);
            return result(plan, PrestigeExecutionStatus.FAILED, reason);
        }
        if (executedCosts.stream().anyMatch(value -> !value.cost().characteristics().reversible())) {
            operations.transition(plan.operationId(), OperationState.EXECUTING,
                    OperationState.NEEDS_RECONCILIATION);
            return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                    reason + "; a consumed cost is not safely reversible");
        }
        operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.COMPENSATING);
        ArrayList<ExecutedCost> reversed = new ArrayList<>(executedCosts);
        Collections.reverse(reversed);
        for (ExecutedCost executed : reversed) {
            String actionId = "compensate-" + executed.cost().actionId();
            start(plan, actionId);
            ActionExecutionResult outcome;
            try {
                outcome = executed.provider().compensate(executed.cost()).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                uncertainAction(plan, actionId, "Cost compensation failed exceptionally: "
                        + rootMessage(exception));
                operations.transition(plan.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        reason + "; cost compensation outcome is uncertain");
            }
            if (outcome.status() == ActionExecutionStatus.FAILED) {
                failAction(plan, actionId, outcome.detail().orElse("Cost compensation failed"));
                operations.transition(plan.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        reason + "; cost compensation failed");
            }
            if (outcome.status() == ActionExecutionStatus.UNCERTAIN) {
                uncertainAction(plan, actionId, outcome.detail().orElse("Cost compensation is uncertain"));
                operations.transition(plan.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                        reason + "; cost compensation is uncertain");
            }
            succeedAndVerify(plan, actionId);
        }
        operations.transition(plan.operationId(), OperationState.COMPENSATING, OperationState.COMPENSATED);
        return result(plan, PrestigeExecutionStatus.COMPENSATED, reason + "; consumed costs compensated");
    }

    private void verifyCommittedInternalActions(PrestigePlan plan) {
        plan.simulation().currencyChanges().forEach(value -> verifyPendingInternal(plan,
                "currency-reset-" + value.currencyId().value()));
        plan.simulation().milestoneConsequences().forEach(value -> verifyPendingInternal(plan,
                "milestone-" + value.milestoneId().value()));
    }

    private void verifyPendingInternal(PrestigePlan plan, String actionId) {
        start(plan, actionId);
        succeedAndVerify(plan, actionId);
    }

    private void start(PrestigePlan plan, String actionId) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.PENDING, ActionState.STARTED,
                Optional.empty());
    }

    private void succeedAndVerify(PrestigePlan plan, String actionId) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED, ActionState.SUCCEEDED,
                Optional.empty());
        operations.transitionAction(plan.operationId(), actionId, ActionState.SUCCEEDED, ActionState.VERIFIED,
                Optional.empty());
    }

    private void failAction(PrestigePlan plan, String actionId, String detail) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED, ActionState.FAILED,
                Optional.of(detail));
    }

    private void markPendingFailed(PrestigePlan plan, String actionId, String detail) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.PENDING, ActionState.FAILED,
                Optional.of(detail));
    }

    private void uncertain(
            PrestigePlan plan,
            String actionId,
            String detail,
            OperationState state) {
        uncertainAction(plan, actionId, detail);
        operations.transition(plan.operationId(), state, OperationState.NEEDS_RECONCILIATION);
        if (state == OperationState.STATE_COMMITTED) {
            lifecycle.recordResult(plan.operationId(), OperationState.NEEDS_RECONCILIATION.name());
        }
    }

    private void uncertainAction(PrestigePlan plan, String actionId, String detail) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED, ActionState.UNCERTAIN,
                Optional.of(detail));
    }

    private boolean bindingsMatch(PrestigePlan plan) {
        return activeRevision.get().filter(plan.configRevision()::equals).isPresent()
                && plan.providerGenerations().entrySet().stream()
                        .allMatch(entry -> bindingMatches(entry.getKey(), entry.getValue()));
    }

    private boolean bindingMatches(ProviderId id, long generation) {
        var snapshot = providers.find(id);
        return snapshot.isPresent() && snapshot.orElseThrow().generation() == generation
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state());
    }

    private <T extends Provider> T provider(ProviderId id, Class<T> type) {
        return providers.provider(id).filter(type::isInstance).map(type::cast)
                .orElseThrow(() -> new IllegalStateException("Pinned provider contract disappeared"));
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static String resultDetail(Result<?> result, String fallback) {
        return result.errors().stream().map(error -> error.code() + ": " + error.message())
                .reduce((left, right) -> left + "; " + right).orElse(fallback);
    }

    private static PrestigeExecutionResult result(
            PrestigePlan plan,
            PrestigeExecutionStatus status,
            String detail) {
        return new PrestigeExecutionResult(plan.operationId(), status, detail);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ExecutedCost(PlannedCost cost, CostProvider provider) {
    }
}
