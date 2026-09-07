package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
import net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A35][A63] Verified-backup migration records deterministic applied history")
    void appliesFreshFoundation() throws Exception {
        Path database = temporaryDirectory.resolve("fresh.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        var report = runner(sqlite, database).migrate(SqliteMigrations.throughVersionOne());

        assertTrue(report.changed());
        assertEquals(1, report.records().size());
        assertEquals("APPLIED", report.records().getFirst().result().name());
        assertTrue(report.records().getFirst().appliedAt().toString().endsWith("Z"));
        assertEquals("ok", scalar(sqlite, "PRAGMA integrity_check"));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result = 'APPLIED'"));
        assertTrue(scalar(sqlite, "SELECT sql FROM sqlite_master "
                + "WHERE type='index' AND name='mp_schema_migrations_applied_version_uq'")
                .contains("WHERE result = 'APPLIED'"));
        assertTrue(Files.list(temporaryDirectory.resolve("fresh-backups")).findAny().isPresent());
    }

    @Test
    @DisplayName("[A69] Migration 9 preserves only live journal ownership and requires source adoption")
    void upgradesLegacyTransitionLeasesWithoutRetainingOrphans() {
        Path database = temporaryDirectory.resolve("legacy-stage-leases.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        List<Migration> administration = SqliteMigrations.throughVersionTen();
        runner(sqlite, database).migrate(administration.subList(0, 8));
        ConfigRevisionId revision = new ConfigRevisionId("legacy_lease_revision");
        new SqliteConfigRevisionRepository(sqlite).insert(revision, RevisionHasher.hashText("legacy lease"));
        UUID live = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        insertLegacyOperation(sqlite, live, "PREPARED", revision);
        insertLegacyOperation(sqlite, terminal, "FAILED", revision);
        insertLegacyLease(sqlite, live, revision);
        insertLegacyLease(sqlite, terminal, revision);
        insertLegacyLease(sqlite, orphan, revision);

        runner(sqlite, database).migrate(administration);

        assertEquals("1", scalar(sqlite, "SELECT COUNT(*) FROM mp_stage_transition_leases"));
        var fence = new SqliteStageReferenceMigrationStore(sqlite);
        var legacy = fence.leases(10).getFirst();
        assertTrue(legacy.sourceStage().isEmpty());
        assertFalse(legacy.participationComplete());
        var adopted = fence.acquire(new OperationId(live), new StageId("source"), new StageId("target"),
                revision, java.time.Instant.parse("2026-08-16T00:00:01Z"));
        assertEquals(live, adopted.leaseToken());
        assertEquals(new StageId("source"), fence.leases(10).getFirst().sourceStage().orElseThrow());
        execute(sqlite, "UPDATE mp_operations SET state='FAILED' WHERE operation_id='" + live + "'");
        fence.release(adopted, java.time.Instant.parse("2026-08-16T00:00:02Z"));
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM mp_stage_transition_leases"));
    }

    @Test
    @DisplayName("[A69] Migration 10 converts legacy remap authority to incomplete fail-closed recovery state")
    void upgradesLegacyPendingRemapAsIncompleteConfigurationAuthority() {
        Path database = temporaryDirectory.resolve("legacy-configuration-transition.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        List<Migration> administration = SqliteMigrations.throughVersionTen();
        runner(sqlite, database).migrate(administration.subList(0, 9));
        ConfigRevisionId revision = new ConfigRevisionId("legacy_configuration_transition");
        var hash = RevisionHasher.hashText("legacy candidate");
        new SqliteConfigRevisionRepository(sqlite).insert(revision, hash);
        execute(sqlite, "INSERT INTO mp_configuration_revisions_v2 (revision_id, canonical_content_hash, "
                + "actor_type, actor_name, source_surface, reason, validation_summary, diff_summary, "
                + "application_status, created_at) VALUES ('" + revision.value() + "','" + hash.value()
                + "','console','Owner','legacy-test','pending migration','valid','legacy','ATTEMPTED',"
                + "'2026-08-16T00:00:00Z')");
        UUID operation = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        execute(sqlite, "INSERT INTO mp_stage_remap_operations (operation_id, config_revision_id, plan_revision, "
                + "plan_hash, actor_type, actor_name, reason, status, migrated_players, created_at, updated_at, "
                + "detail) VALUES ('" + operation + "','" + revision.value() + "','legacy-plan','" + hash.value()
                + "','console','Owner','legacy remap','MIGRATED_PENDING_CONFIG',1,'2026-08-16T00:00:00Z',"
                + "'2026-08-16T00:00:00Z','pending')");
        execute(sqlite, "INSERT INTO mp_stage_remap_entries (operation_id, player_uuid, source_stage_id, "
                + "target_stage_id, expected_state_revision, resulting_state_revision, source_config_revision_id) "
                + "VALUES ('" + operation + "','" + player + "','b','c',0,1,'" + revision.value() + "')");

        runner(sqlite, database).migrate(administration);

        var transition = new SqliteStageReferenceMigrationStore(sqlite).transitions(10).getFirst();
        assertFalse(transition.scopeComplete());
        assertEquals(ConfigurationStageReservationKind.UNKNOWN_LEGACY,
                transition.reservedStages().get(new StageId("b")));
        SqlitePlayerStageRepository players = new SqlitePlayerStageRepository(sqlite);
        PlayerStageState unrelated = new PlayerStageState(UUID.randomUUID(), new StageId("unrelated"), 0,
                revision, Instant.parse("2026-08-16T00:00:01Z"), Instant.parse("2026-08-16T00:00:01Z"),
                Instant.parse("2026-08-16T00:00:01Z"), Optional.empty(), Optional.empty(), Optional.empty());
        assertThrows(StageTransitionBlockedException.class, () -> players.insert(unrelated),
                "unknown legacy scope must block globally rather than guess which zero-reference stages were unsafe");
    }

    @Test
    @DisplayName("[A63] Application schema without history is ambiguous and is never replayed")
    void refusesPartialSchema() throws Exception {
        Path database = temporaryDirectory.resolve("partial.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        execute(sqlite, "CREATE TABLE mp_operations (broken TEXT)");

        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database).migrate(SqliteMigrations.throughVersionOne()));

        assertFalse(tableExists(sqlite, "mp_schema_migrations"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='mp_config_revisions'"));
        assertTrue(tableExists(sqlite, "mp_operations"));
    }

    @Test
    @DisplayName("[A35-correction] Backup failure precedes and prevents all history/schema DDL")
    void backupFailureLeavesPreexistingSchemaIntact() {
        Path database = temporaryDirectory.resolve("blocked.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        execute(sqlite, "CREATE TABLE preexisting_data (value TEXT NOT NULL)");
        execute(sqlite, "INSERT INTO preexisting_data(value) VALUES ('preserved')");
        String schemaBefore = schemaSnapshot(sqlite);

        assertThrows(PersistenceException.class, () -> new MigrationRunner(sqlite,
                reason -> VerifiedBackup.failure("blocked", "injected backup failure"), Clock.systemUTC())
                .migrate(SqliteMigrations.throughVersionOne()));

        assertEquals(schemaBefore, schemaSnapshot(sqlite));
        assertEquals("preserved", scalar(sqlite, "SELECT value FROM preexisting_data"));
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type='table' AND name='mp_schema_migrations'"));
        assertEquals("0", scalar(sqlite, "SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type='index' AND name='mp_schema_migrations_applied_version_uq'"));
    }

    @Test
    @DisplayName("[A35-correction] Existing valid history is inspected without mutation before backup")
    void backupFailureAfterAppliedPrefixLeavesHistoryUntouched() {
        Path database = temporaryDirectory.resolve("existing-history.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        List<Migration> chain = List.of(simpleMigration(1), simpleMigration(2));
        runner(sqlite, database).migrate(List.of(chain.getFirst()));
        String schemaBefore = schemaSnapshot(sqlite);

        assertThrows(PersistenceException.class, () -> new MigrationRunner(sqlite,
                reason -> VerifiedBackup.failure("blocked", "injected backup failure"), Clock.systemUTC())
                .migrate(chain));

        assertEquals(schemaBefore, schemaSnapshot(sqlite));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result = 'APPLIED'"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result = 'FAILED'"));
        assertFalse(tableExists(sqlite, "mp_test_migration_2"));
    }

    @Test
    @DisplayName("[OR8C-07] A complete attempt-ledger change invalidates the sealed migration backup")
    void rejectsCompleteAttemptLedgerChangeDuringBackup() {
        Path database = temporaryDirectory.resolve("changed-attempt-ledger.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        Clock boundary = Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), java.time.ZoneOffset.UTC);
        List<Migration> chain = List.of(simpleMigration(1), simpleMigration(2));
        runner(sqlite, database, boundary).migrate(List.of(chain.getFirst()));
        FileBackupService delegate = new FileBackupService(
                database, temporaryDirectory.resolve("changed-attempt-ledger-backups"), boundary);
        MigrationRunner changingLedger = new MigrationRunner(sqlite, reason -> {
            VerifiedBackup sealed = delegate.createVerifiedBackup(reason);
            insertFailedAttempt(sqlite, 2, boundary.instant(), "concurrent next-version attempt");
            return sealed;
        }, boundary);

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> changingLedger.migrate(chain));

        assertTrue(failure.getMessage().contains("Complete migration attempt ledger changed"));
        assertFalse(tableExists(sqlite, "mp_test_migration_2"));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=2 AND result='FAILED'"));
    }

    @Test
    @DisplayName("[A35-correction] Failed attempts repeat and the same migration can later succeed")
    void recordsRepeatedFailuresThenSuccessfulRetry() {
        Path database = temporaryDirectory.resolve("retry.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        execute(sqlite, "CREATE TABLE retry_blocker (value TEXT)");
        Migration retryable = Migration.of(1, "repeatable failure", List.of(
                "CREATE TABLE retry_created (value TEXT)",
                "CREATE TABLE retry_blocker (value TEXT)"));
        MigrationRunner runner = runner(sqlite, database);

        assertThrows(PersistenceException.class, () -> runner.migrate(List.of(retryable)));
        assertThrows(PersistenceException.class, () -> runner.migrate(List.of(retryable)));
        assertEquals("2", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='FAILED'"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='APPLIED'"));
        assertFalse(tableExists(sqlite, "retry_created"));

        execute(sqlite, "DROP TABLE retry_blocker");
        assertTrue(runner.migrate(List.of(retryable)).changed());
        assertEquals("2", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='FAILED'"));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='APPLIED'"));
        assertTrue(tableExists(sqlite, "retry_created"));
    }

    @Test
    @DisplayName("[A35-correction] Applied version 2 without version 1 fails before older work executes")
    void rejectsNewerAppliedVersionWhenOlderVersionIsMissing() {
        Path database = temporaryDirectory.resolve("missing-first.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        List<Migration> chain = List.of(simpleMigration(1), simpleMigration(2));
        runner(sqlite, database).migrate(chain);
        execute(sqlite, "DELETE FROM mp_schema_migrations WHERE version=1 AND result='APPLIED'");
        execute(sqlite, "DROP TABLE mp_test_migration_1");

        assertThrows(PersistenceException.class, () -> runner(sqlite, database).migrate(chain));
        assertFalse(tableExists(sqlite, "mp_test_migration_1"));
    }

    @Test
    @DisplayName("[A35-correction] Applied versions 1 and 3 without version 2 fail as a non-prefix")
    void rejectsGapInAppliedHistory() {
        Path database = temporaryDirectory.resolve("history-gap.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        List<Migration> chain = List.of(simpleMigration(1), simpleMigration(2), simpleMigration(3));
        runner(sqlite, database).migrate(chain);
        execute(sqlite, "DELETE FROM mp_schema_migrations WHERE version=2 AND result='APPLIED'");
        execute(sqlite, "DROP TABLE mp_test_migration_2");

        assertThrows(PersistenceException.class, () -> runner(sqlite, database).migrate(chain));
        assertFalse(tableExists(sqlite, "mp_test_migration_2"));
    }

    @Test
    @DisplayName("[A35-correction] Unknown applied versions fail closed")
    void rejectsUnknownAppliedVersion() {
        Path database = temporaryDirectory.resolve("unknown-version.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        Migration migration = simpleMigration(1);
        runner(sqlite, database).migrate(List.of(migration));
        execute(sqlite, "UPDATE mp_schema_migrations SET version=99 WHERE result='APPLIED'");

        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database).migrate(List.of(migration)));
    }

    @Test
    @DisplayName("[A35-correction] Applied checksum mismatch fails closed")
    void rejectsAppliedChecksumMismatch() {
        Path database = temporaryDirectory.resolve("checksum-mismatch.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        Migration migration = simpleMigration(1);
        runner(sqlite, database).migrate(List.of(migration));
        execute(sqlite, "UPDATE mp_schema_migrations SET checksum='" + "0".repeat(64)
                + "' WHERE result='APPLIED'");

        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database).migrate(List.of(migration)));
    }

    @Test
    @DisplayName("[A63] Future FAILED history is still unknown schema evidence and fails closed")
    void rejectsUnknownFutureFailedHistory() {
        Path database = temporaryDirectory.resolve("future-failed-version.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        Migration migration = simpleMigration(1);
        runner(sqlite, database).migrate(List.of(migration));
        execute(sqlite, "INSERT INTO mp_schema_migrations (attempt_id, version, checksum, description, applied_at, "
                + "result, detail) VALUES ('" + UUID.randomUUID() + "',99,'" + "0".repeat(64)
                + "','future','2026-08-17T20:00:00Z','FAILED','unknown future attempt')");

        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database).migrate(List.of(migration)));
    }

    @Test
    @DisplayName("[OR8C-03] Known FAILED attempts beyond the immediate next migration fail closed")
    void rejectsFailedAttemptBeyondNextPendingMigration() {
        Path database = temporaryDirectory.resolve("failed-beyond-next.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        List<Migration> chain = List.of(simpleMigration(1), simpleMigration(2), simpleMigration(3));
        runner(sqlite, database).migrate(List.of(chain.getFirst()));
        insertFailedAttempt(sqlite, 3, "future known attempt");

        assertThrows(PersistenceException.class, () -> runner(sqlite, database).migrate(chain));
        assertFalse(tableExists(sqlite, "mp_test_migration_2"));
    }

    @Test
    @DisplayName("[OR8C-03][OR8C-07] Causal prior and immediate-next failures remain repeatable")
    void acceptsFailedAttemptsInAppliedPrefixAndAtNextPendingMigration() {
        Path database = temporaryDirectory.resolve("valid-failed-order.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        Clock firstFailure = Clock.fixed(Instant.parse("2026-08-17T19:59:00Z"), java.time.ZoneOffset.UTC);
        Clock firstApplied = Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), java.time.ZoneOffset.UTC);
        Clock completion = Clock.fixed(Instant.parse("2026-08-17T20:01:00Z"), java.time.ZoneOffset.UTC);
        Migration first = Migration.of(1, "causal first retry", List.of(
                "CREATE TABLE mp_test_migration_1 (value TEXT)",
                "CREATE TABLE retry_blocker_1 (value TEXT)"));
        Migration second = Migration.of(2, "causal next retry", List.of(
                "CREATE TABLE mp_test_migration_2 (value TEXT)",
                "CREATE TABLE retry_blocker_2 (value TEXT)"));
        List<Migration> chain = List.of(first, second, simpleMigration(3));

        execute(sqlite, "CREATE TABLE retry_blocker_1 (value TEXT)");
        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database, firstFailure).migrate(List.of(first)));
        execute(sqlite, "DROP TABLE retry_blocker_1");
        assertTrue(runner(sqlite, database, firstApplied).migrate(List.of(first)).changed());

        execute(sqlite, "CREATE TABLE retry_blocker_2 (value TEXT)");
        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database, firstApplied).migrate(List.of(first, second)));
        execute(sqlite, "DROP TABLE retry_blocker_2");

        assertTrue(runner(sqlite, database, completion).migrate(chain).changed());
        assertTrue(tableExists(sqlite, "mp_test_migration_3"));
        assertEquals("2", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result='FAILED'"));
    }

    @Test
    @DisplayName("[OR8C-07] FAILED v1 after its APPLIED completion is contradictory")
    void rejectsFailedAttemptAfterSameVersionApplied() {
        Path database = temporaryDirectory.resolve("failed-after-applied.db");
        SqliteFoundation sqlite = SqliteMigrationFixture.historical(database, 1);
        insertFailedAttempt(sqlite, 1, Instant.parse("2026-08-17T20:00:01Z"), "impossible late v1");

        assertThrows(PersistenceException.class,
                () -> new MigrationRunner(sqlite, ignored -> VerifiedBackup.failure("unused", "unused"),
                        SqliteMigrationFixture.CLOCK).migrate(SqliteMigrations.throughVersionEleven()));
    }

    @Test
    @DisplayName("[OR8C-07] FAILED v2 before APPLIED v1 completion is contradictory")
    void rejectsNextFailureBeforePriorVersionApplied() {
        Path database = temporaryDirectory.resolve("failed-before-prior-applied.db");
        SqliteFoundation sqlite = SqliteMigrationFixture.historical(database, 1);
        insertFailedAttempt(sqlite, 2, Instant.parse("2026-08-17T19:59:59Z"), "impossible early v2");

        assertThrows(PersistenceException.class,
                () -> new MigrationRunner(sqlite, ignored -> VerifiedBackup.failure("unused", "unused"),
                        SqliteMigrationFixture.CLOCK).migrate(SqliteMigrations.throughVersionEleven()));
    }

    @Test
    @DisplayName("[OR8C-07] Equal FAILED/APPLIED timestamp boundaries remain valid")
    void acceptsEqualFailedAttemptBoundaries() {
        Path database = temporaryDirectory.resolve("equal-failed-boundaries.sqlite");
        SqliteFoundation sqlite = SqliteMigrationFixture.historical(database, 1);
        insertFailedAttempt(sqlite, 1, SqliteMigrationFixture.CLOCK.instant(), "equal prior v1 retry");
        insertFailedAttempt(sqlite, 2, SqliteMigrationFixture.CLOCK.instant(), "equal next v2 retry");

        SqliteDatabaseValidator.validate(database, SqliteMigrations.throughVersionEleven());
    }

    @Test
    @DisplayName("[OR8C-03] Independent validation enforces FAILED-attempt prefix ordering")
    void independentValidatorEnforcesFailedAttemptOrdering() {
        Path validDatabase = temporaryDirectory.resolve("valid-failed-prefix.sqlite");
        SqliteFoundation valid = SqliteMigrationFixture.historical(validDatabase, 9);
        insertFailedAttempt(valid, 9, "prior retry");
        insertFailedAttempt(valid, 10, "next retry");
        SqliteDatabaseValidator.validate(validDatabase, SqliteMigrations.throughVersionEleven());

        Path invalidDatabase = temporaryDirectory.resolve("invalid-failed-prefix.sqlite");
        SqliteFoundation invalid = SqliteMigrationFixture.historical(invalidDatabase, 9);
        insertFailedAttempt(invalid, 11, "skipped known migration");
        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(invalidDatabase, SqliteMigrations.throughVersionEleven()));

        Path causallyInvalidDatabase = temporaryDirectory.resolve("invalid-failed-causality.sqlite");
        SqliteFoundation causallyInvalid = SqliteMigrationFixture.historical(causallyInvalidDatabase, 9);
        insertFailedAttempt(causallyInvalid, 10, Instant.parse("2026-08-17T19:59:59Z"),
                "next attempt predates applied prefix");
        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(causallyInvalidDatabase, SqliteMigrations.throughVersionEleven()));
    }

    @Test
    @DisplayName("[A63] Duplicate APPLIED history fails closed even if a legacy index was removed")
    void rejectsDuplicateAppliedHistory() {
        Path database = temporaryDirectory.resolve("duplicate-history.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        Migration migration = simpleMigration(1);
        runner(sqlite, database).migrate(List.of(migration));
        execute(sqlite, "DROP INDEX mp_schema_migrations_applied_version_uq");
        execute(sqlite, "INSERT INTO mp_schema_migrations (attempt_id, version, checksum, description, applied_at, "
                + "result, detail) VALUES ('" + UUID.randomUUID() + "',1,'" + migration.checksum().value()
                + "','" + migration.description() + "','2026-08-17T20:00:01Z','APPLIED','duplicate')");

        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database).migrate(List.of(migration)));
    }

    @Test
    @DisplayName("[A63] Malformed attempt identity and altered description fail closed")
    void rejectsMalformedAndInconsistentHistoryMetadata() {
        Path malformedDatabase = temporaryDirectory.resolve("malformed-history.db");
        SqliteFoundation malformed = new SqliteFoundation(malformedDatabase);
        Migration migration = simpleMigration(1);
        runner(malformed, malformedDatabase).migrate(List.of(migration));
        execute(malformed, "UPDATE mp_schema_migrations SET attempt_id='not-a-uuid' WHERE result='APPLIED'");
        assertThrows(PersistenceException.class,
                () -> runner(malformed, malformedDatabase).migrate(List.of(migration)));

        Path descriptionDatabase = temporaryDirectory.resolve("description-history.db");
        SqliteFoundation description = new SqliteFoundation(descriptionDatabase);
        runner(description, descriptionDatabase).migrate(List.of(migration));
        execute(description, "UPDATE mp_schema_migrations SET description='altered' WHERE result='APPLIED'");
        assertThrows(PersistenceException.class,
                () -> runner(description, descriptionDatabase).migrate(List.of(migration)));
    }

    @Test
    @DisplayName("Successful history initialization surfaces restoration failure")
    void surfacesRestorationFailureAfterSuccessfulHistoryInitialization() {
        Path database = temporaryDirectory.resolve("history-restoration.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        AtomicInteger commits = new AtomicInteger();
        ConnectionProvider provider = () -> restorationFailingConnection(sqlite.open(), commits, 1);
        var backup = new FileBackupService(
                database, temporaryDirectory.resolve("history-restoration-backups"), Clock.systemUTC());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> new MigrationRunner(provider, backup, Clock.systemUTC())
                        .migrate(List.of(simpleMigration(1))));

        assertEquals("Migration infrastructure failed", failure.getMessage());
        assertEquals("injected autocommit restoration failure", failure.getCause().getMessage());
        assertEquals(0, failure.getCause().getSuppressed().length);
        assertTrue(tableExists(sqlite, "mp_schema_migrations"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result IN ('APPLIED', 'FAILED')"));
        assertFalse(tableExists(sqlite, "mp_test_migration_1"));
    }

    @Test
    @DisplayName("Initialization failure stays primary when restoration also fails")
    void preservesInitializationFailureWhenRestorationAlsoFails() {
        Path database = temporaryDirectory.resolve("history-double-failure.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        ConnectionProvider provider = () -> historyInitializationFailingConnection(sqlite.open());
        var backup = new FileBackupService(
                database, temporaryDirectory.resolve("history-double-failure-backups"), Clock.systemUTC());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> new MigrationRunner(provider, backup, Clock.systemUTC())
                        .migrate(List.of(simpleMigration(1))));

        assertEquals("Migration infrastructure failed", failure.getMessage());
        assertEquals("injected history initialization failure", failure.getCause().getMessage());
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertEquals("injected autocommit restoration failure",
                failure.getCause().getSuppressed()[0].getMessage());
        assertFalse(tableExists(sqlite, "mp_schema_migrations"));
        assertFalse(tableExists(sqlite, "mp_test_migration_1"));
    }

    @Test
    @DisplayName("[A35-correction] Autocommit restoration failure never records committed work as FAILED")
    void doesNotMislabelCommittedMigrationWhenRestorationFails() {
        Path database = temporaryDirectory.resolve("restoration.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        AtomicInteger commits = new AtomicInteger();
        ConnectionProvider provider = () -> restorationFailingConnection(sqlite.open(), commits, 2);
        var backup = new FileBackupService(
                database, temporaryDirectory.resolve("restoration-backups"), Clock.systemUTC());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> new MigrationRunner(provider, backup, Clock.systemUTC())
                        .migrate(List.of(simpleMigration(1))));

        assertTrue(failure.getMessage().contains("recorded APPLIED"));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='APPLIED'"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='FAILED'"));
        assertTrue(tableExists(sqlite, "mp_test_migration_1"));
    }

    @Test
    @DisplayName("[A35-correction] Commit acknowledgement failure checks APPLIED UUID before audit")
    void doesNotMislabelCommittedMigrationWhenCommitAcknowledgementFails() {
        Path database = temporaryDirectory.resolve("commit-acknowledgement.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        AtomicInteger commits = new AtomicInteger();
        ConnectionProvider provider = () -> commitAcknowledgementFailingConnection(sqlite.open(), commits);
        var backup = new FileBackupService(
                database, temporaryDirectory.resolve("commit-acknowledgement-backups"), Clock.systemUTC());

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> new MigrationRunner(provider, backup, Clock.systemUTC())
                        .migrate(List.of(simpleMigration(1))));

        assertTrue(failure.getMessage().contains("commit acknowledgement failed"));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='APPLIED'"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE version=1 AND result='FAILED'"));
        assertTrue(tableExists(sqlite, "mp_test_migration_1"));
    }

    private MigrationRunner runner(SqliteFoundation sqlite, Path database) {
        return runner(sqlite, database, Clock.systemUTC());
    }

    private MigrationRunner runner(SqliteFoundation sqlite, Path database, Clock clock) {
        String backupName = database.getFileName().toString().replace(".db", "-backups");
        var backup = new FileBackupService(database, temporaryDirectory.resolve(backupName), clock);
        return new MigrationRunner(sqlite, backup, clock);
    }

    private static Migration simpleMigration(long version) {
        return Migration.of(version, "test migration " + version,
                List.of("CREATE TABLE mp_test_migration_" + version + " (value TEXT)"));
    }

    private static void insertFailedAttempt(SqliteFoundation sqlite, long version, String detail) {
        insertFailedAttempt(sqlite, version, Instant.parse("2026-08-17T20:00:00Z"), detail);
    }

    private static void insertFailedAttempt(
            SqliteFoundation sqlite, long version, Instant attemptedAt, String detail) {
        execute(sqlite, "INSERT INTO mp_schema_migrations (attempt_id, version, checksum, description, applied_at, "
                + "result, detail) VALUES ('" + UUID.randomUUID() + "'," + version + ",'" + "0".repeat(64)
                + "','diagnostic failed metadata','" + attemptedAt + "','FAILED','" + detail + "')");
    }

    private static void insertLegacyOperation(
            SqliteFoundation sqlite, UUID operationId, String state, ConfigRevisionId revision) {
        execute(sqlite, "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, "
                + "state, expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES ('" + operationId + "','legacy-test','" + UUID.randomUUID()
                + "','" + operationId + "','" + state + "',0,'" + revision.value()
                + "','','','2026-08-16T00:00:00Z','2026-08-16T00:00:00Z')");
    }

    private static void insertLegacyLease(
            SqliteFoundation sqlite, UUID operationId, ConfigRevisionId revision) {
        execute(sqlite, "INSERT INTO mp_stage_transition_leases (operation_id, target_stage_id, "
                + "config_revision_id, acquired_at) VALUES ('" + operationId + "','target','"
                + revision.value() + "','2026-08-16T00:00:00Z')");
    }

    private static Connection restorationFailingConnection(
            Connection delegate, AtomicInteger commits, int failAfterCommit) {
        return (Connection) Proxy.newProxyInstance(
                SqliteMigrationTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("setAutoCommit")
                            && arguments != null
                            && Boolean.TRUE.equals(arguments[0])
                            && commits.get() >= failAfterCommit) {
                        throw new SQLException("injected autocommit restoration failure");
                    }
                    try {
                        Object result = method.invoke(delegate, arguments);
                        if (method.getName().equals("commit")) {
                            commits.incrementAndGet();
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static Connection historyInitializationFailingConnection(Connection delegate) {
        AtomicInteger initializationState = new AtomicInteger();
        return (Connection) Proxy.newProxyInstance(
                SqliteMigrationTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("createStatement") && initializationState.get() == 1) {
                        throw new SQLException("injected history initialization failure");
                    }
                    if (method.getName().equals("setAutoCommit")
                            && arguments != null
                            && Boolean.TRUE.equals(arguments[0])
                            && initializationState.get() == 1) {
                        throw new SQLException("injected autocommit restoration failure");
                    }
                    try {
                        Object result = method.invoke(delegate, arguments);
                        if (method.getName().equals("setAutoCommit")
                                && arguments != null
                                && Boolean.FALSE.equals(arguments[0])) {
                            initializationState.set(1);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static Connection commitAcknowledgementFailingConnection(Connection delegate, AtomicInteger commits) {
        return (Connection) Proxy.newProxyInstance(
                SqliteMigrationTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = method.invoke(delegate, arguments);
                        if (method.getName().equals("commit") && commits.incrementAndGet() >= 2) {
                            throw new SQLException("injected commit acknowledgement failure");
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static boolean tableExists(SqliteFoundation sqlite, String name) {
        return "1".equals(scalar(sqlite, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + name
                + "'"));
    }

    private static String schemaSnapshot(SqliteFoundation sqlite) {
        return scalar(sqlite, "SELECT COALESCE(group_concat(entry, '|'), '') FROM ("
                + "SELECT type || ':' || name || ':' || COALESCE(sql, '') AS entry FROM sqlite_master "
                + "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name)");
    }

    private static void execute(SqliteFoundation sqlite, String sql) {
        try (Connection connection = sqlite.open(); var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static String scalar(SqliteFoundation sqlite, String sql) {
        try (Connection connection = sqlite.open();
                var statement = connection.createStatement();
                var row = statement.executeQuery(sql)) {
            return row.next() ? row.getString(1) : "";
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
