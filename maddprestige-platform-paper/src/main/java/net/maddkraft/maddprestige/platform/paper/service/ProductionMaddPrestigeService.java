package net.maddkraft.maddprestige.platform.paper.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.service.CurrencyBalanceView;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.api.service.OperationEvaluation;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.api.service.ProviderView;
import net.maddkraft.maddprestige.api.service.RequirementProgressView;
import net.maddkraft.maddprestige.api.service.SeasonView;
import net.maddkraft.maddprestige.api.service.ServiceError;
import net.maddkraft.maddprestige.api.service.ServiceResult;
import net.maddkraft.maddprestige.api.service.StageView;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.platform.paper.event.PaperOperationLifecycle;

/** Stable facade that translates every operational failure into the canonical structured result policy. */
public final class ProductionMaddPrestigeService implements MaddPrestigeService {
    private final Function<UUID, CompletionStage<PlayerProgressSnapshot>> progress;
    private final Supplier<List<StageView>> stages;
    private final Function<UUID, CompletionStage<OperationEvaluation>> rankEvaluation;
    private final Function<UUID, CompletionStage<OperationEvaluation>> prestigeEvaluation;
    private final Function<UUID, CompletionStage<List<CurrencyBalanceView>>> currencies;
    private final Function<UUID, CompletionStage<Optional<SeasonView>>> season;
    private final BiFunction<UUID, UUID, CompletionStage<OperationResult>> rankUp;
    private final BiFunction<UUID, UUID, CompletionStage<OperationResult>> prestige;
    private final ProviderRegistry providers;
    private final Predicate<UUID> dispatching;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public ProductionMaddPrestigeService(
            Function<UUID, CompletionStage<PlayerProgressSnapshot>> progress,
            Supplier<List<StageView>> stages,
            Function<UUID, CompletionStage<OperationEvaluation>> rankEvaluation,
            Function<UUID, CompletionStage<OperationEvaluation>> prestigeEvaluation,
            Function<UUID, CompletionStage<List<CurrencyBalanceView>>> currencies,
            Function<UUID, CompletionStage<Optional<SeasonView>>> season,
            BiFunction<UUID, UUID, CompletionStage<OperationResult>> rankUp,
            BiFunction<UUID, UUID, CompletionStage<OperationResult>> prestige,
            ProviderRegistry providers,
            PaperOperationLifecycle lifecycle) {
        this(progress, stages, rankEvaluation, prestigeEvaluation, currencies, season, rankUp, prestige, providers,
                Objects.requireNonNull(lifecycle, "operation lifecycle")::isDispatching);
    }

    ProductionMaddPrestigeService(
            Function<UUID, CompletionStage<PlayerProgressSnapshot>> progress,
            Supplier<List<StageView>> stages,
            Function<UUID, CompletionStage<OperationEvaluation>> rankEvaluation,
            Function<UUID, CompletionStage<OperationEvaluation>> prestigeEvaluation,
            Function<UUID, CompletionStage<List<CurrencyBalanceView>>> currencies,
            Function<UUID, CompletionStage<Optional<SeasonView>>> season,
            BiFunction<UUID, UUID, CompletionStage<OperationResult>> rankUp,
            BiFunction<UUID, UUID, CompletionStage<OperationResult>> prestige,
            ProviderRegistry providers,
            Predicate<UUID> dispatching) {
        this.progress = Objects.requireNonNull(progress, "progress function");
        this.stages = Objects.requireNonNull(stages, "stage catalog");
        this.rankEvaluation = Objects.requireNonNull(rankEvaluation, "rank-up evaluation");
        this.prestigeEvaluation = Objects.requireNonNull(prestigeEvaluation, "Prestige evaluation");
        this.currencies = Objects.requireNonNull(currencies, "currency read");
        this.season = Objects.requireNonNull(season, "season read");
        this.rankUp = Objects.requireNonNull(rankUp, "rank-up function");
        this.prestige = Objects.requireNonNull(prestige, "Prestige function");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.dispatching = Objects.requireNonNull(dispatching, "dispatching predicate");
    }

    @Override
    public CompletionStage<ServiceResult<PlayerProgressSnapshot>> playerProgress(UUID playerId) {
        return read(playerId, progress);
    }

    @Override
    public ServiceResult<List<StageView>> stages() {
        if (!accepting.get()) {
            return closed();
        }
        try {
            return ServiceResult.success(List.copyOf(stages.get()));
        } catch (RuntimeException | LinkageError failure) {
            return operationalFailure();
        }
    }

    @Override
    public CompletionStage<ServiceResult<OperationEvaluation>> evaluateRankUp(UUID playerId) {
        return read(playerId, rankEvaluation);
    }

