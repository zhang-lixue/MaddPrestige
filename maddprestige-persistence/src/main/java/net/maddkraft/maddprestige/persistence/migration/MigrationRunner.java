package net.maddkraft.maddprestige.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.persistence.BackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class MigrationRunner {
    private static final MigrationHistorySnapshot EMPTY_HISTORY =
            new MigrationHistorySnapshot(List.of(), List.of());
    private final ConnectionProvider connections;
    private final BackupService backups;
    private final Clock clock;

    public MigrationRunner(ConnectionProvider connections, BackupService backups, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.backups = Objects.requireNonNull(backups, "backups");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MigrationReport migrate(List<Migration> requestedMigrations) {
        List<Migration> migrations = validateChain(requestedMigrations);
        MigrationHistorySnapshot beforeBackup = inspectHistory(migrations);
        List<MigrationRecord> applied = beforeBackup.applied();
        if (applied.size() == migrations.size()) {
            return new MigrationReport(applied, false);
        }

        List<Migration> pending = migrations.subList(applied.size(), migrations.size());
        VerifiedBackup backup = backups.createVerifiedBackup("schema migrations "
                + pending.getFirst().version() + " through " + pending.getLast().version());
        if (!backup.verified()) {
            throw new PersistenceException("Migration blocked because a verified backup could not be produced: "
                    + backup.detail());
        }

        try (Connection connection = connections.open()) {
            MigrationHistorySnapshot afterBackup = historyTableExists(connection)
                    ? validatedHistory(connection, migrations) : EMPTY_HISTORY;
            validateAppliedHistory(migrations, afterBackup.applied());
            if (!afterBackup.equals(beforeBackup)) {
                throw new PersistenceException("Complete migration attempt ledger changed while the pre-migration "
                        + "backup was sealed; refusing to apply a stale plan");
            }
            if (afterBackup.applied().isEmpty()
                    && !historyTableExists(connection)
                    && applicationSchemaExists(connection)) {
                throw new PersistenceException("Application schema appeared while the pre-migration backup was sealed");
            }
            initializeHistory(connection);
            ArrayList<MigrationRecord> newRecords = new ArrayList<>();
            for (Migration migration : pending) {
                newRecords.add(apply(connection, migration));
            }
            ArrayList<MigrationRecord> all = new ArrayList<>(applied);
            all.addAll(newRecords);
            return new MigrationReport(all, true);
        } catch (SQLException exception) {
            throw new PersistenceException("Migration infrastructure failed", exception);
        }
    }

    private MigrationHistorySnapshot inspectHistory(List<Migration> migrations) {
        try (Connection connection = connections.open()) {
            boolean hasHistory = historyTableExists(connection);
            if (!hasHistory && applicationSchemaExists(connection)) {
                throw new PersistenceException("Database contains MaddPrestige schema objects but no migration "
                        + "history; refusing an ambiguous migration replay");
            }
            MigrationHistorySnapshot history = hasHistory
                    ? validatedHistory(connection, migrations) : EMPTY_HISTORY;
            validateAppliedHistory(migrations, history.applied());
            return history;
        } catch (SQLException exception) {
            throw new PersistenceException("Migration history inspection failed", exception);
        }
    }

    private MigrationRecord apply(Connection connection, Migration migration) {
        boolean priorAutoCommit;
        try {
            priorAutoCommit = connection.getAutoCommit();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not inspect migration transaction state", exception);
        }

        MigrationRecord appliedRecord = record(migration, MigrationResult.APPLIED, "Applied successfully");
        try {
            connection.setAutoCommit(false);
            for (String sql : migration.statements()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
            insertRecord(connection, appliedRecord);
            connection.commit();
        } catch (SQLException exception) {
            FailureDisposition disposition = recordFailedAttempt(
                    connection, migration, appliedRecord, priorAutoCommit, exception);
            throw switch (disposition) {
                case COMMITTED -> new PersistenceException("Migration " + migration.version()
                        + " committed and was recorded APPLIED, but commit acknowledgement failed", exception);
                case ROLLED_BACK -> new PersistenceException(
                        "Migration " + migration.version() + " failed; schema was not marked current", exception);
                case UNCERTAIN -> new PersistenceException("Migration " + migration.version()
                        + " failed with uncertain transaction outcome; no FAILED record was emitted", exception);
            };
        }

        try {
            connection.setAutoCommit(priorAutoCommit);
        } catch (SQLException exception) {
            throw new PersistenceException("Migration " + migration.version()
                    + " committed and was recorded APPLIED, but connection state restoration failed", exception);
        }
        return appliedRecord;
    }

    private FailureDisposition recordFailedAttempt(
            Connection connection,
            Migration migration,
            MigrationRecord appliedRecord,
            boolean priorAutoCommit,
            SQLException migrationFailure) {
        boolean rolledBack = false;
        try {
            connection.rollback();
            rolledBack = true;
        } catch (SQLException rollbackFailure) {
            migrationFailure.addSuppressed(rollbackFailure);
        }

        FailureDisposition disposition = FailureDisposition.UNCERTAIN;
        if (rolledBack) {
            Boolean applied = appliedAttemptExists(connection, appliedRecord.attemptId(), migrationFailure);
            if (Boolean.TRUE.equals(applied)) {
                disposition = FailureDisposition.COMMITTED;
            } else if (Boolean.FALSE.equals(applied)) {
                disposition = FailureDisposition.ROLLED_BACK;
                try {
                    connection.setAutoCommit(false);
                    insertRecord(connection, record(migration, MigrationResult.FAILED, safeDetail(migrationFailure)));
                    connection.commit();
                } catch (SQLException auditFailure) {
                    migrationFailure.addSuppressed(auditFailure);
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        migrationFailure.addSuppressed(rollbackFailure);
                    }
                }
            }
        }

        try {
            connection.setAutoCommit(priorAutoCommit);
        } catch (SQLException restorationFailure) {
            migrationFailure.addSuppressed(restorationFailure);
        }
        return disposition;
    }

    private static Boolean appliedAttemptExists(
            Connection connection,
            UUID attemptId,
            SQLException migrationFailure) {
        String sql = "SELECT COUNT(*) FROM mp_schema_migrations WHERE attempt_id = ? AND result = 'APPLIED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, attemptId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getLong(1) == 1;
            }
        } catch (SQLException verificationFailure) {
            migrationFailure.addSuppressed(verificationFailure);
            return null;
        }
    }

    private MigrationRecord record(Migration migration, MigrationResult result, String detail) {
        return new MigrationRecord(UUID.randomUUID(), migration.version(), migration.checksum(), migration.description(),
                clock.instant(), result, detail);
    }

    private static String safeDetail(SQLException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "SQL failure without driver detail";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private static List<Migration> validateChain(List<Migration> requested) {
        Objects.requireNonNull(requested, "migrations");
        List<Migration> migrations = List.copyOf(requested);
        long expectedVersion = 1;
        for (Migration migration : migrations) {
            if (migration.version() != expectedVersion) {
                throw new IllegalArgumentException("Migration chain must be the exact contiguous prefix 1 through N; "
                        + "expected version " + expectedVersion + " but found " + migration.version());
            }
            expectedVersion++;
        }
        return migrations;
    }

    private static void validateAppliedHistory(List<Migration> migrations, List<MigrationRecord> applied) {
        Map<Long, Migration> requestedByVersion = new LinkedHashMap<>();
        migrations.forEach(migration -> requestedByVersion.put(migration.version(), migration));
        for (MigrationRecord record : applied) {
            if (!requestedByVersion.containsKey(record.version())) {
                throw new PersistenceException("Database contains unknown applied migration version "
                        + record.version());
            }
        }
        if (applied.size() > migrations.size()) {
            throw new PersistenceException("Applied migration history is longer than the requested chain");
        }
        for (int index = 0; index < applied.size(); index++) {
            MigrationRecord record = applied.get(index);
            Migration expected = migrations.get(index);
            if (record.version() != expected.version()) {
                throw new PersistenceException("Applied migrations do not form an ordered prefix; expected version "
                        + expected.version() + " but found " + record.version());
            }
            if (!expected.checksum().equals(record.checksum())) {
                throw new PersistenceException("Applied migration checksum mismatch at version " + record.version());
            }
            if (!expected.description().equals(record.description())) {
                throw new PersistenceException("Applied migration description mismatch at version "
                        + record.version());
            }
        }
    }

    private static MigrationHistorySnapshot validatedHistory(
            Connection connection, List<Migration> migrations) throws SQLException {
        Map<Long, Migration> expected = new LinkedHashMap<>();
        migrations.forEach(migration -> expected.put(migration.version(), migration));
        String sql = "SELECT attempt_id, version, checksum, description, applied_at, result, detail "
                + "FROM mp_schema_migrations ORDER BY version, applied_at, attempt_id";
        ArrayList<MigrationRecord> records = new ArrayList<>();
        ArrayList<MigrationRecord> applied = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                long version = rows.getLong(2);
                if (!expected.containsKey(version)) {
                    throw new PersistenceException("Database contains unknown migration history version " + version);
                }
                MigrationRecord record;
                try {
                    record = new MigrationRecord(
                            UUID.fromString(rows.getString(1)), version, new ContentHash(rows.getString(3)),
                            rows.getString(4), Instant.parse(rows.getString(5)),
                            MigrationResult.valueOf(rows.getString(6)), rows.getString(7));
                } catch (IllegalArgumentException | NullPointerException exception) {
                    throw new PersistenceException("Malformed migration history at version " + version, exception);
                }
                if (record.result() == MigrationResult.APPLIED) {
                    applied.add(record);
                }
                records.add(record);
            }
        }
        if (applied.isEmpty() && applicationSchemaExists(connection)) {
            throw new PersistenceException("Migration history is empty but application schema objects exist; "
                    + "refusing an ambiguous migration replay");
        }
        MigrationHistoryRules.validateAttemptOrdering(records, applied.size(), migrations.size());
        return new MigrationHistorySnapshot(records, applied);
    }

    private static boolean historyTableExists(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, MigrationHistorySchema.TABLE, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (MigrationHistorySchema.TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean applicationSchemaExists(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type IN ('table', 'index', 'trigger', 'view') "
                + "AND name LIKE 'mp\\_%' ESCAPE '\\' AND name <> '" + MigrationHistorySchema.TABLE + "' "
                + "AND name <> '" + MigrationHistorySchema.APPLIED_INDEX + "'";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() && rows.getLong(1) > 0;
        }
    }

    private static void initializeHistory(Connection connection) throws SQLException {
        boolean priorAutoCommit = connection.getAutoCommit();
        SQLException primaryFailure = null;
        SQLException restorationFailure = null;
        try {
            connection.setAutoCommit(false);
            MigrationHistorySchema.initialize(connection);
            connection.commit();
        } catch (SQLException exception) {
            primaryFailure = exception;
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
        } finally {
            try {
                connection.setAutoCommit(priorAutoCommit);
            } catch (SQLException exception) {
                restorationFailure = exception;
            }
        }

        if (primaryFailure != null) {
            if (restorationFailure != null) {
                primaryFailure.addSuppressed(restorationFailure);
            }
            throw primaryFailure;
        }
        if (restorationFailure != null) {
            throw restorationFailure;
        }
    }

    private static void insertRecord(Connection connection, MigrationRecord record) throws SQLException {
        String sql = "INSERT INTO mp_schema_migrations "
                + "(attempt_id, version, checksum, description, applied_at, result, detail) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.attemptId().toString());
            statement.setLong(2, record.version());
            statement.setString(3, record.checksum().value());
            statement.setString(4, record.description());
            statement.setString(5, record.appliedAt().toString());
            statement.setString(6, record.result().name());
            statement.setString(7, record.detail());
            statement.executeUpdate();
        }
    }

    private enum FailureDisposition {
        COMMITTED,
        ROLLED_BACK,
        UNCERTAIN
    }

    private record MigrationHistorySnapshot(List<MigrationRecord> attempts, List<MigrationRecord> applied) {
        private MigrationHistorySnapshot {
            attempts = List.copyOf(attempts);
            applied = List.copyOf(applied);
        }
    }

}
