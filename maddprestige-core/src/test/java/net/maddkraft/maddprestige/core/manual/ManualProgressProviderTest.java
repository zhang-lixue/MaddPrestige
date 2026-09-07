package net.maddkraft.maddprestige.core.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManualProgressProviderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A62][A65] Capability-owned high-volume increments aggregate without SQL/write-per-event")
    void aggregatesHighVolumeUpdates() {
        CountingRepository repository = new CountingRepository();
        ManualProgressBootstrap bootstrap = bootstrap(repository, Runnable::run, 4, 100);
        ManualMetricHandle handle = bootstrap.owner().registerMetric(definition("progress"))
                .toCompletableFuture().join();
        UUID player = UUID.randomUUID();
        for (int index = 0; index < 10_000; index++) {
            handle.increment(player, MetricValue.count(1), "server-event", CLOCK.instant());
        }
        assertEquals(0, repository.writeBatches);
        assertEquals(1, bootstrap.provider().flushAsync().toCompletableFuture().join());
        assertEquals(1, repository.writeBatches);
        assertEquals(1, repository.writtenRecords);
        assertEquals("10000", repository.records.values().iterator().next().value().canonical());
    }

    @Test
    @DisplayName("[A65] Identity strings, foreign capabilities, stale handles, and forged provenance are rejected")
    void enforcesOpaqueCapabilityScope() {
        CountingRepository repository = new CountingRepository();
        ManualProgressBootstrap first = bootstrap(repository, Runnable::run, 4, 1);
        assertThrows(SecurityException.class,
                () -> first.provider().registerMetric("owner", definition("progress")));
        ManualMetricHandle handle = first.owner().registerMetric(definition("progress"))
                .toCompletableFuture().join();
        UUID player = UUID.randomUUID();

        ManualProgressBootstrap second = bootstrap(new CountingRepository(), Runnable::run, 4, 10);
        assertThrows(SecurityException.class, () -> second.provider().increment(handle.registration(), player,
                MetricValue.count(1), second.owner().provenance("server-event", CLOCK.instant())));
        ProgressProvenance forged = new ProgressProvenance("owner", "forged", CLOCK.instant(), UUID.randomUUID());
        assertThrows(SecurityException.class, () -> first.provider().increment(handle.registration(), player,
                MetricValue.count(1), forged));
        ManualMetricRegistration stale = new ManualMetricRegistration(handle.registration().providerId(),
                handle.registration().metricId(), handle.registration().ownerIdentity(),
                handle.registration().generation() + 1, handle.registration().token());
        assertThrows(SecurityException.class, () -> first.provider().increment(stale, player,
                MetricValue.count(1), first.owner().provenance("server-event", CLOCK.instant())));

        handle.increment(player, MetricValue.count(1), "server-event", CLOCK.instant());
        assertThrows(IllegalStateException.class, () -> handle.increment(UUID.randomUUID(), MetricValue.count(1),
                "server-event", CLOCK.instant()));
        first.provider().closeAsync().toCompletableFuture().join();
        assertThrows(SecurityException.class, () -> handle.increment(player, MetricValue.count(1),
                "server-event", CLOCK.instant()));
        assertFalse(first.provider().descriptor().toString().contains(handle.registration().toString()));
    }

    @Test
    @DisplayName("[A65] Concurrent registrations cannot both consume the final bounded metric slot")
    void finalMetricSlotIsCommittedAtomically() {
        QueueExecutor executor = new QueueExecutor();
        ManualProgressBootstrap bootstrap = bootstrap(new CountingRepository(), executor, 1, 100);
        var first = bootstrap.owner().registerMetric(definition("first")).toCompletableFuture();
        var second = bootstrap.owner().registerMetric(definition("second")).toCompletableFuture();
        assertEquals(2, executor.size());
        executor.runAll();
        assertEquals(1, java.util.stream.Stream.of(first, second).filter(future -> !future.isCompletedExceptionally())
                .count());
        assertEquals(1, bootstrap.provider().metrics().size());
        assertTrue(first.isCompletedExceptionally() || second.isCompletedExceptionally());
    }

    @Test
    @DisplayName("[A65] Registration finishing after close is rejected at the synchronized commit point")
    void closeWinsRegistrationRace() {
        QueueExecutor executor = new QueueExecutor();
        ManualProgressBootstrap bootstrap = bootstrap(new CountingRepository(), executor, 2, 100);
        var registration = bootstrap.owner().registerMetric(definition("late")).toCompletableFuture();
        bootstrap.provider().closeAsync();
        executor.runAll();
        assertThrows(CompletionException.class, registration::join);
        assertTrue(bootstrap.provider().metrics().isEmpty());
    }

    @Test
    @DisplayName("[A65] Repeated flush ticks coalesce behind one drain and include updates made while queued")
    void flushIsSingleFlightAndCoalescesQueuedUpdates() {
        QueueExecutor executor = new QueueExecutor();
        CountingRepository repository = new CountingRepository();
        ManualProgressBootstrap bootstrap = bootstrap(repository, executor, 2, 100);
        var registration = bootstrap.owner().registerMetric(definition("coalesced")).toCompletableFuture();
        executor.runAll();
        ManualMetricHandle handle = registration.join();
        UUID player = UUID.randomUUID();
        handle.increment(player, MetricValue.count(1), "event", CLOCK.instant());

        var first = bootstrap.provider().flushAsync().toCompletableFuture();
        var second = bootstrap.provider().flushAsync().toCompletableFuture();
        assertEquals(first, second);
        assertEquals(1, executor.size());
        handle.increment(player, MetricValue.count(2), "event", CLOCK.instant());
        executor.runAll();

        assertEquals(1, repository.writeBatches);
        assertEquals("3", repository.records.get(player).value().canonical());
        assertEquals(1, first.join());
    }

    @Test
    @DisplayName("[A65] A failed flush retains dirty state and a later single-flight drain retries it")
    void failedFlushRetainsDirtyState() {
        AtomicBoolean fail = new AtomicBoolean(true);
        CountingRepository repository = new CountingRepository() {
            @Override
            public void writeBatch(Collection<ManualProgressRecord> records) {
                if (fail.getAndSet(false)) {
                    throw new IllegalStateException("simulated storage outage");
                }
                super.writeBatch(records);
            }
        };
        ManualProgressBootstrap bootstrap = bootstrap(repository, Runnable::run, 2, 100);
        ManualMetricHandle handle = bootstrap.owner().registerMetric(definition("retry"))
                .toCompletableFuture().join();
        UUID player = UUID.randomUUID();
        handle.increment(player, MetricValue.count(4), "event", CLOCK.instant());
        assertThrows(CompletionException.class, () -> bootstrap.provider().flushAsync().toCompletableFuture().join());
        assertEquals(ProviderHealthState.DEGRADED, bootstrap.provider().health().state());
        assertEquals("manual.persistence_stalled", bootstrap.provider().health().code());
        assertEquals(1, bootstrap.provider().flushAsync().toCompletableFuture().join());
        assertEquals(ProviderHealthState.AVAILABLE, bootstrap.provider().health().state());
        assertEquals("manual.available", bootstrap.provider().health().code());
        assertEquals("4", repository.records.get(player).value().canonical());
    }

    @Test
    @DisplayName("[A65] Large dirty sets are drained in bounded batches without losing entries")
    void flushBatchesAreBounded() {
        CountingRepository repository = new CountingRepository();
        ManualProgressBootstrap bootstrap = bootstrap(repository, Runnable::run, 2, 3_000);
        ManualMetricHandle handle = bootstrap.owner().registerMetric(definition("bounded"))
                .toCompletableFuture().join();
        for (int index = 0; index < 2_100; index++) {
            handle.increment(new UUID(0, index + 1L), MetricValue.count(1), "event", CLOCK.instant());
        }
        assertEquals(2_100, bootstrap.provider().flushAsync().toCompletableFuture().join());
        assertEquals(3, repository.writeBatches);
        assertEquals(List.of(1_024, 1_024, 52), repository.batchSizes);
        assertEquals(2_100, repository.records.size());
    }

    private static ManualProgressBootstrap bootstrap(
            CountingRepository repository,
            Executor executor,
            int maximumMetrics,
            int maximumEntries) {
        return ManualProgressProvider.bootstrap(new ProviderId("manual_test"), "owner", repository, executor,
                CLOCK, maximumMetrics, maximumEntries);
    }

    private static ManualCounterDefinition definition(String id) {
        return new ManualCounterDefinition(new MetricId(id), MetricValueType.COUNT, true, false,
                id, "Trusted progress", Map.of());
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private int size() {
            return tasks.size();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
    }

    private static class CountingRepository implements ManualProgressRepository {
        private final Map<UUID, ManualProgressRecord> records = new LinkedHashMap<>();
        private final List<Integer> batchSizes = new java.util.ArrayList<>();
        private int writeBatches;
        private int writtenRecords;

        @Override
        public Map<UUID, ManualProgressRecord> load(ProviderId providerId, MetricId metricId) {
            return Map.copyOf(records);
        }

        @Override
        public void writeBatch(Collection<ManualProgressRecord> records) {
            writeBatches++;
            batchSizes.add(records.size());
            writtenRecords += records.size();
            records.forEach(record -> this.records.put(record.playerId(), record));
        }
    }
}
