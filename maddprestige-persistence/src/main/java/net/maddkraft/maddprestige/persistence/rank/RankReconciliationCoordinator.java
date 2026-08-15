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
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.RankOperationExecutionStatus;
import net.maddkraft.maddprestige.core.rank.RankProjectionOperationPlanner;
import net.maddkraft.maddprestige.core.rank.RankReconciliationDecision;
import net.maddkraft.maddprestige.core.rank.RankReconciliationResult;
import net.maddkraft.maddprestige.core.rank.RankReconciler;
import net.maddkraft.maddprestige.core.rank.ReconciliationAction;
import net.maddkraft.maddprestige.core.rank.ReconciliationRateGate;
import net.maddkraft.maddprestige.core.rank.ReconciliationStatus;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.persistence.AuditRepository;
import net.maddkraft.maddprestige.persistence.PlayerStageRepository;

public final class RankReconciliationCoordinator {
    private final PlayerStageRepository playerStages;
    private final AuditRepository audit;
    private final ProviderRegistry providers;
    private final RankProjectionOperationExecutor operationExecutor;
    private final RankReconciler reconciler = new RankReconciler();
    private final RankProjectionOperationPlanner planner = new RankProjectionOperationPlanner();
    private final ReconciliationRateGate rateGate;
    private final Supplier<Optional<StageConfigurationSnapshot>> activeConfiguration;
    private final Executor workerExecutor;
    private final Clock clock;

    public RankReconciliationCoordinator(
            PlayerStageRepository playerStages,
            AuditRepository audit,
            ProviderRegistry providers,
            RankProjectionOperationExecutor operationExecutor,
            ReconciliationRateGate rateGate,
            Supplier<Optional<StageConfigurationSnapshot>> activeConfiguration,
            Executor workerExecutor,
            Clock clock) {
        this.playerStages = Objects.requireNonNull(playerStages, "player stage repository");
        this.audit = Objects.requireNonNull(audit, "audit repository");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.operationExecutor = Objects.requireNonNull(operationExecutor, "operation executor");
        this.rateGate = Objects.requireNonNull(rateGate, "rate gate");
        this.activeConfiguration = Objects.requireNonNull(activeConfiguration, "active configuration supplier");
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "worker executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<RankReconciliationResult> reconcile(
            UUID playerId,
            StageConfigurationSnapshot configuration,
            RankAdapter adapter,
            long providerGeneration,
            Actor actor,
            String idempotencyKey) {
        Instant now = clock.instant();
        if (!rateGate.tryAcquire(playerId, now)) {
            return CompletableFuture.completedFuture(result(ReconciliationStatus.RATE_LIMITED,
                    "Reconciliation is bounded by the configured minimum interval.", Optional.empty(), Optional.empty()));
        }
        ProviderId providerId = adapter.descriptor().id();
        if (!configuration.configuration().rankProvider().filter(providerId::equals).isPresent()
                || !bindingMatches(providerId, adapter, providerGeneration)
                || !configurationMatches(configuration)) {
            return CompletableFuture.completedFuture(result(ReconciliationStatus.STALE_GENERATION,
                    "The provider binding or active configuration no longer matches the pinned generation.",
                    Optional.empty(), Optional.empty()));
        }
        return CompletableFuture.supplyAsync(() -> playerStages.find(playerId), workerExecutor)
                .thenCompose(internal -> adapter.readManagedState(
                        playerId, configuration.configuration().managedGroups(providerId))
                .handle((result, failure) -> failure == null ? result
                        : net.maddkraft.maddprestige.api.result.Result
                                .<net.maddkraft.maddprestige.api.rank.ManagedRankState>failure(
                                        net.maddkraft.maddprestige.api.result.StructuredError.unavailable(
                                                "rank.reconciliation.read_failed",
                                                failure.getMessage() == null ? failure.getClass().getSimpleName()
                                                        : failure.getMessage())))
                .thenCompose(read -> {
                    if (!read.isSuccess()) {
                        String detail = read.errors().stream().map(error -> error.message())
                                .reduce((left, right) -> left + "; " + right).orElse("Rank adapter unavailable");
                        return auditResult(playerId, actor, configuration, ReconciliationStatus.PROVIDER_UNAVAILABLE,
                                detail, Optional.empty(), Optional.empty(), AuditOutcome.FAILED);
                    }
                    RankReconciliationDecision decision = reconciler.assess(
                            configuration.configuration(), internal, read.value().orElseThrow());
                    return executeDecision(playerId, configuration, adapter, providerGeneration, actor,
                            idempotencyKey, internal, decision);
                }));
    }

