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
    private static final String HISTORY_TABLE = "mp_schema_migrations";
    private static final String APPLIED_INDEX = "mp_schema_migrations_applied_version_uq";
    private static final String CREATE_HISTORY = """
            CREATE TABLE IF NOT EXISTS mp_schema_migrations (
                attempt_id VARCHAR(36) PRIMARY KEY,
                version BIGINT NOT NULL,
                checksum VARCHAR(64) NOT NULL,
                description VARCHAR(255) NOT NULL,
                applied_at VARCHAR(40) NOT NULL,
                result VARCHAR(16) NOT NULL,
                detail VARCHAR(1024) NOT NULL,
                CHECK (result IN ('APPLIED', 'FAILED'))
            )
            """;
    private static final String DROP_LEGACY_APPLIED_INDEX = "DROP INDEX IF EXISTS " + APPLIED_INDEX;
    private static final String CREATE_APPLIED_INDEX = """
            CREATE UNIQUE INDEX mp_schema_migrations_applied_version_uq
            ON mp_schema_migrations(version)
            WHERE result = 'APPLIED'
            """;

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
        try (Connection connection = connections.open()) {
            List<MigrationRecord> applied = historyTableExists(connection)
                    ? appliedRecords(connection)
                    : List.of();
            validateAppliedHistory(migrations, applied);
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
        long prior = 0;
        for (Migration migration : migrations) {
            if (migration.version() <= prior) {
                throw new IllegalArgumentException("Migration versions must be unique and strictly ordered");
            }
            prior = migration.version();
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
        }
    }

    private static boolean historyTableExists(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, HISTORY_TABLE, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (HISTORY_TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void initializeHistory(Connection connection) throws SQLException {
        boolean priorAutoCommit = connection.getAutoCommit();
        SQLException primaryFailure = null;
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_HISTORY);
                statement.execute(DROP_LEGACY_APPLIED_INDEX);
                statement.execute(CREATE_APPLIED_INDEX);
            }
            connection.commit();
        } catch (SQLException exception) {
            primaryFailure = exception;
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        } finally {
            try {
                connection.setAutoCommit(priorAutoCommit);
            } catch (SQLException restorationFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(restorationFailure);
                } else {
                    throw restorationFailure;
                }
            }
        }
    }

    private static List<MigrationRecord> appliedRecords(Connection connection) throws SQLException {
        ArrayList<MigrationRecord> result = new ArrayList<>();
        String sql = "SELECT attempt_id, version, checksum, description, applied_at, result, detail "
                + "FROM mp_schema_migrations WHERE result = 'APPLIED' ORDER BY version";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(new MigrationRecord(
                        UUID.fromString(rows.getString(1)), rows.getLong(2), new ContentHash(rows.getString(3)),
                        rows.getString(4), Instant.parse(rows.getString(5)), MigrationResult.valueOf(rows.getString(6)),
                        rows.getString(7)));
            }
        }
        return List.copyOf(result);
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

}
