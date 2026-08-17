package net.maddkraft.maddprestige.persistence.recovery;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.cost.NativeRecoverableCostProvider;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.NativeRecoverableRewardProvider;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.stage.StageTransitionFence;
import net.maddkraft.maddprestige.persistence.OperationRepository;
import net.maddkraft.maddprestige.persistence.PrestigeLifecycleRepository;
import net.maddkraft.maddprestige.persistence.RecoveryEvent;
import net.maddkraft.maddprestige.persistence.RecoveryEventRepository;
import net.maddkraft.maddprestige.persistence.StoredOperation;
import net.maddkraft.maddprestige.persistence.StoredOperationAction;

/** Bounded startup recovery that never blindly replays an external action. */
public final class PendingOperationRecoveryService {
    private final OperationRepository operations;
    private final PrestigeLifecycleRepository prestige;
    private final RecoveryEventRepository events;
    private final Clock clock;
    private final ProviderRegistry providers;
    private final Optional<StageTransitionFence> transitionFence;

    public PendingOperationRecoveryService(
            OperationRepository operations,
            PrestigeLifecycleRepository prestige,
            RecoveryEventRepository events,
            Clock clock) {
        this(operations, prestige, events, clock, new ProviderRegistry(), Optional.empty());
    }

    public PendingOperationRecoveryService(
            OperationRepository operations,
            PrestigeLifecycleRepository prestige,
            RecoveryEventRepository events,
            Clock clock,
            ProviderRegistry providers) {
        this(operations, prestige, events, clock, providers, Optional.empty());
    }

    public PendingOperationRecoveryService(
            OperationRepository operations,
            PrestigeLifecycleRepository prestige,
            RecoveryEventRepository events,
            Clock clock,
            ProviderRegistry providers,
            StageTransitionFence transitionFence) {
        this(operations, prestige, events, clock, providers,
                Optional.of(java.util.Objects.requireNonNull(transitionFence, "stage transition fence")));
    }

    private PendingOperationRecoveryService(
            OperationRepository operations,
            PrestigeLifecycleRepository prestige,
            RecoveryEventRepository events,
            Clock clock,
            ProviderRegistry providers,
            Optional<StageTransitionFence> transitionFence) {
        this.operations = java.util.Objects.requireNonNull(operations, "operation repository");
        this.prestige = java.util.Objects.requireNonNull(prestige, "Prestige lifecycle repository");
        this.events = java.util.Objects.requireNonNull(events, "recovery event repository");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.providers = java.util.Objects.requireNonNull(providers, "provider registry");
        this.transitionFence = java.util.Objects.requireNonNull(transitionFence, "stage transition fence");
    }

    public List<RecoveryOutcome> recover(int limit) {
        transitionFence.ifPresent(fence -> fence.releaseTerminalLeases(clock.instant()));
        ArrayList<RecoveryOutcome> result = new ArrayList<>();
        for (StoredOperation operation : operations.findIncomplete(limit)) {
            result.add("prestige".equals(operation.operationType())
                    ? recoverPrestige(operation) : recoverGeneric(operation));
        }
        return List.copyOf(result);
    }

    /** Records an evidence-backed terminal reconciliation and releases its durable stage participation. */
    public RecoveryOutcome resolve(
            net.maddkraft.maddprestige.api.id.OperationId operationId,
            OperationState terminalState,
            String detail) {
        if (!terminal(terminalState)) {
            throw new IllegalArgumentException("Reconciliation resolution must be terminal");
        }
        if (detail == null || detail.isBlank() || detail.length() > 2048) {
            throw new IllegalArgumentException("Reconciliation detail must contain 1-2048 characters");
        }
        StoredOperation operation = operations.find(operationId).orElseThrow(() ->
                new IllegalArgumentException("Unknown operation: " + operationId));
        if (operation.state() != OperationState.NEEDS_RECONCILIATION) {
            throw new IllegalStateException("Operation does not require reconciliation: " + operationId);
        }
        operations.transition(operationId, OperationState.NEEDS_RECONCILIATION, terminalState);
        if ("prestige".equals(operation.operationType())) {
            prestige.recordResult(operationId, terminalState.name());
        }
        return outcome(operation, terminalState, "evidence-backed-terminal-resolution", detail, true);
    }

