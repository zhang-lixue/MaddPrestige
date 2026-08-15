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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
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
        var report = runner(sqlite, database).migrate(SqliteMigrations.phaseOne());

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
    @DisplayName("[Phase1-hard-4] A partial schema cannot be relabeled as the current migration version")
    void refusesPartialSchema() throws Exception {
        Path database = temporaryDirectory.resolve("partial.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        execute(sqlite, "CREATE TABLE mp_operations (broken TEXT)");

        assertThrows(PersistenceException.class,
                () -> runner(sqlite, database).migrate(SqliteMigrations.phaseOne()));

        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result = 'APPLIED'"));
        assertEquals("0", scalar(sqlite,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='mp_config_revisions'"));
        assertEquals("1", scalar(sqlite,
                "SELECT COUNT(*) FROM mp_schema_migrations WHERE result = 'FAILED'"));
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
                .migrate(SqliteMigrations.phaseOne()));

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
    @DisplayName("[A35-correction] Autocommit restoration failure never records committed work as FAILED")
    void doesNotMislabelCommittedMigrationWhenRestorationFails() {
        Path database = temporaryDirectory.resolve("restoration.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        AtomicInteger commits = new AtomicInteger();
        ConnectionProvider provider = () -> restorationFailingConnection(sqlite.open(), commits);
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
        String backupName = database.getFileName().toString().replace(".db", "-backups");
        var backup = new FileBackupService(database, temporaryDirectory.resolve(backupName), Clock.systemUTC());
        return new MigrationRunner(sqlite, backup, Clock.systemUTC());
    }

    private static Migration simpleMigration(long version) {
        return Migration.of(version, "test migration " + version,
                List.of("CREATE TABLE mp_test_migration_" + version + " (value TEXT)"));
    }

    private static Connection restorationFailingConnection(Connection delegate, AtomicInteger commits) {
        return (Connection) Proxy.newProxyInstance(
                SqliteMigrationTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("setAutoCommit")
                            && arguments != null
                            && Boolean.TRUE.equals(arguments[0])
                            && commits.get() >= 2) {
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
