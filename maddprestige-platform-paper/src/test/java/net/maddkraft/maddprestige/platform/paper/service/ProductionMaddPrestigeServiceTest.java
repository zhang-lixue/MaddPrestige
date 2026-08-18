package net.maddkraft.maddprestige.platform.paper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationEvaluation;
import net.maddkraft.maddprestige.api.service.OperationEvaluationStatus;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;

class ProductionMaddPrestigeServiceTest {
    private static final UUID PLAYER = UUID.fromString("418731d5-e571-49e0-8c76-8960365184c3");

    @Test
    void callerCancellationDoesNotCancelAcceptedDurableWork() {
        CompletableFuture<OperationResult> durable = new CompletableFuture<>();
        ProductionMaddPrestigeService service = service((ignored, requestId) -> durable, ignored -> false);

        CompletableFuture<net.maddkraft.maddprestige.api.service.ServiceResult<OperationResult>> caller =
                service.rankUp(PLAYER).toCompletableFuture();
        caller.cancel(false);

        assertFalse(durable.isCancelled());
        durable.complete(ServiceResults.blocked(OperationKind.RANK_UP, "test.complete", "complete"));
        assertEquals(OperationStatus.BLOCKED, durable.join().status());
    }

    @Test
    void closeRejectsNewReadsAndMutationsWithBoundedOutcomes() {
        ProductionMaddPrestigeService service = service(
                (ignored, requestId) -> CompletableFuture.completedFuture(ServiceResults.blocked(requestId,
                        OperationKind.RANK_UP, "test.unused", "unused")), ignored -> false);
        service.close();

        assertEquals("service.closed", service.rankUp(PLAYER).toCompletableFuture().join()
                .error().orElseThrow().code());
        assertEquals("service.closed", service.prestige(PLAYER).toCompletableFuture().join()
                .error().orElseThrow().code());
        assertEquals("service.closed", service.playerProgress(PLAYER).toCompletableFuture().join()
                .error().orElseThrow().code());
    }

    @Test
    void samePlayerListenerReentrancyFailsBeforeCallingMutationEngine() {
        AtomicInteger calls = new AtomicInteger();
        ProductionMaddPrestigeService service = service((player, requestId) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(ServiceResults.blocked(requestId,
                    OperationKind.RANK_UP, "test.unused", "unused"));
        }, PLAYER::equals);

