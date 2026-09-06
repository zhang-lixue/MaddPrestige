package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePlayerInitializationStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-17T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[OR8B-02] Explicit post-PRE initialization atomically creates exact initial lifecycle state")
    void initializesBothRowsAtomicallyAndIdempotently() {
        Path database = temporaryDirectory.resolve("initialization.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database, temporaryDirectory.resolve("backups"), CLOCK),
                CLOCK).migrate(SqliteMigrations.phaseSix());
        ConfigRevisionId revision = new ConfigRevisionId("initialization_revision");
        new SqliteConfigRevisionRepository(sqlite).insert(revision, RevisionHasher.hashText("initialization"));
        UUID playerId = UUID.randomUUID();
        StageId stageId = new StageId("novice");
        ScopeId scopeId = new ScopeId("prestige_initial_scope");
        SqlitePlayerInitializationStore initialization = new SqlitePlayerInitializationStore(sqlite);
        var baselineKey = new BaselineKey(playerId, new RequirementId("play_time"),
                MeasurementScope.SINCE_PRESTIGE_START, scopeId, "0".repeat(64));
        var baseline = new RequirementBaseline(baselineKey, MetricValue.duration(Duration.ofSeconds(7)), 1, NOW);

        initialization.initialize(playerId, stageId, revision, scopeId, NOW, List.of(baseline));
        initialization.initialize(playerId, stageId, revision, scopeId, NOW, List.of(baseline));

        var stage = new SqlitePlayerStageRepository(sqlite).find(playerId).orElseThrow();
        var prestige = new SqlitePlayerPrestigeRepository(sqlite).find(playerId).orElseThrow();
        assertEquals(stageId, stage.stageId());
        assertEquals(0, stage.stateRevision());
        assertEquals(revision, stage.configRevision());
        assertEquals(0, prestige.currentPrestige());
        assertEquals(0, prestige.lifetimePrestige());
        assertEquals(0, prestige.stateRevision());
        assertEquals(List.of(playerId), new SqlitePlayerPrestigeRepository(sqlite).knownPlayerIds());
        assertEquals(scopeId, prestige.prestigeScope());
        assertEquals(baseline, new SqliteRequirementStateRepository(sqlite).findBaseline(baselineKey).orElseThrow());
        assertTrue(count(sqlite, "mp_operations") == 0);
    }

    @Test
    @DisplayName("[Phase 9B] Fresh numeric Prestige starts at zero without a stage and survives restart")
    void initializesNumericPrestigeWithoutImportingStageState() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path legacyDatabase = temporaryDirectory.resolve("maddprestige.db");
        SqliteFoundation legacy = new SqliteFoundation(legacyDatabase);
        execute(legacy, "CREATE TABLE v1_player_authority (player_uuid TEXT PRIMARY KEY, progression_rank TEXT, "
                + "prestige INTEGER, tea_leaves INTEGER, perk_level INTEGER, pending_operation TEXT, "
                + "season_history TEXT)");
        execute(legacy, "INSERT INTO v1_player_authority VALUES ('" + playerId
                + "', 'UNBOUND', 57, 9001, 12, 'EXECUTING', 'champion')");
        var legacyHash = SqliteBackupService.sha256(legacyDatabase);

        Path database = temporaryDirectory.resolve("maddprestige-v2.sqlite");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite, new FileBackupService(database,
                temporaryDirectory.resolve("numeric-backups"), CLOCK), CLOCK)
                .migrate(SqliteMigrations.phaseNineB());
        ConfigRevisionId revision = new ConfigRevisionId("phase9b_numeric_revision");
        new SqliteConfigRevisionRepository(sqlite).insert(revision, RevisionHasher.hashText("phase9b numeric"));
        ScopeId scopeId = new ScopeId("prestige_numeric_scope");

        new SqlitePlayerInitializationStore(sqlite).initializePrestige(
                playerId, revision, scopeId, NOW, List.of());

        assertFalse(new SqlitePlayerStageRepository(sqlite).find(playerId).isPresent());
        var initial = new SqlitePlayerPrestigeRepository(sqlite).find(playerId).orElseThrow();
        assertEquals(0, initial.currentPrestige());
        assertEquals(0, initial.lifetimePrestige());
        assertEquals(0, initial.stateRevision());
        assertEquals(legacyHash, SqliteBackupService.sha256(legacyDatabase),
                "separate V1 player data remains byte-for-byte untouched");
        assertEquals(1, count(legacy, "v1_player_authority"));

        SqliteFoundation restarted = new SqliteFoundation(database);
        var recovered = new SqlitePlayerPrestigeRepository(restarted).find(playerId).orElseThrow();
        assertEquals(initial, recovered);
        assertFalse(new SqlitePlayerStageRepository(restarted).find(playerId).isPresent());
    }

    private static long count(SqliteFoundation sqlite, String table) {
        try (var connection = sqlite.open(); var statement = connection.createStatement();
                var row = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return row.getLong(1);
        } catch (java.sql.SQLException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void execute(SqliteFoundation sqlite, String sql) {
        try (var connection = sqlite.open(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (java.sql.SQLException failure) {
            throw new AssertionError(failure);
        }
    }
}
