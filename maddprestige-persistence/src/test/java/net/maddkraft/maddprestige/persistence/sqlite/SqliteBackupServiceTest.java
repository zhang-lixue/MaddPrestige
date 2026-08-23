package net.maddkraft.maddprestige.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteBackupServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("[A63] Native backup seals a populated snapshot, manifest, and restore rehearsal")
    void createsValidatedPopulatedBackup() throws Exception {
        Path database = temporaryDirectory.resolve("source.sqlite");
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 10);
        Path backups = temporaryDirectory.resolve("backups");

        var result = service(source, backups).createVerifiedBackup("pre-migration qualification");

        assertTrue(result.verified(), result.detail());
        Path artifact = result.location().orElseThrow();
        assertTrue(Files.isRegularFile(artifact));
        SqliteBackupManifest manifest = SqliteBackupService.validateAcceptedBackup(
                artifact, SqliteMigrations.phaseEightC());
        assertEquals(10, manifest.sourceSchemaVersion());
        assertEquals(SqlitePhase8cFixture.REVISION, manifest.activeConfigurationRevision().orElseThrow());
        assertEquals("PASS", manifest.validationResult());
        assertEquals("PASS", manifest.restoreRehearsalResult());
        SqliteFoundation restored = new SqliteFoundation(artifact);
        assertEquals(SqlitePhase8cFixture.EXACT_DECIMAL,
                SqlitePhase8cFixture.scalar(restored, "SELECT balance_text FROM mp_currency_accounts"));
        assertEquals("NEEDS_RECONCILIATION",
                SqlitePhase8cFixture.scalar(restored, "SELECT state FROM mp_operations"));
    }

    @Test
    @DisplayName("[A63] Backup waits for an in-flight writer and includes its committed state")
    void coordinatesWithActiveWriter() throws Exception {
        Path database = temporaryDirectory.resolve("writer.sqlite");
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 10);
        Connection writer = source.open();
        writer.createStatement().execute("BEGIN IMMEDIATE");
        writer.createStatement().execute("UPDATE mp_currency_accounts SET balance_text='17.25'");
        var executor = Executors.newSingleThreadExecutor();
        try {
            var backup = executor.submit(() -> service(source, temporaryDirectory.resolve("writer-backups"))
                    .createVerifiedBackup("writer fence"));
            Thread.sleep(150);
            assertFalse(backup.isDone(), "snapshot must wait while an earlier authoritative writer is in-flight");
            writer.createStatement().execute("COMMIT");
            writer.close();

            var result = backup.get(10, TimeUnit.SECONDS);
            assertTrue(result.verified(), result.detail());
            SqliteFoundation restored = new SqliteFoundation(result.location().orElseThrow());
            assertEquals("17.25", SqlitePhase8cFixture.scalar(
                    restored, "SELECT balance_text FROM mp_currency_accounts"));
        } finally {
            if (!writer.isClosed()) {
                writer.close();
            }
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[A63] Backup drains a writer already queued at the snapshot boundary")
    void coordinatesWithQueuedWriter() throws Exception {
        Path database = temporaryDirectory.resolve("queued-writer.sqlite");
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 11);
        Connection firstWriter = source.open();
        firstWriter.createStatement().execute("BEGIN IMMEDIATE");
        CountDownLatch queuedConnectionOpen = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var queuedWriter = executor.submit(() -> {
                try (Connection connection = source.open()) {
                    queuedConnectionOpen.countDown();
                    connection.createStatement().execute("BEGIN IMMEDIATE");
                    connection.createStatement().execute(
                            "UPDATE mp_currency_accounts SET balance_text='23.75'");
                    connection.createStatement().execute("COMMIT");
                    return null;
                }
            });
            assertTrue(queuedConnectionOpen.await(2, TimeUnit.SECONDS));
            var backup = executor.submit(() -> service(source, temporaryDirectory.resolve("queued-backups"))
                    .createVerifiedBackup("queued writer fence"));
            Thread.sleep(150);
            assertFalse(backup.isDone());
            firstWriter.createStatement().execute("COMMIT");
            firstWriter.close();
            queuedWriter.get(5, TimeUnit.SECONDS);

            var result = backup.get(10, TimeUnit.SECONDS);
            assertTrue(result.verified(), result.detail());
            assertEquals("23.75", SqlitePhase8cFixture.scalar(new SqliteFoundation(result.location().orElseThrow()),
                    "SELECT balance_text FROM mp_currency_accounts"));
        } finally {
            if (!firstWriter.isClosed()) {
                firstWriter.close();
            }
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[A63] Missing manifest and manifest/hash mismatch fail closed")
    void rejectsMissingManifestAndChecksumMismatch() throws Exception {
        var result = accepted("manifest-source.sqlite", "manifest-backups");
        Path artifact = result.location().orElseThrow();
        Path manifest = SqliteBackupService.manifestPath(artifact);
        byte[] manifestBytes = Files.readAllBytes(manifest);
        Files.delete(manifest);
        assertThrows(PersistenceException.class,
                () -> SqliteBackupService.validateAcceptedBackup(artifact, SqliteMigrations.phaseEightC()));
        Files.write(manifest, manifestBytes);

        try (RandomAccessFile file = new RandomAccessFile(artifact.toFile(), "rw")) {
            file.seek(Math.min(4096, file.length() - 1));
            file.write(file.read() ^ 0x01);
        }
        assertThrows(PersistenceException.class,
                () -> SqliteBackupService.validateAcceptedBackup(artifact, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-04] Accepted manifest revalidation binds UUID, artifact, and observed journal mode")
    void rebindsAcceptedManifestIdentityAndJournalMode() throws Exception {
        var result = accepted("manifest-binding-source.sqlite", "manifest-binding-backups");
        Path artifact = result.location().orElseThrow();
        Path manifestPath = SqliteBackupService.manifestPath(artifact);
        String original = Files.readString(manifestPath);
        SqliteBackupManifest manifest = SqliteBackupManifest.read(manifestPath);

        Files.writeString(manifestPath, original.replace(
                "backupId=" + manifest.backupId(), "backupId=" + UUID.randomUUID()));
        assertThrows(PersistenceException.class,
                () -> SqliteBackupService.validateAcceptedBackup(artifact, SqliteMigrations.phaseEightC()));

        Files.writeString(manifestPath, original.replace(
                "backupId=" + manifest.backupId(), "backupId=not-a-uuid"));
        assertThrows(PersistenceException.class,
                () -> SqliteBackupService.validateAcceptedBackup(artifact, SqliteMigrations.phaseEightC()));

        String differentJournal = "wal".equals(manifest.journalMode()) ? "delete" : "wal";
        Files.writeString(manifestPath, original.replace(
                "journalMode=" + manifest.journalMode(), "journalMode=" + differentJournal));
        assertThrows(PersistenceException.class,
                () -> SqliteBackupService.validateAcceptedBackup(artifact, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-01] Schema validation rejects the migration-5 actor UUID omission")
    void rejectsMissingStageHistoryActorUuid() {
        Path database = currentDatabase("missing-actor-uuid.sqlite");
        SqlitePhase8cFixture.execute(new SqliteFoundation(database),
                "ALTER TABLE mp_stage_history DROP COLUMN actor_uuid");

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-01] Schema validation rejects a required column lost from another migration")
    void rejectsMissingManualProgressColumn() {
        Path database = currentDatabase("missing-manual-column.sqlite");
        SqlitePhase8cFixture.execute(new SqliteFoundation(database),
                "ALTER TABLE mp_manual_progress DROP COLUMN updated_at");

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-01] Schema validation rejects a readable table with malformed column semantics")
    void rejectsReadableMalformedTableShape() throws Exception {
        Path database = currentDatabase("malformed-shape.sqlite");
        rebuildWithoutForeignKeys(database, """
                ALTER TABLE mp_currency_accounts RENAME TO mp_currency_accounts_old;
                CREATE TABLE mp_currency_accounts (
                    player_uuid TEXT NOT NULL,
                    currency_id TEXT NOT NULL,
                    balance_text INTEGER NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, currency_id),
                    CHECK (length(balance_text) BETWEEN 1 AND 512)
                );
                INSERT INTO mp_currency_accounts SELECT * FROM mp_currency_accounts_old;
                DROP TABLE mp_currency_accounts_old;
                """);

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-01] Schema validation rejects removal of a correctness partial-UNIQUE index")
    void rejectsMissingPartialUniqueIndex() {
        Path database = currentDatabase("missing-partial-unique.sqlite");
        SqlitePhase8cFixture.execute(new SqliteFoundation(database),
                "DROP INDEX mp_schema_migrations_applied_version_uq");

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-01] Schema validation rejects removal of a declared foreign key")
    void rejectsMissingForeignKey() throws Exception {
        Path database = currentDatabase("missing-foreign-key.sqlite");
        rebuildWithoutForeignKeys(database, """
                ALTER TABLE mp_stage_history RENAME TO mp_stage_history_old;
                CREATE TABLE mp_stage_history (
                    history_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    stage_id TEXT NOT NULL,
                    entered_at TEXT NOT NULL,
                    operation_id TEXT NULL,
                    actor_type TEXT NOT NULL,
                    actor_name TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    config_revision_id TEXT NOT NULL,
                    actor_uuid TEXT NULL
                );
                INSERT INTO mp_stage_history SELECT * FROM mp_stage_history_old;
                DROP TABLE mp_stage_history_old;
                """);

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-06] Partial-UNIQUE string-literal case remains correctness authority")
    void rejectsCaseChangedPartialUniquePredicate() {
        Path database = currentDatabase("lowercase-season-predicate.sqlite");
        SqliteFoundation foundation = new SqliteFoundation(database);
        SqlitePhase8cFixture.execute(foundation, "DROP INDEX mp_seasons_one_active_idx");
        SqlitePhase8cFixture.execute(foundation,
                "CREATE UNIQUE INDEX mp_seasons_one_active_idx ON mp_seasons(lifecycle_state) "
                        + "WHERE lifecycle_state = 'active'");
        String insert = "INSERT INTO mp_seasons (season_id, display_name_snapshot, lifecycle_state, scope_id, "
                + "season_progress_policy, config_revision_id, started_at) VALUES (?, ?, 'ACTIVE', 'global', "
                + "'RESET', ?, '2026-08-17T20:00:00Z')";
        SqlitePhase8cFixture.execute(foundation, insert, "season-one", "Season One", SqlitePhase8cFixture.REVISION);
        SqlitePhase8cFixture.execute(foundation, insert, "season-two", "Season Two", SqlitePhase8cFixture.REVISION);

        assertEquals("2", SqlitePhase8cFixture.scalar(foundation,
                "SELECT COUNT(*) FROM mp_seasons WHERE lifecycle_state='ACTIVE'"),
                "the lowercase predicate must demonstrably permit two uppercase ACTIVE rows");
        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-06] CREATE TABLE CHECK string-literal case remains correctness authority")
    void rejectsCaseChangedTableCheckLiteral() throws Exception {
        Path database = currentDatabase("lowercase-table-check.sqlite");
        SqlitePhase8cFixture.execute(new SqliteFoundation(database), "DELETE FROM mp_operation_actions");
        rebuildWithoutForeignKeys(database, """
                ALTER TABLE mp_operation_actions RENAME TO mp_operation_actions_old;
                CREATE TABLE mp_operation_actions (
                    operation_id TEXT NOT NULL REFERENCES mp_operations(operation_id) ON DELETE CASCADE,
                    action_index INTEGER NOT NULL,
                    action_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    state TEXT NOT NULL,
                    redacted_description TEXT NOT NULL,
                    reversible INTEGER NOT NULL,
                    idempotent INTEGER NOT NULL,
                    failure_reason TEXT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (operation_id, action_index),
                    UNIQUE (operation_id, action_id),
                    CHECK (action_index >= 0),
                    CHECK (state IN ('PENDING','STARTED','SUCCEEDED','VERIFIED','FAILED','COMPENSATED','uncertain')),
                    CHECK (reversible IN (0, 1)),
                    CHECK (idempotent IN (0, 1))
                );
                DROP TABLE mp_operation_actions_old;
                """);

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[OR8C-06] SQL canonicalization changes only unquoted case and external whitespace")
    void canonicalizesOnlySemanticallyHarmlessSqlText() {
        String expected = SqliteSchemaContractValidator.canonicalizeSql(
                "  CREATE TABLE Sample (state TEXT CHECK (state = 'A  B''C'))  \n");
        String harmless = SqliteSchemaContractValidator.canonicalizeSql(
                "create   table sample (state text check (state = 'A  B''C'))");
        String changedCase = SqliteSchemaContractValidator.canonicalizeSql(
                "create table sample (state text check (state = 'a  B''C'))");
        String changedWhitespace = SqliteSchemaContractValidator.canonicalizeSql(
                "create table sample (state text check (state = 'A B''C'))");

        assertEquals(expected, harmless);
        assertNotEquals(expected, changedCase);
        assertNotEquals(expected, changedWhitespace);
    }

    @Test
    @DisplayName("[SONAR] Database-derived table names never become executable SQL identifiers")
    void rejectsUnexpectedTableNameWithoutExecutingIdentifierText() throws Exception {
        Path database = currentDatabase("hostile-table-name.sqlite");
        SqliteFoundation foundation = new SqliteFoundation(database);
        try (Connection connection = foundation.open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"mp_hostile\"\"; DROP TABLE mp_operations; --\" (value TEXT)");
        }

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
        assertEquals("1", SqlitePhase8cFixture.scalar(foundation,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='mp_operations'"));
    }

    @Test
    @DisplayName("[SONAR] Database-derived index names are bound values, never SQL fragments")
    void bindsHostileIndexNameWithoutExecutingIdentifierText() throws Exception {
        Path database = currentDatabase("hostile-index-name.sqlite");
        SqliteFoundation foundation = new SqliteFoundation(database);
        try (Connection connection = foundation.open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX \"mp_hostile\"\"; DROP TABLE mp_operations; --\" "
                    + "ON mp_audit_log(audit_id)");
        }

        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC()));
        assertEquals("1", SqlitePhase8cFixture.scalar(foundation,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='mp_operations'"));
    }

    @Test
    @DisplayName("[A63] Invalid header, truncation, and bit-flipped SQLite are rejected independently")
    void rejectsCorruptAndTruncatedDatabases() throws Exception {
        var result = accepted("corruption-source.sqlite", "corruption-backups");
        Path valid = result.location().orElseThrow();
        Path invalidHeader = temporaryDirectory.resolve("invalid.sqlite");
        Files.writeString(invalidHeader, "not a database");
        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(invalidHeader, SqliteMigrations.phaseEightC()));

        Path truncated = temporaryDirectory.resolve("truncated.sqlite");
        Files.copy(valid, truncated);
        try (RandomAccessFile file = new RandomAccessFile(truncated.toFile(), "rw")) {
            file.setLength(64);
        }
        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(truncated, SqliteMigrations.phaseEightC()));

        Path bitFlipped = temporaryDirectory.resolve("bit-flipped.sqlite");
        Files.copy(valid, bitFlipped);
        try (RandomAccessFile file = new RandomAccessFile(bitFlipped.toFile(), "rw")) {
            file.seek(file.length() / 2);
            int original = file.read();
            file.seek(file.length() / 2);
            file.write(original ^ 0xff);
        }
        assertThrows(PersistenceException.class,
                () -> SqliteDatabaseValidator.validate(bitFlipped, SqliteMigrations.phaseEightC()));
    }

    @Test
    @DisplayName("[A63] Validation failure preserves live source and previous known-good backup")
    void preservesSourceAndPreviousGoodBackup() throws Exception {
        Path database = temporaryDirectory.resolve("preservation-source.sqlite");
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 10);
        Path backups = temporaryDirectory.resolve("preservation-backups");
        var first = service(source, backups).createVerifiedBackup("known good");
        assertTrue(first.verified(), first.detail());
        var sourceBefore = SqliteBackupService.sha256(database);
        Path good = first.location().orElseThrow();
        var goodBefore = SqliteBackupService.sha256(good);

        SqlitePhase8cFixture.execute(source,
                "UPDATE mp_configuration_revision_documents SET document_hash = ?",
                "0".repeat(64));
        var invalidSourceBefore = SqliteBackupService.sha256(database);
        var second = service(source, backups).createVerifiedBackup("must fail validation");

        assertFalse(second.verified());
        assertEquals(invalidSourceBefore, SqliteBackupService.sha256(database));
        assertEquals(goodBefore, SqliteBackupService.sha256(good));
        assertTrue(Files.isRegularFile(SqliteBackupService.manifestPath(good)));
        assertFalse(sourceBefore.equals(invalidSourceBefore), "test setup must actually change the source");
        try (var files = Files.list(backups)) {
            assertEquals(2, files.count(), "only the earlier accepted database and manifest may remain");
        }
    }

    @Test
    @DisplayName("[A63] Failed disposable restore rehearsal rejects and cleans the candidate")
    void rejectsFailedRestoreRehearsal() throws Exception {
        Path database = temporaryDirectory.resolve("rehearsal-failure-source.sqlite");
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 10);
        ArrayList<Migration> broken = new ArrayList<>(SqliteMigrations.phaseEightC());
        broken.set(10, Migration.of(11, "injected rehearsal failure",
                java.util.List.of("CREATE TABLE mp_operations (duplicate TEXT)")));
        Path backups = temporaryDirectory.resolve("rehearsal-failure-backups");

        var result = new SqliteBackupService(source, backups, broken, SqlitePhase8cFixture.CLOCK)
                .createVerifiedBackup("rehearsal must fail");

        assertFalse(result.verified());
        assertTrue(result.detail().contains("restore rehearsal failed"), result.detail());
        try (var files = Files.list(backups)) {
            assertEquals(0, files.count());
        }
        assertEquals("10", SqlitePhase8cFixture.scalar(source,
                "SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'"));
    }

    @Test
    @DisplayName("[A63] Reasons are metadata only and cannot become paths")
    void rejectsUnsafeReasonWithoutWritingArtifacts() throws Exception {
        Path database = temporaryDirectory.resolve("reason-source.sqlite");
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 10);
        Path backups = temporaryDirectory.resolve("reason-backups");

        var result = service(source, backups).createVerifiedBackup("../escape\ncontrol");

        assertFalse(result.verified());
        assertFalse(Files.exists(backups));
        assertFalse(Files.exists(temporaryDirectory.resolve("escape")));
    }

    private net.maddkraft.maddprestige.persistence.VerifiedBackup accepted(
            String databaseName, String backupDirectoryName) {
        Path database = temporaryDirectory.resolve(databaseName);
        SqliteFoundation source = SqlitePhase8cFixture.historical(database, 10);
        var result = service(source, temporaryDirectory.resolve(backupDirectoryName))
                .createVerifiedBackup("accepted test source");
        assertTrue(result.verified(), result.detail());
        return result;
    }

    private Path currentDatabase(String name) {
        Path database = temporaryDirectory.resolve(name);
        SqlitePhase8cFixture.historical(database, 11);
        SqliteDatabaseValidator.validate(database, SqliteMigrations.phaseEightC());
        return database;
    }

    private static void rebuildWithoutForeignKeys(Path database, String script) throws Exception {
        try (Connection connection = new SqliteFoundation(database).open();
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=OFF");
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static SqliteBackupService service(SqliteFoundation source, Path backups) {
        return new SqliteBackupService(source, backups, SqliteMigrations.phaseEightC(), SqlitePhase8cFixture.CLOCK);
    }
}
