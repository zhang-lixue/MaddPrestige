package net.maddkraft.maddprestige.persistence.rank;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.audit.AuditOutcome;
import net.maddkraft.maddprestige.api.audit.AuditRecord;
import net.maddkraft.maddprestige.api.audit.AuditValue;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.RankOperationExecution;
import net.maddkraft.maddprestige.core.rank.RankOperationExecutionStatus;
import net.maddkraft.maddprestige.core.rank.RankProjectionOperation;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.persistence.AuditRepository;
import net.maddkraft.maddprestige.persistence.OperationRepository;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.PlayerStageRepository;

public final class RankProjectionOperationExecutor {
    private static final String ACTION_ID = "rank-projection";
    private final OperationRepository operations;
    private final PlayerStageRepository playerStages;
    private final AuditRepository audit;
    private final ProviderRegistry providers;
    private final Supplier<Optional<StageConfigurationSnapshot>> activeConfiguration;
    private final Executor workerExecutor;
    private final Clock clock;

    public RankProjectionOperationExecutor(
            OperationRepository operations,
            PlayerStageRepository playerStages,
            AuditRepository audit,
            ProviderRegistry providers,
            Supplier<Optional<StageConfigurationSnapshot>> activeConfiguration,
            Executor workerExecutor,
            Clock clock) {
        this.operations = Objects.requireNonNull(operations, "operation repository");
        this.playerStages = Objects.requireNonNull(playerStages, "player stage repository");
        this.audit = Objects.requireNonNull(audit, "audit repository");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.activeConfiguration = Objects.requireNonNull(activeConfiguration, "active configuration supplier");
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "worker executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<RankOperationExecution> execute(
            RankProjectionOperation operation,
            RankAdapter adapter) {
        Objects.requireNonNull(operation, "projection operation");
        Objects.requireNonNull(adapter, "rank adapter");
        return CompletableFuture.supplyAsync(() -> prepare(operation, adapter), workerExecutor)
                .thenCompose(prepared -> {
                    if (prepared != null) {
                        return CompletableFuture.completedFuture(prepared);
                    }
                    return adapter.project(operation.projectionRequest())
                            .handle((result, failure) -> failure == null
                                    ? result
                                    : Result.<net.maddkraft.maddprestige.api.rank.RankProjectionResult>failure(
                                            new StructuredError("rank.projection.exception", ErrorCategory.UNCERTAIN,
                                                    rootMessage(failure), java.util.Map.of())))
                            .thenCompose(result -> CompletableFuture.supplyAsync(
                                    () -> finish(operation, result), workerExecutor));
                });
    }

    private RankOperationExecution prepare(RankProjectionOperation operation, RankAdapter adapter) {
        var existing = operations.findByIdempotency(operation.plan().operationType(), operation.plan().target(),
                operation.plan().idempotencyKey());
        if (existing.isPresent()) {
            return new RankOperationExecution(operation.plan().id(), RankOperationExecutionStatus.DUPLICATE,
                    "An operation with the same target/type/idempotency key already exists as "
                            + existing.orElseThrow().operationId() + ".");
        }
        try {
            operations.insertPrepared(operation.plan());
        } catch (PersistenceException exception) {
            var raced = operations.findByIdempotency(operation.plan().operationType(), operation.plan().target(),
                    operation.plan().idempotencyKey());
            RankOperationExecutionStatus status = raced.isPresent()
                    ? RankOperationExecutionStatus.DUPLICATE : RankOperationExecutionStatus.FAILED;
            return new RankOperationExecution(operation.plan().id(), status,
                    "Operation could not be prepared safely: " + exception.getMessage());
        }
        if (!bindingMatches(operation, adapter)) {
            operations.transition(operation.plan().id(), OperationState.PREPARED, OperationState.FAILED);
            appendAudit(operation, AuditOutcome.FAILED, "Pinned provider/configuration generation is stale.");
            return new RankOperationExecution(operation.plan().id(), RankOperationExecutionStatus.FAILED,
                    "Pinned provider or configuration generation changed before projection.");
        }
        operations.transition(operation.plan().id(), OperationState.PREPARED, OperationState.EXECUTING);
        operations.transitionAction(operation.plan().id(), ACTION_ID, ActionState.PENDING, ActionState.STARTED,
                Optional.empty());
        return null;
    }