    @Override
    public CompletionStage<ServiceResult<OperationEvaluation>> evaluatePrestige(UUID playerId) {
        return read(playerId, prestigeEvaluation);
    }

    @Override
    public CompletionStage<ServiceResult<List<RequirementProgressView>>> requirementProgress(UUID playerId) {
        Objects.requireNonNull(playerId, "player ID");
        if (!accepting.get()) {
            return CompletableFuture.completedFuture(closed());
        }
        CompletionStage<List<RequirementProgressView>> combined;
        try {
            combined = requireStage(rankEvaluation.apply(playerId)).thenCombine(
                    requireStage(prestigeEvaluation.apply(playerId)), (rank, prestigeResult) -> {
                        LinkedHashMap<String, RequirementProgressView> unique = new LinkedHashMap<>();
                        rank.requirements().forEach(value -> unique.put(
                                value.operation().name() + ':' + value.requirementId(), value));
                        prestigeResult.requirements().forEach(value -> unique.putIfAbsent(
                                value.operation().name() + ':' + value.requirementId(), value));
                        return List.copyOf(unique.values());
                    });
        } catch (RuntimeException | LinkageError failure) {
            return CompletableFuture.completedFuture(operationalFailure());
        }
        return detach(combined);
    }

    @Override
    public CompletionStage<ServiceResult<List<CurrencyBalanceView>>> currencies(UUID playerId) {
        return read(playerId, currencies);
    }

    @Override
    public CompletionStage<ServiceResult<Optional<SeasonView>>> activeSeason(UUID playerId) {
        return read(playerId, season);
    }

    @Override
    public CompletionStage<ServiceResult<OperationResult>> rankUp(UUID playerId) {
        return mutate(playerId, OperationKind.RANK_UP, rankUp);
    }

    @Override
    public CompletionStage<ServiceResult<OperationResult>> prestige(UUID playerId) {
        return mutate(playerId, OperationKind.PRESTIGE, prestige);
    }

    @Override
    public ServiceResult<List<ProviderView>> providers() {
        if (!accepting.get()) {
            return closed();
        }
        try {
            return ServiceResult.success(providers.snapshots().stream().map(snapshot -> new ProviderView(
                    snapshot.descriptor().id(), snapshot.health().state(),
                    snapshot.health().code(), snapshot.health().changedAt())).toList());
        } catch (RuntimeException | LinkageError failure) {
            return operationalFailure();
        }
    }

    /** Stops new work while allowing already accepted durable operations to reach their terminal outcome. */
    public void close() {
        accepting.set(false);
    }

    private <T> CompletionStage<ServiceResult<T>> read(
            UUID playerId,
            Function<UUID, CompletionStage<T>> operation) {
        Objects.requireNonNull(playerId, "player ID");
        if (!accepting.get()) {
            return CompletableFuture.completedFuture(closed());
        }
        try {
            return detach(requireStage(operation.apply(playerId)));
        } catch (RuntimeException | LinkageError failure) {
            return CompletableFuture.completedFuture(operationalFailure());
        }
    }

    private CompletionStage<ServiceResult<OperationResult>> mutate(
            UUID playerId,
            OperationKind kind,
            BiFunction<UUID, UUID, CompletionStage<OperationResult>> operation) {
        Objects.requireNonNull(playerId, "player ID");
        UUID requestId = UUID.randomUUID();
        if (!accepting.get()) {
            return CompletableFuture.completedFuture(closed());
        }
        try {
            if (dispatching.test(playerId)) {
                return CompletableFuture.completedFuture(ServiceResult.success(ServiceResults.conflict(requestId,
                        kind,
                        "operation.reentrant", "operation.reentrant")));
            }
            return detach(requireStage(operation.apply(playerId, requestId)));
        } catch (RuntimeException | LinkageError failure) {
            return CompletableFuture.completedFuture(operationalFailure());
        }
    }

    private static <T> CompletionStage<T> requireStage(CompletionStage<T> stage) {
        return Objects.requireNonNull(stage, "service completion stage");
    }

    private static <T> CompletionStage<ServiceResult<T>> detach(CompletionStage<T> stage) {
        CompletableFuture<ServiceResult<T>> detached = new CompletableFuture<>();
        stage.whenComplete((value, failure) -> {
            if (failure == null && value != null) {
                detached.complete(ServiceResult.success(value));
            } else {
                detached.complete(operationalFailure());
            }
        });
        return detached;
    }

    private static <T> ServiceResult<T> closed() {
        return ServiceResult.failure(new ServiceError("service.closed", "service.closed", Map.of()));
    }

    private static <T> ServiceResult<T> operationalFailure() {
        return ServiceResult.failure(new ServiceError("service.operation_failed", "service.operation_failed",
                Map.of()));
    }
}