    private RecoveryOutcome recoverPrestige(StoredOperation operation) {
        List<StoredOperationAction> actions = operations.findActions(operation.operationId());
        if (operation.state() == OperationState.NEEDS_RECONCILIATION) {
            return outcome(operation, operation.state(), "retained",
                    "Manual reconciliation remains required; no action was replayed", false);
        }
        if (operation.state() == OperationState.PREPARED) {
            operations.transition(operation.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return outcome(operation, OperationState.FAILED, "abandoned-before-execution",
                    "Prepared operation had no consequential mutation and was failed safely", true);
        }
        if (operation.state() == OperationState.COMPENSATING) {
            markStartedUncertain(operation, actions);
            operations.transition(operation.operationId(), OperationState.COMPENSATING,
                    OperationState.NEEDS_RECONCILIATION);
            return outcome(operation, OperationState.NEEDS_RECONCILIATION, "compensation-reconciliation",
                    "Interrupted compensation was not replayed because its effect may be external", true);
        }
        boolean committed = prestige.internalCommitObserved(operation.operationId());
        if (operation.state() == OperationState.EXECUTING && committed) {
            reconcileInternalActions(operation, actions);
            operations.transition(operation.operationId(), OperationState.EXECUTING,
                    OperationState.STATE_COMMITTED);
            operation = new StoredOperation(operation.operationId(), operation.operationType(), operation.target(),
                    operation.idempotencyKey(), OperationState.STATE_COMMITTED, operation.createdAt(),
                    operation.updatedAt());
            actions = operations.findActions(operation.operationId());
        }
        if (operation.state() == OperationState.EXECUTING) {
            RecoveryOutcome compensated = recoverKnownNativeCosts(operation, actions);
            if (compensated != null) {
                return compensated;
            }
            boolean started = markStartedUncertain(operation, actions);
            operations.transition(operation.operationId(), OperationState.EXECUTING,
                    OperationState.NEEDS_RECONCILIATION);
            return outcome(operation, OperationState.NEEDS_RECONCILIATION,
                    started ? "uncertain-external-effect" : "known-incomplete-no-replay",
                    started ? "An action was in-flight; its effect is uncertain and was not replayed"
                            : "Internal commit is absent, but applied prerequisites cannot be reconstructed safely",
                    true);
        }
        if (operation.state() == OperationState.STATE_COMMITTED) {
            recoverKnownPostCommitActions(operation, actions);
            actions = operations.findActions(operation.operationId());
            boolean started = markStartedUncertain(operation, actions);
            actions = operations.findActions(operation.operationId());
            StoredOperation committedOperation = operation;
            boolean incompleteReward = actions.stream()
                    .anyMatch(action -> isUnresolvedReward(committedOperation, action));
            if (started || incompleteReward || actions.stream().anyMatch(
                    action -> action.state() == ActionState.UNCERTAIN)) {
                operations.transition(operation.operationId(), OperationState.STATE_COMMITTED,
                        OperationState.NEEDS_RECONCILIATION);
                prestige.recordResult(operation.operationId(), OperationState.NEEDS_RECONCILIATION.name());
                return outcome(operation, OperationState.NEEDS_RECONCILIATION, "post-commit-reconciliation",
                        "Authoritative state is committed; incomplete/uncertain external rewards were not replayed",
                        true);
            }
            operations.transition(operation.operationId(), OperationState.STATE_COMMITTED,
                    OperationState.COMPLETED);
            prestige.recordResult(operation.operationId(), OperationState.COMPLETED.name());
            return outcome(operation, OperationState.COMPLETED, "verified-completion",
                    "Committed internal evidence and verified actions allowed safe completion", true);
        }
        return outcome(operation, operation.state(), "retained", "No safe automatic recovery transition", false);
    }

    private RecoveryOutcome recoverGeneric(StoredOperation operation) {
        List<StoredOperationAction> actions = operations.findActions(operation.operationId());
        if (operation.state() == OperationState.NEEDS_RECONCILIATION) {
            return outcome(operation, operation.state(), "retained",
                    "Existing reconciliation state retained without replay", false);
        }
        if (operation.state() == OperationState.PREPARED) {
            operations.transition(operation.operationId(), OperationState.PREPARED, OperationState.FAILED);
            return outcome(operation, OperationState.FAILED, "abandoned-before-execution",
                    "Prepared generic operation was failed without mutation", true);
        }
        markStartedUncertain(operation, actions);
        if (operation.state() == OperationState.EXECUTING
                || operation.state() == OperationState.STATE_COMMITTED
                || operation.state() == OperationState.COMPENSATING) {
            operations.transition(operation.operationId(), operation.state(), OperationState.NEEDS_RECONCILIATION);
            return outcome(operation, OperationState.NEEDS_RECONCILIATION, "generic-no-blind-replay",
                    "Persisted action state was retained for operator reconciliation", true);
        }
        return outcome(operation, operation.state(), "retained", "No safe generic recovery transition", false);
    }

    private boolean markStartedUncertain(
            StoredOperation operation,
            List<StoredOperationAction> actions) {
        boolean changed = false;
        for (StoredOperationAction action : actions) {
            if (action.state() == ActionState.STARTED) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.STARTED,
                        ActionState.UNCERTAIN, Optional.of(
                                "Recovery observed an interrupted in-flight action; no replay attempted"));
                changed = true;
            }
        }
        return changed;
    }

    private void reconcileInternalActions(
            StoredOperation operation,
            List<StoredOperationAction> actions) {
        for (StoredOperationAction action : actions) {
            if (!isInternalCommitAction(action.actionId())) {
                continue;
            }
            ActionState state = action.state();
            if (state == ActionState.PENDING) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.PENDING,
                        ActionState.STARTED, Optional.of("Recovery found committed internal evidence"));
                state = ActionState.STARTED;
            }
            if (state == ActionState.STARTED) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.STARTED,
                        ActionState.SUCCEEDED, Optional.of("Recovery found committed internal evidence"));
                state = ActionState.SUCCEEDED;
            }
            if (state == ActionState.SUCCEEDED) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.SUCCEEDED,
                        ActionState.VERIFIED, Optional.of("Recovery verified committed internal evidence"));
            }
        }
    }

    private void recoverKnownPostCommitActions(
            StoredOperation operation,
            List<StoredOperationAction> actions) {
        for (StoredOperationAction action : actions) {
            if (!"reward".equals(action.actionType())) {
                continue;
            }
            if (action.state() == ActionState.SUCCEEDED) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.SUCCEEDED,
                        ActionState.VERIFIED, Optional.of("Recovery verified persisted provider success"));
                continue;
            }
            if (action.state() != ActionState.PENDING && action.state() != ActionState.STARTED) {
                continue;
            }
            var planned = prestige.findRecoveryReward(operation.operationId(), action.actionId());
            if (planned.isEmpty() || !nativeBindingMatches(planned.orElseThrow()) || !action.idempotent()
                    || !planned.orElseThrow().characteristics().idempotent()
                    || planned.orElseThrow().characteristics().externalUncertaintyPossible()) {
                continue;
            }
            if (action.state() == ActionState.PENDING) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.PENDING,
                        ActionState.STARTED, Optional.of("Recovery is applying exact sealed native reward"));
            }
            net.maddkraft.maddprestige.api.action.ActionExecutionResult result;
            try {
                NativeRecoverableRewardProvider provider = providers.provider(action.providerId())
                        .filter(NativeRecoverableRewardProvider.class::isInstance)
                        .map(NativeRecoverableRewardProvider.class::cast).orElseThrow();
                result = provider.execute(planned.orElseThrow()).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.STARTED,
                        ActionState.UNCERTAIN, Optional.of("Native recovery call failed: " + rootMessage(exception)));
                continue;
            }
            if (result.status() == ActionExecutionStatus.APPLIED
                    || result.status() == ActionExecutionStatus.UNCHANGED) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.STARTED,
                        ActionState.SUCCEEDED, Optional.of("Exact native action applied or idempotently replayed"));
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.SUCCEEDED,
                        ActionState.VERIFIED, Optional.of("Native ledger effect verified by operation/action ID"));
            } else if (result.status() == ActionExecutionStatus.FAILED) {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.STARTED,
                        ActionState.FAILED, result.detail());
            } else {
                operations.transitionAction(operation.operationId(), action.actionId(), ActionState.STARTED,
                        ActionState.UNCERTAIN, result.detail());
            }
        }
    }

    private RecoveryOutcome recoverKnownNativeCosts(
            StoredOperation operation,
            List<StoredOperationAction> actions) {
        List<StoredOperationAction> appliedCosts = actions.stream().filter(action -> "cost".equals(action.actionType()))
                .filter(action -> action.state() == ActionState.SUCCEEDED || action.state() == ActionState.VERIFIED)
                .toList();
        if (appliedCosts.isEmpty()) {
            return null;
        }
        boolean allRecoverable = appliedCosts.stream().allMatch(action -> {
            var planned = prestige.findRecoveryCost(operation.operationId(), action.actionId());
            return action.reversible() && action.idempotent() && planned.isPresent()
                    && planned.orElseThrow().characteristics().reversible()
                    && planned.orElseThrow().characteristics().idempotent()
                    && !planned.orElseThrow().characteristics().externalUncertaintyPossible()
                    && nativeCostBindingMatches(planned.orElseThrow());
        });
        if (!allRecoverable) {
            return null;
        }
        boolean unresolvedCostEffect = actions.stream()
                .filter(action -> "cost".equals(action.actionType()))
                .anyMatch(action -> action.state() == ActionState.STARTED
                        || action.state() == ActionState.UNCERTAIN);
        operations.transition(operation.operationId(), OperationState.EXECUTING, OperationState.COMPENSATING);
        ArrayList<StoredOperationAction> reverse = new ArrayList<>(appliedCosts);
        java.util.Collections.reverse(reverse);
        for (StoredOperationAction action : reverse) {
            var planned = prestige.findRecoveryCost(operation.operationId(), action.actionId()).orElseThrow();
            String compensationId = "compensate-" + action.actionId();
            operations.transitionAction(operation.operationId(), compensationId, ActionState.PENDING,
                    ActionState.STARTED, Optional.of("Recovery is compensating exact sealed native cost"));
            net.maddkraft.maddprestige.api.action.ActionExecutionResult result;
            try {
                NativeRecoverableCostProvider provider = providers.provider(action.providerId())
                        .filter(NativeRecoverableCostProvider.class::isInstance)
                        .map(NativeRecoverableCostProvider.class::cast).orElseThrow();
                result = provider.compensate(planned).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                operations.transitionAction(operation.operationId(), compensationId, ActionState.STARTED,
                        ActionState.UNCERTAIN, Optional.of("Native cost recovery failed: " + rootMessage(exception)));
                operations.transition(operation.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return outcome(operation, OperationState.NEEDS_RECONCILIATION, "native-cost-compensation-uncertain",
                        "Exact native cost compensation did not reach a verified result", true);
            }
            if (result.status() != ActionExecutionStatus.APPLIED
                    && result.status() != ActionExecutionStatus.UNCHANGED) {
                ActionState replacement = result.status() == ActionExecutionStatus.FAILED
                        ? ActionState.FAILED : ActionState.UNCERTAIN;
                operations.transitionAction(operation.operationId(), compensationId, ActionState.STARTED,
                        replacement, result.detail());
                operations.transition(operation.operationId(), OperationState.COMPENSATING,
                        OperationState.NEEDS_RECONCILIATION);
                return outcome(operation, OperationState.NEEDS_RECONCILIATION, "native-cost-compensation-failed",
                        "Exact native cost compensation failed or remained uncertain", true);
            }
            operations.transitionAction(operation.operationId(), compensationId, ActionState.STARTED,
                    ActionState.SUCCEEDED, Optional.of("Native compensation applied or idempotently replayed"));
            operations.transitionAction(operation.operationId(), compensationId, ActionState.SUCCEEDED,
                    ActionState.VERIFIED, Optional.of("Native compensation verified by operation/action ID"));
            operations.transitionAction(operation.operationId(), action.actionId(), action.state(),
                    ActionState.COMPENSATED, Optional.of("Recovery verified exact native compensation"));
        }
        if (unresolvedCostEffect) {
            markStartedUncertain(operation, operations.findActions(operation.operationId()));
            operations.transition(operation.operationId(), OperationState.COMPENSATING,
                    OperationState.NEEDS_RECONCILIATION);
            return outcome(operation, OperationState.NEEDS_RECONCILIATION,
                    "native-costs-compensated-external-cost-unresolved",
                    "Known native costs were compensated, but another potentially applied cost remains uncertain",
                    true);
        }
        operations.transition(operation.operationId(), OperationState.COMPENSATING, OperationState.COMPENSATED);
        return outcome(operation, OperationState.COMPENSATED, "native-costs-compensated",
                "Known applied native costs were compensated exactly once; internal Prestige state was absent", true);
    }

    private boolean nativeBindingMatches(net.maddkraft.maddprestige.api.reward.PlannedReward reward) {
        var snapshot = providers.find(reward.definition().providerId());
        return snapshot.isPresent() && providers.provider(reward.definition().providerId())
                .filter(NativeRecoverableRewardProvider.class::isInstance).isPresent()
                && snapshot.orElseThrow().generation() == reward.providerGeneration()
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state());
    }

    private boolean nativeCostBindingMatches(net.maddkraft.maddprestige.api.cost.PlannedCost cost) {
        var snapshot = providers.find(cost.definition().providerId());
        return snapshot.isPresent() && providers.provider(cost.definition().providerId())
                .filter(NativeRecoverableCostProvider.class::isInstance).isPresent()
                && snapshot.orElseThrow().generation() == cost.providerGeneration()
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state());
    }

    private boolean isUnresolvedReward(StoredOperation operation, StoredOperationAction action) {
        if (!"reward".equals(action.actionType()) || action.state() == ActionState.VERIFIED) {
            return false;
        }
        var planned = prestige.findRecoveryReward(operation.operationId(), action.actionId());
        return action.state() != ActionState.FAILED || planned.isEmpty()
                || planned.orElseThrow().definition().failurePolicy() == RewardFailurePolicy.REQUIRED;
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static boolean isInternalCommitAction(String actionId) {
        return "prestige-state-commit".equals(actionId) || actionId.startsWith("currency-reset-")
                || actionId.startsWith("milestone-");
    }

    private RecoveryOutcome outcome(
            StoredOperation operation,
            OperationState resultingState,
            String decision,
            String detail,
            boolean persist) {
        if (persist) {
            events.append(new RecoveryEvent(UUID.randomUUID(), operation.operationId(), operation.state(),
                    resultingState, decision, detail, clock.instant()));
        }
        if (terminal(resultingState)) {
            transitionFence.ifPresent(fence -> fence.release(operation.operationId(), clock.instant()));
        }
        return new RecoveryOutcome(operation.operationId(), resultingState, decision, detail);
    }

    private static boolean terminal(OperationState state) {
        return state == OperationState.COMPLETED || state == OperationState.COMPENSATED
                || state == OperationState.FAILED;
    }
}
