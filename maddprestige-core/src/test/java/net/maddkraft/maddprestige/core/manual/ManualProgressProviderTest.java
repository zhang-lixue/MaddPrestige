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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
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

    private static final class CountingRepository implements ManualProgressRepository {
        private final Map<UUID, ManualProgressRecord> records = new LinkedHashMap<>();
        private int writeBatches;
        private int writtenRecords;

        @Override
        public Map<UUID, ManualProgressRecord> load(ProviderId providerId, MetricId metricId) {
            return Map.copyOf(records);
        }

        @Override
        public void writeBatch(Collection<ManualProgressRecord> records) {
            writeBatches++;
            writtenRecords += records.size();
            records.forEach(record -> this.records.put(record.playerId(), record));
        }
    }
}
