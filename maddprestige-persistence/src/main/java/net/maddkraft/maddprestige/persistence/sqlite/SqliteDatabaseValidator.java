package net.maddkraft.maddprestige.persistence.sqlite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationHistoryRules;
import net.maddkraft.maddprestige.persistence.migration.MigrationRecord;
import net.maddkraft.maddprestige.persistence.migration.MigrationResult;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Independently validates a closed SQLite backup without using the live database connection. */
public final class SqliteDatabaseValidator {
    private static final byte[] SQLITE_HEADER = "SQLite format 3\000".getBytes(StandardCharsets.US_ASCII);
    private SqliteDatabaseValidator() {
    }

    public static SqliteValidationResult validate(Path database, List<Migration> migrations) {
        Path normalized = database.toAbsolutePath().normalize();
        validateMigrationChain(migrations);
        validateHeader(normalized);
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setBusyTimeout(5000);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + normalized);
        try (Connection connection = dataSource.getConnection()) {
            requireIntegrity(connection);
            requireNoForeignKeyViolations(connection);
            long schemaVersion = validateHistory(connection, migrations);
            LinkedHashMap<String, Long> rows = validateStructures(connection, migrations, schemaVersion);
            Optional<String> activeRevision = validateConfigurationHistory(connection, schemaVersion);
            return new SqliteValidationResult(
                    schemaVersion, activeRevision, scalarText(connection, "PRAGMA journal_mode"), rows);
        } catch (SQLException exception) {
            throw new PersistenceException("SQLite backup validation failed for " + normalized + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static void validateHeader(Path database) {
        try {
            if (!Files.isRegularFile(database) || Files.size(database) < SQLITE_HEADER.length) {
                throw new PersistenceException("SQLite backup is missing or truncated before its database header");
            }
            byte[] header = new byte[SQLITE_HEADER.length];
            try (var input = Files.newInputStream(database)) {
                int offset = 0;
                while (offset < header.length) {
                    int read = input.read(header, offset, header.length - offset);
                    if (read < 0) {
                        throw new PersistenceException("SQLite backup is truncated before its database header");
                    }
                    offset += read;
                }
            }
            if (!java.util.Arrays.equals(SQLITE_HEADER, header)) {
                throw new PersistenceException("SQLite backup has an invalid database header");
            }
        } catch (IOException exception) {
            throw new PersistenceException("Could not read SQLite backup header", exception);
        }
    }

    private static void requireIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA integrity_check")) {
            if (!rows.next() || !"ok".equalsIgnoreCase(rows.getString(1)) || rows.next()) {
                throw new PersistenceException("SQLite integrity_check did not return exactly one 'ok' result");
            }
        }
    }