        assertEquals(OperationStatus.CONFLICT, service.rankUp(PLAYER).toCompletableFuture().join()
                .value().orElseThrow().status());
        assertEquals(OperationStatus.CONFLICT, service.prestige(PLAYER).toCompletableFuture().join()
                .value().orElseThrow().status());
        assertEquals(0, calls.get());
    }

    @Test
    void providerViewsUseOnlyRegistryCachedMetadataAndHealth() {
        ProviderRegistry registry = new ProviderRegistry();
        CountingProvider provider = new CountingProvider();
        var registration = registry.register("fixture", provider);
        registry.activate(registration);
        int descriptors = provider.descriptors.get();
        int healthReads = provider.healthReads.get();
        ProductionMaddPrestigeService service = service(registry,
                (ignored, requestId) -> CompletableFuture.completedFuture(ServiceResults.blocked(requestId,
                        OperationKind.RANK_UP, "test.unused", "unused")), ignored -> false);

        assertEquals(1, service.providers().value().orElseThrow().size());
        assertEquals(descriptors, provider.descriptors.get());
        assertEquals(healthReads, provider.healthReads.get());
    }

    @Test
    void operationalFailuresNeverEscapeStableServiceFuturesOrSynchronousReads() {
        ProductionMaddPrestigeService failedMutation = service(
                (ignored, requestId) -> CompletableFuture.failedFuture(new IllegalStateException("database failure")),
                ignored -> false);
        assertEquals("service.operation_failed", failedMutation.rankUp(PLAYER).toCompletableFuture().join()
                .error().orElseThrow().code());

        ProductionMaddPrestigeService failedReentrancyCheck = service(
                (ignored, requestId) -> CompletableFuture.completedFuture(ServiceResults.blocked(requestId,
                        OperationKind.RANK_UP, "test.unused", "test.unused")),
                ignored -> {
                    throw new IllegalStateException("lifecycle failure");
                });
        assertEquals("service.operation_failed", failedReentrancyCheck.rankUp(PLAYER).toCompletableFuture().join()
                .error().orElseThrow().code());
    }

    @Test
    void publicIngressAllocatesAndPreservesOneRequestIdBeforeMutation() {
        AtomicReference<UUID> observed = new AtomicReference<>();
        OperationId durable = OperationId.random();
        ProductionMaddPrestigeService service = service((player, requestId) -> {
            observed.set(requestId);
            return CompletableFuture.completedFuture(new OperationResult(requestId, Optional.of(durable),
                    OperationKind.RANK_UP, OperationStatus.COMPLETED, Optional.empty()));
        }, ignored -> false);

        OperationResult result = service.rankUp(PLAYER).toCompletableFuture().join().value().orElseThrow();

        assertEquals(observed.get(), result.requestId());
        assertFalse(result.requestId().equals(result.durableOperationId().orElseThrow().value()));
    }

    @Test
    void requestIdentitySurvivesBlockedFailedAndReconciliationOutcomeShapes() {
        for (OperationStatus status : List.of(OperationStatus.BLOCKED, OperationStatus.FAILED,
                OperationStatus.NEEDS_RECONCILIATION)) {
            AtomicReference<UUID> observed = new AtomicReference<>();
            OperationId durable = OperationId.random();
            ProductionMaddPrestigeService service = service((player, requestId) -> {
                observed.set(requestId);
                Optional<OperationId> durableId = status == OperationStatus.BLOCKED
                        ? Optional.empty() : Optional.of(durable);
                return CompletableFuture.completedFuture(new OperationResult(requestId, durableId,
                        OperationKind.RANK_UP, status, Optional.of(new net.maddkraft.maddprestige.api.service
                                .ServiceError("test.outcome", "test.outcome", Map.of()))));
            }, ignored -> false);

            OperationResult result = service.rankUp(PLAYER).toCompletableFuture().join().value().orElseThrow();
            assertEquals(observed.get(), result.requestId());
            assertEquals(status, result.status());
            result.durableOperationId().ifPresent(value -> assertFalse(result.requestId()
                    .equals(value.value())));
        }
    }

    private static ProductionMaddPrestigeService service(
            java.util.function.BiFunction<UUID, UUID,
                    java.util.concurrent.CompletionStage<OperationResult>> mutation,
            java.util.function.Predicate<UUID> dispatching) {
        return service(new ProviderRegistry(), mutation, dispatching);
    }

    private static ProductionMaddPrestigeService service(
            ProviderRegistry registry,
            java.util.function.BiFunction<UUID, UUID,
                    java.util.concurrent.CompletionStage<OperationResult>> mutation,
            java.util.function.Predicate<UUID> dispatching) {
        return new ProductionMaddPrestigeService(
                player -> CompletableFuture.completedFuture(new PlayerProgressSnapshot(player, Optional.empty(),
                        0, 0, Optional.empty(), Map.of(), Instant.EPOCH)), List::of,
                player -> CompletableFuture.completedFuture(evaluation(OperationKind.RANK_UP)),
                player -> CompletableFuture.completedFuture(evaluation(OperationKind.PRESTIGE)),
                player -> CompletableFuture.completedFuture(List.of()),
                player -> CompletableFuture.completedFuture(Optional.empty()), mutation, mutation, registry,
                dispatching);
    }

    private static OperationEvaluation evaluation(OperationKind kind) {
        return new OperationEvaluation(kind, OperationEvaluationStatus.UNAVAILABLE, Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty(), Instant.EPOCH);
    }

    private static final class CountingProvider implements Provider {
        private static final ProviderId ID = new ProviderId("service_fixture");
        private final AtomicInteger descriptors = new AtomicInteger();
        private final AtomicInteger healthReads = new AtomicInteger();

        @Override
        public ProviderDescriptor descriptor() {
            descriptors.incrementAndGet();
            return new ProviderDescriptor(ID, "fixture", "test", "1", List.of(), List.of());
        }

        @Override
        public ProviderHealth health() {
            healthReads.incrementAndGet();
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "test.available", "available", Instant.EPOCH);
        }
    }
}
