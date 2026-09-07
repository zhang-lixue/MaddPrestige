package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.core.manual.ManualCounterDefinition;
import net.maddkraft.maddprestige.core.manual.ManualProgressProvider;
import net.maddkraft.maddprestige.core.manual.ProgressProvenance;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.LatchKey;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementLatch;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteProgressionStateTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);
    @TempDir
    Path temporaryDirectory;
    private SqliteFoundation sqlite;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("progression.db");
        sqlite = new SqliteFoundation(database);
        var report = new MigrationRunner(sqlite,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK), CLOCK)
                .migrate(SqliteMigrations.throughVersionThree());
        assertTrue(report.changed());
    }

    @Test
    @DisplayName("[A14-A17] SQLite baselines/latches are idempotent and isolated by scope instance and semantics")
    void persistsRequirementState() {
        SqliteRequirementStateRepository repository = new SqliteRequirementStateRepository(sqlite);
        UUID player = UUID.randomUUID();
        BaselineKey key = new BaselineKey(player, new RequirementId("progress"),
                MeasurementScope.SINCE_STAGE_START, new ScopeId("stage_a"), "a".repeat(64));
        RequirementBaseline first = repository.initializeBaseline(new RequirementBaseline(key,
                MetricValue.count(10), 1, CLOCK.instant()));
        RequirementBaseline repeated = repository.initializeBaseline(new RequirementBaseline(key,
                MetricValue.count(999), 1, CLOCK.instant().plusSeconds(5)));
        assertEquals(MetricValue.count(10), first.value());
        assertEquals(first, repeated);

        BaselineKey nextScope = new BaselineKey(player, new RequirementId("progress"),
                MeasurementScope.SINCE_STAGE_START, new ScopeId("stage_b"), "a".repeat(64));
        repository.initializeBaseline(new RequirementBaseline(nextScope, MetricValue.count(50), 1, CLOCK.instant()));
        assertEquals(MetricValue.count(50), repository.findBaseline(nextScope).orElseThrow().value());

        LatchKey latchKey = new LatchKey(player, new RequirementId("progress"),
                MeasurementScope.SINCE_STAGE_START, new ScopeId("stage_a"), "a".repeat(64));
        RequirementLatch latch = repository.recordLatch(new RequirementLatch(latchKey, CLOCK.instant()));
        assertEquals(latch, repository.recordLatch(new RequirementLatch(latchKey, CLOCK.instant().plusSeconds(10))));
        assertTrue(repository.findLatch(new LatchKey(player, new RequirementId("progress"),
                MeasurementScope.SINCE_STAGE_START, new ScopeId("stage_a"), "b".repeat(64))).isEmpty());
    }

    @Test
    @DisplayName("[A58][A62] Batched manual progress flushes and restores safely after restart")
    void persistsAndRestoresManualProgress() {
        ProviderId providerId = new ProviderId("manual_test");
        MetricId metricId = new MetricId("objectives");
        SqliteManualProgressRepository repository = new SqliteManualProgressRepository(sqlite);
        Executor direct = Runnable::run;
        ManualCounterDefinition definition = new ManualCounterDefinition(metricId, MetricValueType.COUNT,
                true, false, "Objectives", "Trusted objective progress", java.util.Map.of());
        var bootstrap = ManualProgressProvider.bootstrap(providerId, "owner", repository, direct, CLOCK, 10, 100);
        ManualProgressProvider provider = bootstrap.provider();
        var handle = bootstrap.owner().registerMetric(definition).toCompletableFuture().join();
        UUID player = UUID.randomUUID();
        for (int index = 0; index < 100; index++) {
            handle.increment(player, MetricValue.count(1), "server-event", CLOCK.instant());
        }
        assertEquals(1, provider.flushAsync().toCompletableFuture().join(),
                "one player/metric aggregate is persisted in one batch record");
        provider.closeAsync().toCompletableFuture().join();

        var restoredBootstrap = ManualProgressProvider.bootstrap(providerId, "owner", repository, direct,
                CLOCK, 10, 100);
        ManualProgressProvider restored = restoredBootstrap.provider();
        restoredBootstrap.owner().registerMetric(definition).toCompletableFuture().join();
        var query = new net.maddkraft.maddprestige.api.metric.MetricQuery(metricId,
                net.maddkraft.maddprestige.api.metric.MetricReadMode.CURRENT, java.util.Map.of());
        var sample = restored.read(player, List.of(query), 7).toCompletableFuture().join().get(query);
        assertEquals("100", sample.value().orElseThrow().canonical());
        assertEquals(7, sample.providerGeneration());
    }
}