    private static void requireNoForeignKeyViolations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (rows.next()) {
                throw new PersistenceException("SQLite foreign_key_check found a violation in table "
                        + rows.getString(1));
            }
        }
    }

    private static long validateHistory(Connection connection, List<Migration> migrations) throws SQLException {
        if (!tableExists(connection, "mp_schema_migrations")) {
            if (applicationSchemaCount(connection) != 0) {
                throw new PersistenceException("MaddPrestige tables exist without migration history");
            }
            return 0;
        }
        ArrayList<MigrationRecord> records = new ArrayList<>();
        LinkedHashMap<Long, Integer> appliedCounts = new LinkedHashMap<>();
        LinkedHashMap<Long, ContentHash> appliedChecksums = new LinkedHashMap<>();
        LinkedHashMap<Long, String> appliedDescriptions = new LinkedHashMap<>();
        String sql = "SELECT attempt_id, version, checksum, description, applied_at, result, detail "
                + "FROM mp_schema_migrations "
                + "ORDER BY version, applied_at, attempt_id";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                long version = rows.getLong(2);
                if (version < 1 || version > migrations.size()) {
                    throw new PersistenceException("Unknown/future migration history version " + version);
                }
                MigrationRecord record;
                try {
                    record = new MigrationRecord(
                            java.util.UUID.fromString(rows.getString(1)), version,
                            new ContentHash(rows.getString(3)), rows.getString(4),
                            java.time.Instant.parse(rows.getString(5)), MigrationResult.valueOf(rows.getString(6)),
                            rows.getString(7));
                } catch (IllegalArgumentException | NullPointerException exception) {
                    throw new PersistenceException("Malformed migration history at version " + version, exception);
                }
                records.add(record);
                if (record.result() == MigrationResult.APPLIED) {
                    appliedCounts.merge(version, 1, Integer::sum);
                    appliedChecksums.put(version, record.checksum());
                    appliedDescriptions.put(version, record.description());
                }
            }
        }
        long version = 0;
        for (long expected = 1; expected <= appliedCounts.size(); expected++) {
            if (appliedCounts.getOrDefault(expected, 0) != 1) {
                throw new PersistenceException("Migration history is duplicate or has a gap at version " + expected);
            }
            Migration migration = migrations.get(Math.toIntExact(expected - 1));
            if (!migration.checksum().equals(appliedChecksums.get(expected))) {
                throw new PersistenceException("Migration checksum mismatch at version " + expected);
            }
            if (!migration.description().equals(appliedDescriptions.get(expected))) {
                throw new PersistenceException("Migration description mismatch at version " + expected);
            }
            version = expected;
        }
        if (version == 0 && applicationSchemaCount(connection) != 0) {
            throw new PersistenceException("Application schema exists without an APPLIED migration prefix");
        }
        MigrationHistoryRules.validateAttemptOrdering(records, version, migrations.size());
        return version;
    }

    private static LinkedHashMap<String, Long> validateStructures(
            Connection connection, List<Migration> migrations, long version)
            throws SQLException {
        return SqliteSchemaContractValidator.validate(connection, migrations, version);
    }

    private static Optional<String> validateConfigurationHistory(Connection connection, long version)
            throws SQLException {
        if (version < 6) {
            return Optional.empty();
        }
        String sql = "SELECT revision_id, canonical_content_hash, application_status, applied_at "
                + "FROM mp_configuration_revisions_v2 "
                + "ORDER BY COALESCE(applied_at, created_at), created_at, revision_id";
        String activeRevision = null;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String revision = rows.getString(1);
                String expectedHash = rows.getString(2);
                String status = rows.getString(3);
                if ("APPLIED".equals(status) && rows.getString(4) == null) {
                    throw new PersistenceException("Applied configuration revision has no applied_at: " + revision);
                }
                Map<String, String> documents = configurationDocuments(connection, revision);
                if (documents.isEmpty()) {
                    throw new PersistenceException("Configuration revision has no durable documents: " + revision);
                }
                if (!RevisionHasher.hashDocuments(documents).equals(new ContentHash(expectedHash))) {
                    throw new PersistenceException("Configuration canonical hash mismatch for revision " + revision);
                }
                if ("APPLIED".equals(status)) {
                    activeRevision = revision;
                }
            }
        }
        requireNoBrokenConfigurationParents(connection);
        return Optional.ofNullable(activeRevision);
    }

    private static Map<String, String> configurationDocuments(Connection connection, String revision)
            throws SQLException {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        String sql = "SELECT document_name, document_hash, document_content "
                + "FROM mp_configuration_revision_documents WHERE revision_id = ? ORDER BY document_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revision);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString(1);
                    String content = rows.getString(3);
                    if (!RevisionHasher.hashText(content).equals(new ContentHash(rows.getString(2)))) {
                        throw new PersistenceException("Configuration document hash mismatch for " + revision
                                + "/" + name);
                    }
                    documents.put(name, content);
                }
            }
        }
        return documents;
    }

    private static void requireNoBrokenConfigurationParents(Connection connection) throws SQLException {
        String sql = "SELECT child.revision_id FROM mp_configuration_revisions_v2 child "
                + "LEFT JOIN mp_configuration_revisions_v2 parent ON parent.revision_id = child.parent_revision_id "
                + "WHERE child.parent_revision_id IS NOT NULL AND parent.revision_id IS NULL LIMIT 1";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (rows.next()) {
                throw new PersistenceException("Configuration parent revision is missing for " + rows.getString(1));
            }
        }
        String rollbackSql = "SELECT child.revision_id FROM mp_configuration_revisions_v2 child "
                + "LEFT JOIN mp_configuration_revisions_v2 source "
                + "ON source.revision_id = child.rollback_source_revision_id "
                + "WHERE child.rollback_source_revision_id IS NOT NULL AND source.revision_id IS NULL LIMIT 1";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(rollbackSql)) {
            if (rows.next()) {
                throw new PersistenceException("Configuration rollback source is missing for " + rows.getString(1));
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static long applicationSchemaCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master "
                        + "WHERE type = 'table' AND name LIKE 'mp\\_%' ESCAPE '\\' "
                        + "AND name <> 'mp_schema_migrations'")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String scalarText(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                throw new PersistenceException("SQLite diagnostic query returned no rows: " + sql);
            }
            return rows.getString(1);
        }
    }

    private static void validateMigrationChain(List<Migration> migrations) {
        if (migrations == null || migrations.isEmpty()) {
            throw new IllegalArgumentException("Expected migration chain is required");
        }
        for (int index = 0; index < migrations.size(); index++) {
            if (migrations.get(index).version() != index + 1L) {
                throw new IllegalArgumentException("Expected migration chain must be the contiguous prefix 1 through N");
            }
        }
    }
}
