package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteMigrationQualificationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[Phase 9B] Numeric authority migration preserves accepted history and restarts idempotently")
    void upgradesPhaseEightCToNumericAuthority() {
        Path database = temporaryDirectory.resolve("phase9b.sqlite");
        SqliteFoundation foundation = SqlitePhase8cFixture.historical(database, 11);
        List<Migration> current = SqliteMigrations.phaseNineB();
        var runner = new MigrationRunner(foundation,
                new SqliteBackupService(foundation, temporaryDirectory.resolve("phase9b-backups"), current,
                        SqlitePhase8cFixture.CLOCK), SqlitePhase8cFixture.CLOCK);

        var report = runner.migrate(current);

        assertTrue(report.changed());
        assertEquals("12", SqlitePhase8cFixture.scalar(foundation,
                "SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'"));
        assertEquals("'NUMERIC_LEVEL'", SqlitePhase8cFixture.scalar(foundation,
                "SELECT dflt_value FROM pragma_table_info('mp_prestige_operation_details') "
                        + "WHERE name='progression_model'"));
        assertEquals("'NUMERIC_LEVEL'", SqlitePhase8cFixture.scalar(foundation,
                "SELECT dflt_value FROM pragma_table_info('mp_prestige_history') "
                        + "WHERE name='progression_model'"));
        assertEquals("0:0:0:numeric-p0", SqlitePhase8cFixture.scalar(foundation,
                "SELECT current_prestige || ':' || lifetime_prestige || ':' || state_revision "
                        + "|| ':' || prestige_scope_id FROM mp_player_prestige_state"));
        assertEquals("3:5:9", SqlitePhase8cFixture.scalar(foundation,
                "SELECT current_prestige || ':' || lifetime_prestige || ':' || state_revision "
                        + "FROM mp_legacy_stage_prestige_state"));
        assertEquals("veteran", SqlitePhase8cFixture.scalar(foundation,
                "SELECT stage_id FROM mp_legacy_stage_player_state"));
        assertEquals("0", SqlitePhase8cFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_player_stage_state"));
        assertEquals("LEGACY_STAGE", SqlitePhase8cFixture.scalar(foundation,
                "SELECT DISTINCT progression_model FROM mp_prestige_operation_details"));
        assertEquals("LEGACY_STAGE", SqlitePhase8cFixture.scalar(foundation,
                "SELECT DISTINCT progression_model FROM mp_prestige_history"));
        assertFalse(runner.migrate(current).changed());
    }

    @Test
    @DisplayName("[A63] Fresh and every historical schema prefix upgrade through a verified populated rehearsal")
    void upgradesEverySupportedPrefixAndRestartsIdempotently() throws Exception {
        List<Migration> current = SqliteMigrations.phaseEightC();
        for (int prefix = 0; prefix <= current.size(); prefix++) {
            Path caseDirectory = temporaryDirectory.resolve("prefix-" + prefix);
            Files.createDirectories(caseDirectory);
            Path database = caseDirectory.resolve("maddprestige.sqlite");
            SqliteFoundation foundation = prefix == 0
                    ? new SqliteFoundation(database)
                    : SqlitePhase8cFixture.historical(database, prefix);
            Path backups = caseDirectory.resolve("backups");
            MigrationRunner runner = new MigrationRunner(foundation,
                    new SqliteBackupService(foundation, backups, current, SqlitePhase8cFixture.CLOCK),
                    SqlitePhase8cFixture.CLOCK);

            var report = runner.migrate(current);
            assertEquals(current.size(), report.records().size(), "prefix " + prefix);
            assertEquals("11", SqlitePhase8cFixture.scalar(foundation,
                    "SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'"));
            assertEquals("ok", SqlitePhase8cFixture.scalar(foundation, "PRAGMA integrity_check"));
            if (prefix < current.size()) {
                assertTrue(report.changed(), "prefix " + prefix);
                try (var artifacts = Files.list(backups)) {
                    assertEquals(2, artifacts.count(), "prefix " + prefix + " accepted backup+manifest");
                }
            } else {
                assertFalse(report.changed());
                assertFalse(Files.exists(backups), "current schema does not need a migration backup");
            }

            if (prefix >= 1) {
                assertEquals("request-uuid-is-distinct", SqlitePhase8cFixture.scalar(foundation,
                        "SELECT idempotency_key FROM mp_operations"));
                assertEquals("NEEDS_RECONCILIATION", SqlitePhase8cFixture.scalar(foundation,
                        "SELECT state FROM mp_operations"));
                assertEquals(SqlitePhase8cFixture.EXACT_DECIMAL, SqlitePhase8cFixture.scalar(foundation,
                        "SELECT balance_text FROM mp_currency_accounts"));
            }
            if (prefix >= 2) {
                assertEquals("7", SqlitePhase8cFixture.scalar(foundation,
                        "SELECT state_revision FROM mp_player_stage_state"));
            }
            if (prefix >= 3) {
                assertEquals(SqlitePhase8cFixture.EXACT_DECIMAL, SqlitePhase8cFixture.scalar(foundation,
                        "SELECT value_text FROM mp_requirement_baselines"));
            }
            if (prefix >= 4) {
                assertEquals("3:5:9", SqlitePhase8cFixture.scalar(foundation,
                        "SELECT current_prestige || ':' || lifetime_prestige || ':' || state_revision "
                                + "FROM mp_player_prestige_state"));
                assertEquals("PRESERVE_UNCERTAINTY", SqlitePhase8cFixture.scalar(foundation,
                        "SELECT decision FROM mp_recovery_events"));
            }
            if (prefix >= 6) {
                assertEquals(SqlitePhase8cFixture.REVISION, SqlitePhase8cFixture.scalar(foundation,
                        "SELECT revision_id FROM mp_configuration_revisions_v2 WHERE application_status='APPLIED'"));
            }
            if (prefix >= 7) {
                assertEquals(SqlitePhase8cFixture.REMAP.toString(), SqlitePhase8cFixture.scalar(foundation,
                        "SELECT operation_id FROM mp_stage_remap_operations"));
            }
            if (prefix >= 8) {
                assertEquals(SqlitePhase8cFixture.OPERATION.toString(), SqlitePhase8cFixture.scalar(foundation,
                        "SELECT lease_token FROM mp_stage_transition_leases"));
            }
            if (prefix >= 10) {
                assertEquals("NEEDS_RECONCILIATION:legacy", SqlitePhase8cFixture.scalar(foundation,
                        "SELECT transition.status || ':' || reservation.stage_id "
                                + "FROM mp_configuration_stage_transitions transition "
                                + "JOIN mp_configuration_stage_reservations reservation "
                                + "ON reservation.config_revision_id=transition.config_revision_id"));
            }

            if (prefix == 2) {
                SqliteFoundation restarted = new SqliteFoundation(database);
                SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(restarted);
                var priorStage = stages.find(SqlitePhase8cFixture.PLAYER).orElseThrow();
                stages.update(priorStage.advanceTo(new StageId("elite"),
                        new ConfigRevisionId(SqlitePhase8cFixture.REVISION), 8, SqlitePhase8cFixture.CLOCK.instant()),
                        priorStage.stateRevision());
                SqlitePlayerPrestigeRepository prestiges = new SqlitePlayerPrestigeRepository(restarted);
                var priorPrestige = prestiges.find(SqlitePhase8cFixture.PLAYER).orElseThrow();
                prestiges.update(priorPrestige.advance(1, 1,
                        new ConfigRevisionId(SqlitePhase8cFixture.REVISION), new ScopeId("global"),
                        SqlitePhase8cFixture.CLOCK.instant()), priorPrestige.stateRevision());
                UUID unknown = UUID.fromString("50000000-0000-0000-0000-000000000005");
                new SqlitePlayerInitializationStore(restarted).initialize(unknown, new StageId("veteran"),
                        new ConfigRevisionId(SqlitePhase8cFixture.REVISION), new ScopeId("global"),
                        SqlitePhase8cFixture.CLOCK.instant());
                assertEquals("elite", stages.find(SqlitePhase8cFixture.PLAYER).orElseThrow().stageId().value());
                assertEquals(1, prestiges.find(SqlitePhase8cFixture.PLAYER).orElseThrow().currentPrestige());
                assertTrue(stages.find(unknown).isPresent());
                assertTrue(prestiges.find(unknown).isPresent());
            }

            var restart = runner.migrate(current);
            assertFalse(restart.changed(), "completed prefix " + prefix + " must restart idempotently");
            assertEquals(report.records(), restart.records());
        }
    }

    @Test
    @DisplayName("[A63] Migration chain gaps and missing versions are rejected before persistence access")
    void rejectsMissingMigrationInRequestedChain() {
        Path database = temporaryDirectory.resolve("chain-gap.sqlite");
        SqliteFoundation foundation = new SqliteFoundation(database);
        List<Migration> current = SqliteMigrations.phaseEightC();
        ArrayList<Migration> gap = new ArrayList<>(current);
        gap.remove(4);
        var runner = new MigrationRunner(foundation,
                new SqliteBackupService(foundation, temporaryDirectory.resolve("gap-backups"), current,
                        SqlitePhase8cFixture.CLOCK), SqlitePhase8cFixture.CLOCK);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> runner.migrate(gap));

        assertTrue(failure.getMessage().contains("contiguous prefix"));
        assertFalse(Files.exists(database));
    }

    @Test
    @DisplayName("[A63] Failed and interrupted migration retains prior schema and deterministic audit evidence")
    void failedMigrationRollsBackAndRestartsDeterministically() {
        Path database = temporaryDirectory.resolve("failed.sqlite");
        SqliteFoundation foundation = new SqliteFoundation(database);
        Migration first = Migration.of(1, "stable foundation", List.of("CREATE TABLE stable_data (value TEXT)"));
        Migration failing = Migration.of(2, "atomic failure", List.of(
                "INSERT INTO stable_data(value) VALUES ('must-roll-back')",
                "CREATE TABLE stable_data (duplicate TEXT)"));
        List<Migration> chain = List.of(first, failing);
        var runner = new MigrationRunner(foundation,
                ignored -> new net.maddkraft.maddprestige.persistence.VerifiedBackup(
                        "qualified", java.util.Optional.of(database),
                        java.util.Optional.of(net.maddkraft.maddprestige.core.config.RevisionHasher.hashText("fixture")),
                        SqlitePhase8cFixture.CLOCK.instant(), true, "injected verified fixture"),
                SqlitePhase8cFixture.CLOCK);
        runner.migrate(List.of(first));

        assertThrows(PersistenceException.class, () -> runner.migrate(chain));
        assertEquals("0", SqlitePhase8cFixture.scalar(foundation, "SELECT COUNT(*) FROM stable_data"));
        assertEquals("1", SqlitePhase8cFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=2 AND result='FAILED'"));
        assertThrows(PersistenceException.class, () -> runner.migrate(chain));
        assertEquals("0", SqlitePhase8cFixture.scalar(foundation, "SELECT COUNT(*) FROM stable_data"));
        assertEquals("2", SqlitePhase8cFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=2 AND result='FAILED'"));
    }

    @Test
    @DisplayName("[A63] Current history with a missing critical table is structurally invalid")
    void rejectsStructurallyIncompleteCurrentDatabase() {
        Path database = temporaryDirectory.resolve("missing-critical-table.sqlite");
        SqliteFoundation foundation = SqlitePhase8cFixture.historical(database, 11);
        SqlitePhase8cFixture.execute(foundation, "DROP TABLE mp_recovery_events");

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));

        assertTrue(failure.getMessage().contains("missing required table"));
    }
}
