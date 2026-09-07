package net.maddkraft.maddprestige.persistence.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.core.compatibility.LegacyProviderIdentifiers;
import net.maddkraft.maddprestige.core.manual.ManualProgressRecord;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteManualProgressRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalManualProgressStorageAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);
    private static final ProviderId HISTORICAL = new ProviderId("phase5_events");
    private static final MetricId METRIC = new MetricId("event_total");
    @TempDir
    Path temporaryDirectory;
    private SqliteManualProgressRepository historicalRepository;
    private HistoricalManualProgressStorageAdapter adapter;

    @BeforeEach
    void migrate() {
        Path database = temporaryDirectory.resolve("manual-progress.db");
        SqliteFoundation foundation = new SqliteFoundation(database);
        new MigrationRunner(foundation,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK), CLOCK)
                .migrate(SqliteMigrations.throughVersionThree());
        historicalRepository = new SqliteManualProgressRepository(foundation);
        adapter = new HistoricalManualProgressStorageAdapter(historicalRepository);
    }

    @Test
    @DisplayName("Historical manual-progress rows remain visible under the canonical in-memory provider ID")
    void readsExistingRowsWithoutRewritingTheirStorageKey() {
        UUID player = UUID.randomUUID();
        historicalRepository.writeBatch(List.of(record(HISTORICAL, player, 7, 1)));

        ManualProgressRecord loaded = adapter.load(LegacyProviderIdentifiers.EVENT_PROGRESS, METRIC)
                .get(player);

        assertEquals(LegacyProviderIdentifiers.EVENT_PROGRESS, loaded.providerId());
        assertEquals(MetricValue.count(7), loaded.value());
        assertTrue(historicalRepository.load(LegacyProviderIdentifiers.EVENT_PROGRESS, METRIC).isEmpty());
        assertEquals(MetricValue.count(7), historicalRepository.load(HISTORICAL, METRIC).get(player).value());
    }

    @Test
    @DisplayName("Canonical manual-progress writes retain the historical database storage namespace")
    void writesCanonicalRecordsToTheHistoricalStorageKey() {
        UUID player = UUID.randomUUID();

        adapter.writeBatch(List.of(record(LegacyProviderIdentifiers.EVENT_PROGRESS, player, 9, 2)));

        assertTrue(historicalRepository.load(LegacyProviderIdentifiers.EVENT_PROGRESS, METRIC).isEmpty());
        ManualProgressRecord stored = historicalRepository.load(HISTORICAL, METRIC).get(player);
        assertEquals(HISTORICAL, stored.providerId());
        assertEquals(MetricValue.count(9), stored.value());
    }

    private static ManualProgressRecord record(ProviderId provider, UUID player, long value, long version) {
        return new ManualProgressRecord(provider, METRIC, player, MetricValue.count(value), version,
                "compatibility-test", CLOCK.instant());
    }
}
