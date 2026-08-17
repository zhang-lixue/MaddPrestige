package net.maddkraft.maddprestige.persistence.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
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
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.plan.StageTransitionCommitter;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;
import net.maddkraft.maddprestige.core.stage.StageTransitionFence;
import net.maddkraft.maddprestige.core.stage.StageTransitionPermit;
import net.maddkraft.maddprestige.persistence.OperationRepository;
import net.maddkraft.maddprestige.persistence.PersistenceException;

public final class RankUpOperationExecutor {
    private static final String STAGE_ACTION = "stage-transition";
    private static final String PROJECTION_ACTION = "rank-projection";
    private final OperationRepository operations;
    private final ProviderRegistry providers;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final StageTransitionCommitter stageCommitter;
    private final StageTransitionFence transitionFence;
    private final Executor workerExecutor;

    public RankUpOperationExecutor(
            OperationRepository operations,
            ProviderRegistry providers,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            StageTransitionCommitter stageCommitter,
            StageTransitionFence transitionFence,
            Executor workerExecutor) {
        this.operations = Objects.requireNonNull(operations, "operation repository");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision supplier");
        this.stageCommitter = Objects.requireNonNull(stageCommitter, "stage committer");
        this.transitionFence = Objects.requireNonNull(transitionFence, "stage transition fence");
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "worker executor");
    }

    public CompletionStage<RankUpExecutionResult> execute(RankUpPlan plan) {
        return CompletableFuture.supplyAsync(() -> executeOnWorker(plan), workerExecutor);
    }

    private RankUpExecutionResult executeOnWorker(RankUpPlan plan) {
        if (!plan.authorization().matches(plan)) {
            return result(plan, RankUpExecutionStatus.BLOCKED,
                    "Rank-up plan lacks a valid canonical authorization binding");
        }
        if (!plan.executionAllowed()) {
            return result(plan, RankUpExecutionStatus.BLOCKED,
                    "Immutable plan is blocked: " + String.join("; ", plan.blockers()));
        }
        if (!bindingsMatch(plan)) {
            return result(plan, RankUpExecutionStatus.STALE_GENERATION,
                    "Configuration or provider generation changed before persistence/mutation");
        }
        var duplicate = operations.findByIdempotency(plan.operationPlan().operationType(), plan.playerId(),
                plan.operationPlan().idempotencyKey());
        if (duplicate.isPresent()
                && (!duplicate.orElseThrow().operationId().equals(plan.operationId())
                        || duplicate.orElseThrow().state() != OperationState.PREPARED)) {
            return result(plan, RankUpExecutionStatus.DUPLICATE,
                    "Existing operation has the same idempotency tuple: "
                            + duplicate.orElseThrow().operationId());
        }
        if (duplicate.isEmpty()) {
            try {
                operations.insertPrepared(plan.operationPlan());
            } catch (PersistenceException exception) {
                var raced = operations.findByIdempotency(plan.operationPlan().operationType(), plan.playerId(),
                        plan.operationPlan().idempotencyKey());
                if (raced.isEmpty() || !raced.orElseThrow().operationId().equals(plan.operationId())
                        || raced.orElseThrow().state() != OperationState.PREPARED) {
                    return result(plan, raced.isPresent()
                            ? RankUpExecutionStatus.DUPLICATE : RankUpExecutionStatus.FAILED,
                            "Could not persist immutable operation plan: " + exception.getMessage());
                }
            }
        }
        StageTransitionPermit permit;
        try {
            permit = transitionFence.acquire(plan.operationId(), plan.sourceStage(), plan.targetStage(),
                    plan.configRevision(), Instant.now());
        } catch (StageTransitionBlockedException exception) {
            failPrepared(plan.operationId());
            return result(plan, RankUpExecutionStatus.BLOCKED,
                    "Configuration transition fence denied source/target participation before effects: "
                            + exception.getMessage());
        }
        RankUpExecutionResult outcome = null;
        try {
            outcome = executeWhileFenced(plan);
            return outcome;
        } finally {
            if (outcome != null && outcome.status() != RankUpExecutionStatus.NEEDS_RECONCILIATION) {
                transitionFence.release(permit, Instant.now());
            }
        }
    }

    private RankUpExecutionResult executeWhileFenced(RankUpPlan plan) {
        if (!bindingsMatch(plan)) {
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return result(plan, RankUpExecutionStatus.STALE_GENERATION,
                    "Configuration or provider generation changed after persistence but before mutation");
        }
        RankUpExecutionResult invalidProjection = validateProjectionBeforeCosts(plan);
        if (invalidProjection != null) {
            return invalidProjection;
        }
        try {
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.EXECUTING);
        } catch (PersistenceException exception) {
            return result(plan, RankUpExecutionStatus.DUPLICATE,
                    "Another owner already resumed this exact prepared operation");
        }
        ArrayList<ExecutedCost> executedCosts = new ArrayList<>();
        for (PlannedCost cost : plan.costs()) {
            if (!bindingMatches(cost.definition().providerId(), cost.providerGeneration())) {
                return failCosts(plan, executedCosts, cost.actionId(),
                        "Cost provider generation changed before mutation", true);
            }
            CostProvider provider;
            try {
                provider = provider(cost.definition().providerId(), CostProvider.class);
            } catch (RuntimeException exception) {
                return failCosts(plan, executedCosts, cost.actionId(),
                        "Pinned cost provider contract disappeared before mutation: " + rootMessage(exception), true);
            }
            start(plan, cost.actionId());
            ActionExecutionResult outcome;
            try {
                outcome = provider.execute(cost).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                String detail = "Cost provider failed after execution started: " + rootMessage(exception);
                uncertain(plan, cost.actionId(), detail, OperationState.EXECUTING);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
            }
            if (outcome.status() == ActionExecutionStatus.UNCERTAIN) {
                uncertain(plan, cost.actionId(), outcome.detail().orElse("Cost effect is uncertain"),
                        OperationState.EXECUTING);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        outcome.detail().orElse("Cost effect is uncertain"));
            }
            if (outcome.status() == ActionExecutionStatus.FAILED) {
                failAction(plan, cost.actionId(), outcome.detail().orElse("Cost execution failed"));
                return compensate(plan, executedCosts, outcome.detail().orElse("Cost execution failed"));
            }
            succeed(plan, cost.actionId());
            executedCosts.add(new ExecutedCost(cost, provider));
        }
        executedCosts.forEach(executed -> verify(plan, executed.cost.actionId()));
        if (!bindingsMatch(plan)) {
            return compensate(plan, executedCosts, "Pinned bindings changed before stage transition");
        }
        RankUpExecutionResult projection = executeProjection(plan, executedCosts);
        if (projection != null) {
            return projection;
        }
        start(plan, STAGE_ACTION);
        ActionExecutionResult stage;
        try {
            stage = stageCommitter.commit(plan).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            String detail = "Stage transition failed after commit started: " + rootMessage(exception);
            uncertain(plan, STAGE_ACTION, detail, OperationState.EXECUTING);
            return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
        }
        if (stage.status() == ActionExecutionStatus.UNCERTAIN) {
            uncertain(plan, STAGE_ACTION, stage.detail().orElse("Stage transition effect is uncertain"),
                    OperationState.EXECUTING);
            return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                    stage.detail().orElse("Stage transition effect is uncertain"));
        }
        if (stage.status() == ActionExecutionStatus.FAILED) {
            failAction(plan, STAGE_ACTION, stage.detail().orElse("Stage transition failed"));
            if (plan.rankProjectionRequest().isPresent()) {
                operations.transition(plan.operationId(), OperationState.EXECUTING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        "External projection is verified but the authoritative internal commit failed: "
                                + stage.detail().orElse("Stage transition failed"));
            }
            return compensate(plan, executedCosts, stage.detail().orElse("Stage transition failed"));
        }
        succeed(plan, STAGE_ACTION);
        verify(plan, STAGE_ACTION);
        operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.STATE_COMMITTED);
        for (PlannedReward reward : plan.rewards()) {
            if (!bindingMatches(reward.definition().providerId(), reward.providerGeneration())) {
                String detail = "Reward provider generation changed after authoritative stage commit";
                operations.transitionAction(plan.operationId(), reward.actionId(), ActionState.PENDING,
                        ActionState.FAILED, Optional.of(detail));
                if (reward.definition().failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    operations.transition(plan.operationId(), OperationState.STATE_COMMITTED,
                            OperationState.NEEDS_RECONCILIATION);
                    return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
                }
                continue;
            }
            RewardProvider provider;
            try {
                provider = provider(reward.definition().providerId(), RewardProvider.class);
            } catch (RuntimeException exception) {
                String detail = "Pinned reward provider contract disappeared: " + rootMessage(exception);
                operations.transitionAction(plan.operationId(), reward.actionId(), ActionState.PENDING,
                        ActionState.FAILED, Optional.of(detail));
                if (reward.definition().failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    operations.transition(plan.operationId(), OperationState.STATE_COMMITTED,
                            OperationState.NEEDS_RECONCILIATION);
                    return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
                }
                continue;
            }
            start(plan, reward.actionId());
            ActionExecutionResult outcome;
            try {
                outcome = provider.execute(reward).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                String detail = "Reward provider failed after execution started: " + rootMessage(exception);
                uncertain(plan, reward.actionId(), detail, OperationState.STATE_COMMITTED);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
            }
            if (outcome.status() == ActionExecutionStatus.UNCERTAIN) {
                uncertain(plan, reward.actionId(), outcome.detail().orElse("Reward effect is uncertain"),
                        OperationState.STATE_COMMITTED);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        outcome.detail().orElse("Reward effect is uncertain"));
            }
            if (outcome.status() == ActionExecutionStatus.FAILED) {
                failAction(plan, reward.actionId(), outcome.detail().orElse("Reward execution failed"));
                if (reward.definition().failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    operations.transition(plan.operationId(), OperationState.STATE_COMMITTED,
                            OperationState.NEEDS_RECONCILIATION);
                    return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                            outcome.detail().orElse("Required reward failed after stage commit"));
                }
            } else {
                succeed(plan, reward.actionId());
                verify(plan, reward.actionId());
            }
        }
        operations.transition(plan.operationId(), OperationState.STATE_COMMITTED, OperationState.COMPLETED);
        return result(plan, RankUpExecutionStatus.COMPLETED,
                "Costs verified, external projection processed, authoritative stage committed, and rewards processed");
    }

    private RankUpExecutionResult validateProjectionBeforeCosts(RankUpPlan plan) {
        if (plan.rankProjectionRequest().isEmpty()) {
            return null;
        }
        var request = plan.rankProjectionRequest().orElseThrow();
        ProviderId providerId = plan.externalRankProjection().orElseThrow().providerId().orElseThrow();
        if (!bindingMatches(providerId, request.providerGeneration())) {
            operations.transitionAction(plan.operationId(), PROJECTION_ACTION, ActionState.PENDING,
                    ActionState.FAILED, Optional.of("Rank provider generation changed before target validation"));
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return result(plan, RankUpExecutionStatus.STALE_GENERATION,
                    "Rank provider generation changed before any cost mutation");
        }
        RankAdapter adapter;
        try {
            adapter = provider(providerId, RankAdapter.class);
        } catch (RuntimeException exception) {
            operations.transitionAction(plan.operationId(), PROJECTION_ACTION, ActionState.PENDING,
                    ActionState.FAILED, Optional.of("Pinned rank adapter contract disappeared"));
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return result(plan, RankUpExecutionStatus.FAILED,
                    "Pinned rank adapter contract disappeared before any cost mutation");
        }
        try {
            Result<java.util.Set<String>> validation = adapter.validateTargets(request.managedGroups())
                    .toCompletableFuture().join();
            if (!validation.isSuccess()
                    || !validation.value().orElse(java.util.Set.of()).containsAll(request.managedGroups())) {
                String detail = validation.isSuccess()
                        ? "One or more pinned managed groups no longer exist"
                        : resultDetail(validation, "External target validation failed");
                operations.transitionAction(plan.operationId(), PROJECTION_ACTION, ActionState.PENDING,
                        ActionState.FAILED, Optional.of(detail));
                operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
                return result(plan, RankUpExecutionStatus.FAILED, detail + "; no costs were consumed");
            }
        } catch (RuntimeException exception) {
            String detail = "External target validation failed: " + rootMessage(exception);
            operations.transitionAction(plan.operationId(), PROJECTION_ACTION, ActionState.PENDING,
                    ActionState.FAILED, Optional.of(detail));
            operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return result(plan, RankUpExecutionStatus.FAILED, detail + "; no costs were consumed");
        }
        return null;
    }

    private RankUpExecutionResult executeProjection(RankUpPlan plan, List<ExecutedCost> executedCosts) {
        if (plan.rankProjectionRequest().isEmpty()) {
            return null;
        }
        var request = plan.rankProjectionRequest().orElseThrow();
        ProviderId providerId = plan.externalRankProjection().orElseThrow().providerId().orElseThrow();
        if (!bindingMatches(providerId, request.providerGeneration())) {
            return failCosts(plan, executedCosts, PROJECTION_ACTION,
                    "Rank provider generation changed immediately before projection", true);
        }
        RankAdapter adapter;
        try {
            adapter = provider(providerId, RankAdapter.class);
        } catch (RuntimeException exception) {
            return failCosts(plan, executedCosts, PROJECTION_ACTION,
                    "Pinned rank adapter contract disappeared before projection", true);
        }
        start(plan, PROJECTION_ACTION);
        Result<RankProjectionResult> outcome;
        try {
            outcome = adapter.project(request).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            String detail = "Rank projection failed after the external call started: " + rootMessage(exception);
            uncertain(plan, PROJECTION_ACTION, detail, OperationState.EXECUTING);
            return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
        }
        if (!outcome.isSuccess()) {
            String detail = resultDetail(outcome, "Rank projection failed");
            boolean uncertainEffect = outcome.errors().stream()
                    .anyMatch(error -> error.category() == ErrorCategory.UNCERTAIN);
            if (uncertainEffect) {
                uncertain(plan, PROJECTION_ACTION, detail, OperationState.EXECUTING);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
            }
            failAction(plan, PROJECTION_ACTION, detail);
            return compensate(plan, executedCosts, detail);
        }
        if (!bindingsMatch(plan)) {
            String detail = "Provider or configuration generation changed during external projection";
            uncertain(plan, PROJECTION_ACTION, detail, OperationState.EXECUTING);
            return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
        }
        java.util.HashSet<String> projectedManaged = new java.util.HashSet<>(
                outcome.value().orElseThrow().after().permanentContextFreeGroups());
        projectedManaged.retainAll(request.managedGroups());
        java.util.Set<String> expected = request.desiredGroup().map(java.util.Set::of).orElseGet(java.util.Set::of);
        if (!projectedManaged.equals(expected)) {
            String detail = "Rank adapter returned success without the exact pinned managed projection";
            uncertain(plan, PROJECTION_ACTION, detail, OperationState.EXECUTING);
            return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION, detail);
        }
        succeed(plan, PROJECTION_ACTION);
        verify(plan, PROJECTION_ACTION);
        return null;
    }

    private static String resultDetail(Result<?> result, String fallback) {
        return result.errors().stream().map(error -> error.code() + ": " + error.message())
                .reduce((left, right) -> left + "; " + right).orElse(fallback);
    }

    private RankUpExecutionResult failCosts(
            RankUpPlan plan,
            List<ExecutedCost> executedCosts,
            String pendingAction,
            String detail,
            boolean stale) {
        operations.transitionAction(plan.operationId(), pendingAction, ActionState.PENDING, ActionState.FAILED,
                Optional.of(detail));
        if (executedCosts.isEmpty()) {
            operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.FAILED);
            return result(plan, stale ? RankUpExecutionStatus.STALE_GENERATION : RankUpExecutionStatus.FAILED, detail);
        }
        return compensate(plan, executedCosts, detail);
    }

    private RankUpExecutionResult compensate(
            RankUpPlan plan,
            List<ExecutedCost> executedCosts,
            String reason) {
        if (executedCosts.isEmpty()) {
            operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.FAILED);
            return result(plan, RankUpExecutionStatus.FAILED, reason);
        }
        if (executedCosts.stream().anyMatch(executed -> !executed.cost.characteristics().reversible())) {
            operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.NEEDS_RECONCILIATION);
            return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                    reason + "; a prior consumed cost is not safely reversible");
        }
        operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.COMPENSATING);
        ArrayList<ExecutedCost> reversed = new ArrayList<>(executedCosts);
        Collections.reverse(reversed);
        for (ExecutedCost executed : reversed) {
            String compensationAction = RankUpPlan.compensationActionId(executed.cost.actionId());
            operations.transitionAction(plan.operationId(), compensationAction, ActionState.PENDING,
                    ActionState.STARTED, Optional.of(compensationEvidence(executed.cost.actionId(),
                            "STARTED", "Compensation call started", true)));
            ActionExecutionResult outcome;
            try {
                outcome = executed.provider.compensate(executed.cost).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                String evidence = compensationEvidence(executed.cost.actionId(), "UNCERTAIN",
                        "Compensation failed exceptionally: " + rootMessage(exception), true);
                operations.transitionAction(plan.operationId(), compensationAction, ActionState.STARTED,
                        ActionState.UNCERTAIN, Optional.of(evidence));
                operations.transition(plan.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        reason + "; cost compensation failed exceptionally: " + rootMessage(exception));
            }
            if (outcome.status() == ActionExecutionStatus.FAILED) {
                String evidence = compensationEvidence(executed.cost.actionId(), "FAILED",
                        outcome.detail().orElse("Compensation provider reported failure"), true);
                operations.transitionAction(plan.operationId(), compensationAction, ActionState.STARTED,
                        ActionState.FAILED, Optional.of(evidence));
                operations.transition(plan.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        reason + "; cost compensation failed");
            }
            if (outcome.status() == ActionExecutionStatus.UNCERTAIN) {
                String evidence = compensationEvidence(executed.cost.actionId(), "UNCERTAIN",
                        outcome.detail().orElse("Compensation effect is uncertain"), true);
                operations.transitionAction(plan.operationId(), compensationAction, ActionState.STARTED,
                        ActionState.UNCERTAIN, Optional.of(evidence));
                operations.transition(plan.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return result(plan, RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        reason + "; cost compensation effect is uncertain");
            }
            String evidence = compensationEvidence(executed.cost.actionId(), outcome.status().name(),
                    outcome.detail().orElse("Compensation completed"), false);
            operations.transitionAction(plan.operationId(), compensationAction, ActionState.STARTED,
                    ActionState.SUCCEEDED, Optional.of(evidence));
            operations.transitionAction(plan.operationId(), compensationAction, ActionState.SUCCEEDED,
                    ActionState.VERIFIED, Optional.of(evidence));
        }
        operations.transition(plan.operationId(), OperationState.COMPENSATING, OperationState.COMPENSATED);
        return result(plan, RankUpExecutionStatus.COMPENSATED, reason + "; all consumed costs were compensated");
    }

    private static String compensationEvidence(
            String originalAction,
            String result,
            String detail,
            boolean reconciliation) {
        String safeDetail = detail == null ? "" : detail.replaceAll("[\\p{Cntrl}]", "?");
        if (safeDetail.length() > 512) {
            safeDetail = safeDetail.substring(0, 512);
        }
        return "original=" + originalAction + "; result=" + result + "; reconciliation=" + reconciliation
                + "; detail=" + safeDetail;
    }

    private boolean bindingsMatch(RankUpPlan plan) {
        if (activeRevision.get().filter(plan.configRevision()::equals).isEmpty()) {
            return false;
        }
        return plan.providerGenerations().entrySet().stream()
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
                .orElseThrow(() -> new IllegalStateException("Pinned provider contract disappeared: " + id.value()));
    }

    private void start(RankUpPlan plan, String actionId) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.PENDING, ActionState.STARTED,
                Optional.empty());
    }

    private void succeed(RankUpPlan plan, String actionId) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED, ActionState.SUCCEEDED,
                Optional.empty());
    }

    private void verify(RankUpPlan plan, String actionId) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.SUCCEEDED, ActionState.VERIFIED,
                Optional.empty());
    }

    private void failAction(RankUpPlan plan, String actionId, String detail) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED, ActionState.FAILED,
                Optional.of(detail));
    }

    private void uncertain(RankUpPlan plan, String actionId, String detail, OperationState state) {
        operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED, ActionState.UNCERTAIN,
                Optional.of(detail));
        operations.transition(plan.operationId(), state, OperationState.NEEDS_RECONCILIATION);
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static RankUpExecutionResult result(
            RankUpPlan plan,
            RankUpExecutionStatus status,
            String detail) {
        return new RankUpExecutionResult(plan.operationId(), status, detail);
    }

    private void failPrepared(net.maddkraft.maddprestige.api.id.OperationId operationId) {
        try {
            operations.transition(operationId, OperationState.PREPARED, OperationState.FAILED);
        } catch (PersistenceException ignored) {
            // A concurrent exact owner may already have advanced it; its durable lease remains authoritative.
        }
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
