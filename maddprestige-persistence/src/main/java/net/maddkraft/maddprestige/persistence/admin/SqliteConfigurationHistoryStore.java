package net.maddkraft.maddprestige.persistence.admin;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteConfigurationHistoryStore implements ConfigurationHistoryStore {
    private static final int MAX_LIMIT = 1000;
    private final ConnectionProvider connections;

    public SqliteConfigurationHistoryStore(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public void append(StoredConfigurationRevision revision) {
        if (revision.status() != ConfigurationApplicationStatus.ATTEMPTED) {
            throw new IllegalArgumentException("New configuration history must begin as ATTEMPTED");
        }
        try (Connection connection = connections.open()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertLegacyReference(connection, revision);
                insertRevision(connection, revision);
                insertDocuments(connection, revision);
                connection.commit();
                connection.setAutoCommit(autoCommit);
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                connection.setAutoCommit(autoCommit);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not append configuration history", exception);
        }
    }

    @Override
    public void replaceOutcome(StoredConfigurationRevision revision) {
        String sql = "UPDATE mp_configuration_revisions_v2 SET application_status = ?, applied_at = ?, "
                + "failure_detail = ? WHERE revision_id = ? AND application_status = 'ATTEMPTED'";
        try (Connection connection = connections.open()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, revision.status().name());
                setOptional(statement, 2, revision.appliedAt().map(Instant::toString));
                setOptional(statement, 3, revision.failure());
                statement.setString(4, revision.id().value());
                if (statement.executeUpdate() != 1) {
                    throw new PersistenceException("Configuration history outcome is absent or already final");
                }
                try (PreparedStatement legacy = connection.prepareStatement(
                        "UPDATE mp_config_revisions SET applied_at = ? WHERE revision_id = ?")) {
                    setOptional(legacy, 1, revision.appliedAt().map(Instant::toString));
                    legacy.setString(2, revision.id().value());
                    legacy.executeUpdate();
                }
                connection.commit();
                connection.setAutoCommit(autoCommit);
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                connection.setAutoCommit(autoCommit);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not finalize configuration history", exception);
        }
    }

    @Override
    public Optional<StoredConfigurationRevision> find(ConfigRevisionId revisionId) {
        String sql = select() + " WHERE revision_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revisionId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(connection, row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not read configuration history", exception);
        }
    }

    @Override
    public List<StoredConfigurationRevision> recent(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Configuration history limit must be between 1 and " + MAX_LIMIT);
        }
        String sql = select() + " ORDER BY created_at DESC, revision_id DESC LIMIT ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                ArrayList<StoredConfigurationRevision> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(read(connection, rows));
                }
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not list configuration history", exception);
        }
    }

    private static void insertLegacyReference(Connection connection, StoredConfigurationRevision revision)
            throws SQLException {
        String storageHash = availableLegacyHash(connection, revision);
        String sql = "INSERT INTO mp_config_revisions (revision_id, content_hash, parent_revision_id, created_at, "
                + "applied_at, actor, source_surface, validation_summary, diff_summary, backup_checksum) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revision.id().value());
            statement.setString(2, storageHash);
            setOptional(statement, 3, revision.parent().map(ConfigRevisionId::value));
            statement.setString(4, revision.createdAt().toString());
            statement.setNull(5, Types.VARCHAR);
            statement.setString(6, revision.actor().displayName());
            statement.setString(7, revision.sourceSurface());
            statement.setString(8, validation(revision.validation()));
            statement.setString(9, revision.diffSummary());
            statement.setNull(10, Types.VARCHAR);
            statement.executeUpdate();
        }
    }

    private static String availableLegacyHash(Connection connection, StoredConfigurationRevision revision)
            throws SQLException {
        String actual = revision.compiled().contentHash().value();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision_id FROM mp_config_revisions WHERE content_hash = ?")) {
            statement.setString(1, actual);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return actual;
                }
            }
        }
        return RevisionHasher.hashText(actual + "\u0000" + revision.id().value()).value();
    }

    private static void insertRevision(Connection connection, StoredConfigurationRevision revision)
            throws SQLException {
        String sql = "INSERT INTO mp_configuration_revisions_v2 (revision_id, parent_revision_id, "
                + "rollback_source_revision_id, canonical_content_hash, actor_type, actor_uuid, actor_name, "
                + "source_surface, reason, validation_summary, diff_summary, application_status, created_at, "
                + "applied_at, failure_detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revision.id().value());
            setOptional(statement, 2, revision.parent().map(ConfigRevisionId::value));
            setOptional(statement, 3, revision.rollbackSource().map(ConfigRevisionId::value));
            statement.setString(4, revision.compiled().contentHash().value());
            statement.setString(5, revision.actor().type());
            setOptional(statement, 6, revision.actor().uuid().map(UUID::toString));
            statement.setString(7, revision.actor().displayName());
            statement.setString(8, revision.sourceSurface());
            statement.setString(9, revision.reason());
            statement.setString(10, validation(revision.validation()));
            statement.setString(11, revision.diffSummary());
            statement.setString(12, revision.status().name());
            statement.setString(13, revision.createdAt().toString());
            setOptional(statement, 14, revision.appliedAt().map(Instant::toString));
            setOptional(statement, 15, revision.failure());
            statement.executeUpdate();
        }
    }

    private static void insertDocuments(Connection connection, StoredConfigurationRevision revision)
            throws SQLException {
        String sql = "INSERT INTO mp_configuration_revision_documents "
                + "(revision_id, document_name, document_hash, document_content) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> document : revision.compiled().documents().entrySet()) {
                statement.setString(1, revision.id().value());
                statement.setString(2, document.getKey());
                statement.setString(3, RevisionHasher.hashText(document.getValue()).value());
                statement.setString(4, document.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static StoredConfigurationRevision read(Connection connection, ResultSet row) throws SQLException {
        ConfigRevisionId id = new ConfigRevisionId(row.getString(1));
        Map<String, String> documents = documents(connection, id);
        CompiledConfiguration compiled = new CompiledConfiguration(new ContentHash(row.getString(4)), documents);
        return new StoredConfigurationRevision(id, optionalRevision(row.getString(2)),
                optionalRevision(row.getString(3)), compiled,
                new Actor(row.getString(5), optionalUuid(row.getString(6)), row.getString(7)), row.getString(8),
                row.getString(9), parseValidation(row.getString(10)), row.getString(11),
                ConfigurationApplicationStatus.valueOf(row.getString(12)), Instant.parse(row.getString(13)),
                optionalInstant(row.getString(14)), Optional.ofNullable(row.getString(15)));
    }

    private static Map<String, String> documents(Connection connection, ConfigRevisionId id) throws SQLException {
        String sql = "SELECT document_name, document_hash, document_content "
                + "FROM mp_configuration_revision_documents WHERE revision_id = ? ORDER BY document_name";
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String content = rows.getString(3);
                    if (!RevisionHasher.hashText(content).value().equals(rows.getString(2))) {
                        throw new PersistenceException("Configuration history document checksum mismatch");
                    }
                    documents.put(rows.getString(1), content);
                }
            }
        }
        if (documents.isEmpty()) {
            throw new PersistenceException("Configuration history revision has no documents");
        }
        return Map.copyOf(documents);
    }

    private static String select() {
        return "SELECT revision_id, parent_revision_id, rollback_source_revision_id, canonical_content_hash, "
                + "actor_type, actor_uuid, actor_name, source_surface, reason, validation_summary, diff_summary, "
                + "application_status, created_at, applied_at, failure_detail FROM mp_configuration_revisions_v2";
    }

    private static String validation(ValidationReport report) {
        return report.findings().stream().map(finding -> String.join("\t", encode(finding.code()),
                finding.severity().name(), encode(finding.path()), encode(finding.explanation()),
                encode(finding.consequence()), encode(finding.remediation()))).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static ValidationReport parseValidation(String encoded) {
        if (encoded.isEmpty()) {
            return ValidationReport.VALID;
        }
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        for (String line : encoded.split("\n", -1)) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 6) {
                throw new PersistenceException("Malformed configuration validation history");
            }
            findings.add(new ValidationFinding(decode(fields[0]), ValidationSeverity.valueOf(fields[1]),
                    decode(fields[2]), decode(fields[3]), decode(fields[4]), decode(fields[5])));
        }
        return ValidationReport.of(findings);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Optional<ConfigRevisionId> optionalRevision(String value) {
        return value == null ? Optional.empty() : Optional.of(new ConfigRevisionId(value));
    }

    private static Optional<UUID> optionalUuid(String value) {
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    private static Optional<Instant> optionalInstant(String value) {
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static void setOptional(PreparedStatement statement, int index, Optional<String> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void rollback(Connection connection, Exception primary) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
        }
    }
}