    private RankOperationExecution finish(
            RankProjectionOperation operation,
            Result<net.maddkraft.maddprestige.api.rank.RankProjectionResult> result) {
        if (!result.isSuccess()) {
            String detail = result.errors().stream().map(StructuredError::message)
                    .reduce((left, right) -> left + "; " + right).orElse("Rank projection failed");
            boolean uncertain = result.errors().stream().anyMatch(error -> error.category() == ErrorCategory.UNCERTAIN);
            if (uncertain) {
                operations.transitionAction(operation.plan().id(), ACTION_ID, ActionState.STARTED,
                        ActionState.UNCERTAIN, Optional.of(detail));
                operations.transition(operation.plan().id(), OperationState.EXECUTING,
                        OperationState.NEEDS_RECONCILIATION);
                appendAudit(operation, AuditOutcome.NEEDS_RECONCILIATION, detail);
                return new RankOperationExecution(operation.plan().id(),
                        RankOperationExecutionStatus.NEEDS_RECONCILIATION, detail);
            }
            operations.transitionAction(operation.plan().id(), ACTION_ID, ActionState.STARTED,
                    ActionState.FAILED, Optional.of(detail));
            operations.transition(operation.plan().id(), OperationState.EXECUTING, OperationState.FAILED);
            appendAudit(operation, AuditOutcome.FAILED, detail);
            return new RankOperationExecution(operation.plan().id(), RankOperationExecutionStatus.FAILED, detail);
        }

        operations.transitionAction(operation.plan().id(), ACTION_ID, ActionState.STARTED,
                ActionState.SUCCEEDED, Optional.empty());
        if (!bindingMatches(operation, providers.provider(operation.plan().actions().getFirst().providerId())
                .filter(RankAdapter.class::isInstance).map(RankAdapter.class::cast).orElse(null))) {
            return uncertainAfterEffect(operation, "Provider or configuration generation changed during projection.");
        }
        if (operation.updateInternalStage()) {
            try {
                var replacement = operation.expectedPlayerState().advanceTo(operation.targetStage(),
                        operation.plan().configRevision(), operation.projectionRequest().providerGeneration(),
                        clock.instant());
                playerStages.update(replacement, operation.expectedPlayerState().stateRevision());
            } catch (RuntimeException exception) {
                return uncertainAfterEffect(operation,
                        "External projection succeeded but internal state commit failed: " + exception.getMessage());
            }
        }
        operations.transitionAction(operation.plan().id(), ACTION_ID, ActionState.SUCCEEDED,
                ActionState.VERIFIED, Optional.empty());
        operations.transition(operation.plan().id(), OperationState.EXECUTING, OperationState.STATE_COMMITTED);
        try {
            appendAudit(operation, AuditOutcome.SUCCEEDED, "Pinned projection verified and committed.");
        } catch (RuntimeException exception) {
            operations.transition(operation.plan().id(), OperationState.STATE_COMMITTED,
                    OperationState.NEEDS_RECONCILIATION);
            return new RankOperationExecution(operation.plan().id(),
                    RankOperationExecutionStatus.NEEDS_RECONCILIATION,
                    "Projection committed but audit persistence failed: " + exception.getMessage());
        }
        operations.transition(operation.plan().id(), OperationState.STATE_COMMITTED, OperationState.COMPLETED);
        return new RankOperationExecution(operation.plan().id(), RankOperationExecutionStatus.COMPLETED,
                "Pinned rank projection completed and verified.");
    }

    private RankOperationExecution uncertainAfterEffect(RankProjectionOperation operation, String detail) {
        operations.transitionAction(operation.plan().id(), ACTION_ID, ActionState.SUCCEEDED,
                ActionState.UNCERTAIN, Optional.of(detail));
        operations.transition(operation.plan().id(), OperationState.EXECUTING,
                OperationState.NEEDS_RECONCILIATION);
        appendAudit(operation, AuditOutcome.NEEDS_RECONCILIATION, detail);
        return new RankOperationExecution(operation.plan().id(),
                RankOperationExecutionStatus.NEEDS_RECONCILIATION, detail);
    }

    private boolean bindingMatches(RankProjectionOperation operation, RankAdapter adapter) {
        if (adapter == null) {
            return false;
        }
        var providerId = operation.plan().actions().getFirst().providerId();
        var snapshot = providers.find(providerId);
        var currentProvider = providers.provider(providerId);
        var active = activeConfiguration.get();
        return snapshot.isPresent()
                && currentProvider.filter(provider -> provider == adapter).isPresent()
                && snapshot.orElseThrow().generation() == operation.projectionRequest().providerGeneration()
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state())
                && active.filter(configuration -> configuration.revisionId().equals(
                        operation.projectionRequest().configRevision())).isPresent();
    }

    private void appendAudit(RankProjectionOperation operation, AuditOutcome outcome, String detail) {
        Instant now = clock.instant();
        audit.append(new AuditRecord(UUID.randomUUID(), operation.plan().actor(),
                Optional.of(operation.plan().target()), Optional.of(operation.plan().id()),
                Optional.of(operation.plan().configRevision()), "rank.projection",
                Optional.of(new AuditValue(operation.expectedPlayerState().stageId().value(), false)),
                Optional.of(new AuditValue(operation.targetStage().value(), false)), "phase2-rank-operation",
                operation.plan().operationType(), outcome,
                outcome == AuditOutcome.SUCCEEDED ? Optional.empty() : Optional.of(detail),
                operation.plan().id().value(), now));
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
}
