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
    @DisplayName("Numeric authority migration preserves accepted history and restarts idempotently")
    void upgradesPhaseEightCToNumericAuthority() {
        Path database = temporaryDirectory.resolve("numeric-prestige.sqlite");
        SqliteFoundation foundation = SqliteMigrationFixture.historical(database, 11);
        List<Migration> current = SqliteMigrations.current();
        var runner = new MigrationRunner(foundation,
                new SqliteBackupService(foundation, temporaryDirectory.resolve("numeric-prestige-backups"), current,
                        SqliteMigrationFixture.CLOCK), SqliteMigrationFixture.CLOCK);

        var report = runner.migrate(current);

        assertTrue(report.changed());
        assertEquals("12", SqliteMigrationFixture.scalar(foundation,
                "SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'"));
        assertEquals("'NUMERIC_LEVEL'", SqliteMigrationFixture.scalar(foundation,
                "SELECT dflt_value FROM pragma_table_info('mp_prestige_operation_details') "
                        + "WHERE name='progression_model'"));
        assertEquals("'NUMERIC_LEVEL'", SqliteMigrationFixture.scalar(foundation,
                "SELECT dflt_value FROM pragma_table_info('mp_prestige_history') "
                        + "WHERE name='progression_model'"));
        assertEquals("0:0:0:numeric-p0", SqliteMigrationFixture.scalar(foundation,
                "SELECT current_prestige || ':' || lifetime_prestige || ':' || state_revision "
                        + "|| ':' || prestige_scope_id FROM mp_player_prestige_state"));
        assertEquals("3:5:9", SqliteMigrationFixture.scalar(foundation,
                "SELECT current_prestige || ':' || lifetime_prestige || ':' || state_revision "
                        + "FROM mp_legacy_stage_prestige_state"));
        assertEquals("veteran", SqliteMigrationFixture.scalar(foundation,
                "SELECT stage_id FROM mp_legacy_stage_player_state"));
        assertEquals("0", SqliteMigrationFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_player_stage_state"));
        assertEquals("LEGACY_STAGE", SqliteMigrationFixture.scalar(foundation,
                "SELECT DISTINCT progression_model FROM mp_prestige_operation_details"));
        assertEquals("LEGACY_STAGE", SqliteMigrationFixture.scalar(foundation,
                "SELECT DISTINCT progression_model FROM mp_prestige_history"));
        assertFalse(runner.migrate(current).changed());
    }

    @Test
    @DisplayName("[A63] Fresh and every historical schema prefix upgrade through a verified populated rehearsal")
    void upgradesEverySupportedPrefixAndRestartsIdempotently() throws Exception {
        List<Migration> current = SqliteMigrations.throughVersionEleven();
        for (int prefix = 0; prefix <= current.size(); prefix++) {
            Path caseDirectory = temporaryDirectory.resolve("prefix-" + prefix);
            Files.createDirectories(caseDirectory);
            Path database = caseDirectory.resolve("maddprestige.sqlite");
            SqliteFoundation foundation = prefix == 0
                    ? new SqliteFoundation(database)
                    : SqliteMigrationFixture.historical(database, prefix);
            Path backups = caseDirectory.resolve("backups");
            MigrationRunner runner = new MigrationRunner(foundation,
                    new SqliteBackupService(foundation, backups, current, SqliteMigrationFixture.CLOCK),
                    SqliteMigrationFixture.CLOCK);

            var report = runner.migrate(current);
            assertEquals(current.size(), report.records().size(), "prefix " + prefix);
            assertEquals("11", SqliteMigrationFixture.scalar(foundation,
                    "SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'"));
            assertEquals("ok", SqliteMigrationFixture.scalar(foundation, "PRAGMA integrity_check"));
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
                assertEquals("request-uuid-is-distinct", SqliteMigrationFixture.scalar(foundation,
                        "SELECT idempotency_key FROM mp_operations"));
                assertEquals("NEEDS_RECONCILIATION", SqliteMigrationFixture.scalar(foundation,
                        "SELECT state FROM mp_operations"));
                assertEquals(SqliteMigrationFixture.EXACT_DECIMAL, SqliteMigrationFixture.scalar(foundation,
                        "SELECT balance_text FROM mp_currency_accounts"));
            }
            if (prefix >= 2) {
                assertEquals("7", SqliteMigrationFixture.scalar(foundation,
                        "SELECT state_revision FROM mp_player_stage_state"));
            }
            if (prefix >= 3) {
                assertEquals(SqliteMigrationFixture.EXACT_DECIMAL, SqliteMigrationFixture.scalar(foundation,
                        "SELECT value_text FROM mp_requirement_baselines"));
            }
            if (prefix >= 4) {
                assertEquals("3:5:9", SqliteMigrationFixture.scalar(foundation,
                        "SELECT current_prestige || ':' || lifetime_prestige || ':' || state_revision "
                                + "FROM mp_player_prestige_state"));
                assertEquals("PRESERVE_UNCERTAINTY", SqliteMigrationFixture.scalar(foundation,
                        "SELECT decision FROM mp_recovery_events"));
            }
            if (prefix >= 6) {
                assertEquals(SqliteMigrationFixture.REVISION, SqliteMigrationFixture.scalar(foundation,
                        "SELECT revision_id FROM mp_configuration_revisions_v2 WHERE application_status='APPLIED'"));
            }
            if (prefix >= 7) {
                assertEquals(SqliteMigrationFixture.REMAP.toString(), SqliteMigrationFixture.scalar(foundation,
                        "SELECT operation_id FROM mp_stage_remap_operations"));
            }
            if (prefix >= 8) {
                assertEquals(SqliteMigrationFixture.OPERATION.toString(), SqliteMigrationFixture.scalar(foundation,
                        "SELECT lease_token FROM mp_stage_transition_leases"));
            }
            if (prefix >= 10) {
                assertEquals("NEEDS_RECONCILIATION:legacy", SqliteMigrationFixture.scalar(foundation,
                        "SELECT transition.status || ':' || reservation.stage_id "
                                + "FROM mp_configuration_stage_transitions transition "
                                + "JOIN mp_configuration_stage_reservations reservation "
                                + "ON reservation.config_revision_id=transition.config_revision_id"));
            }

            if (prefix == 2) {
                SqliteFoundation restarted = new SqliteFoundation(database);
                SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(restarted);
                var priorStage = stages.find(SqliteMigrationFixture.PLAYER).orElseThrow();
                stages.update(priorStage.advanceTo(new StageId("elite"),
                        new ConfigRevisionId(SqliteMigrationFixture.REVISION), 8, SqliteMigrationFixture.CLOCK.instant()),
                        priorStage.stateRevision());
                SqlitePlayerPrestigeRepository prestiges = new SqlitePlayerPrestigeRepository(restarted);
                var priorPrestige = prestiges.find(SqliteMigrationFixture.PLAYER).orElseThrow();
                prestiges.update(priorPrestige.advance(1, 1,
                        new ConfigRevisionId(SqliteMigrationFixture.REVISION), new ScopeId("global"),
                        SqliteMigrationFixture.CLOCK.instant()), priorPrestige.stateRevision());
                UUID unknown = UUID.fromString("50000000-0000-0000-0000-000000000005");
                new SqlitePlayerInitializationStore(restarted).initialize(unknown, new StageId("veteran"),
                        new ConfigRevisionId(SqliteMigrationFixture.REVISION), new ScopeId("global"),
                        SqliteMigrationFixture.CLOCK.instant());
                assertEquals("elite", stages.find(SqliteMigrationFixture.PLAYER).orElseThrow().stageId().value());
                assertEquals(1, prestiges.find(SqliteMigrationFixture.PLAYER).orElseThrow().currentPrestige());
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
        List<Migration> current = SqliteMigrations.throughVersionEleven();
        ArrayList<Migration> gap = new ArrayList<>(current);
        gap.remove(4);
        var runner = new MigrationRunner(foundation,
                new SqliteBackupService(foundation, temporaryDirectory.resolve("gap-backups"), current,
                        SqliteMigrationFixture.CLOCK), SqliteMigrationFixture.CLOCK);

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
                        SqliteMigrationFixture.CLOCK.instant(), true, "injected verified fixture"),
                SqliteMigrationFixture.CLOCK);
        runner.migrate(List.of(first));

        assertThrows(PersistenceException.class, () -> runner.migrate(chain));
        assertEquals("0", SqliteMigrationFixture.scalar(foundation, "SELECT COUNT(*) FROM stable_data"));
        assertEquals("1", SqliteMigrationFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=2 AND result='FAILED'"));
        assertThrows(PersistenceException.class, () -> runner.migrate(chain));
        assertEquals("0", SqliteMigrationFixture.scalar(foundation, "SELECT COUNT(*) FROM stable_data"));
        assertEquals("2", SqliteMigrationFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=2 AND result='FAILED'"));
    }

    @Test
    @DisplayName("[A63] Current history with a missing critical table is structurally invalid")
    void rejectsStructurallyIncompleteCurrentDatabase() {
        Path database = temporaryDirectory.resolve("missing-critical-table.sqlite");
        SqliteFoundation foundation = SqliteMigrationFixture.historical(database, 11);
        SqliteMigrationFixture.execute(foundation, "DROP TABLE mp_recovery_events");

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.throughVersionEleven()));

        assertTrue(failure.getMessage().contains("missing required table"));
    }
}