    private CompletionStage<RankReconciliationResult> executeDecision(
            UUID playerId,
            StageConfigurationSnapshot configuration,
            RankAdapter adapter,
            long providerGeneration,
            Actor actor,
            String idempotencyKey,
            Optional<PlayerStageState> internal,
            RankReconciliationDecision decision) {
        if (!bindingMatches(adapter.descriptor().id(), adapter, providerGeneration)
                || !configurationMatches(configuration)) {
            return auditResult(playerId, actor, configuration, ReconciliationStatus.STALE_GENERATION,
                    "Provider generation or active configuration changed while reconciliation read external state.",
                    Optional.empty(), decision.targetStage(), AuditOutcome.FAILED);
        }
        if (decision.action() == ReconciliationAction.PROJECT_INTERNAL_STATE) {
            var operation = planner.plan("rank-reconciliation", actor, internal.orElseThrow(),
                    decision.targetStage().orElseThrow(), configuration, adapter.descriptor().id(),
                    providerGeneration, idempotencyKey, false);
            return operationExecutor.execute(operation, adapter).thenApply(execution -> {
                ReconciliationStatus status = execution.status() == RankOperationExecutionStatus.COMPLETED
                        ? ReconciliationStatus.REPAIRED
                        : execution.status() == RankOperationExecutionStatus.NEEDS_RECONCILIATION
                                ? ReconciliationStatus.UNCERTAIN : ReconciliationStatus.FAILED;
                return result(status, execution.detail(), Optional.of(execution.operationId()), decision.targetStage());
            });
        }
        if (decision.action() == ReconciliationAction.IMPORT_EXTERNAL_STATE) {
            return CompletableFuture.supplyAsync(() -> {
                if (!bindingMatches(adapter.descriptor().id(), adapter, providerGeneration)
                        || !configurationMatches(configuration)) {
                    String detail = "Provider generation or active configuration changed immediately before "
                            + "the import-once insert; no internal state was created.";
                    appendAudit(playerId, actor, configuration, ReconciliationStatus.STALE_GENERATION,
                            detail, decision.targetStage(), AuditOutcome.FAILED);
                    return result(ReconciliationStatus.STALE_GENERATION, detail,
                            Optional.empty(), decision.targetStage());
                }
                Instant now = clock.instant();
                PlayerStageState imported = new PlayerStageState(playerId, decision.targetStage().orElseThrow(), 0,
                        configuration.revisionId(), now, now, now, Optional.of(now),
                        Optional.of(providerGeneration), Optional.of(now));
                boolean inserted = playerStages.importOnce(imported);
                ReconciliationStatus status = inserted ? ReconciliationStatus.IMPORTED : ReconciliationStatus.WARNED;
                String detail = inserted ? "External managed rank seeded one internal stage record."
                        : "Import-once found an existing internal record and made no change.";
                appendAudit(playerId, actor, configuration, status, detail, decision.targetStage(),
                        inserted ? AuditOutcome.SUCCEEDED : AuditOutcome.DENIED);
                return result(status, detail, Optional.empty(), decision.targetStage());
            }, workerExecutor);
        }
        AuditOutcome outcome = decision.status() == ReconciliationStatus.MATCHED
                ? AuditOutcome.SUCCEEDED : AuditOutcome.DENIED;
        return auditResult(playerId, actor, configuration, decision.status(), decision.reason(),
                Optional.empty(), decision.targetStage(), outcome);
    }

    private CompletionStage<RankReconciliationResult> auditResult(
            UUID playerId,
            Actor actor,
            StageConfigurationSnapshot configuration,
            ReconciliationStatus status,
            String detail,
            Optional<net.maddkraft.maddprestige.api.id.OperationId> operationId,
            Optional<net.maddkraft.maddprestige.api.id.StageId> stageId,
            AuditOutcome outcome) {
        return CompletableFuture.supplyAsync(() -> {
            appendAudit(playerId, actor, configuration, status, detail, stageId, outcome);
            return result(status, detail, operationId, stageId);
        }, workerExecutor);
    }

    private void appendAudit(
            UUID playerId,
            Actor actor,
            StageConfigurationSnapshot configuration,
            ReconciliationStatus status,
            String detail,
            Optional<net.maddkraft.maddprestige.api.id.StageId> stageId,
            AuditOutcome outcome) {
        audit.append(new AuditRecord(UUID.randomUUID(), actor, Optional.of(playerId), Optional.empty(),
                Optional.of(configuration.revisionId()), "rank.reconciliation", Optional.empty(),
                stageId.map(id -> new AuditValue(id.value(), false)), "phase2-reconciliation", status.name(), outcome,
                outcome == AuditOutcome.SUCCEEDED ? Optional.empty() : Optional.of(detail), UUID.randomUUID(),
                clock.instant()));
    }

    private boolean bindingMatches(ProviderId providerId, RankAdapter adapter, long generation) {
        var snapshot = providers.find(providerId);
        return snapshot.isPresent()
                && providers.provider(providerId).filter(provider -> provider == adapter).isPresent()
                && snapshot.orElseThrow().generation() == generation
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state());
    }

    private boolean configurationMatches(StageConfigurationSnapshot configuration) {
        return activeConfiguration.get()
                .filter(active -> active.revisionId().equals(configuration.revisionId()))
                .isPresent();
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static RankReconciliationResult result(
            ReconciliationStatus status,
            String detail,
            Optional<net.maddkraft.maddprestige.api.id.OperationId> operationId,
            Optional<net.maddkraft.maddprestige.api.id.StageId> stageId) {
        return new RankReconciliationResult(status, detail, operationId, stageId);
    }
}
