package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.audit.AuditRecord;
import net.maddkraft.maddprestige.api.audit.AuditValue;
import net.maddkraft.maddprestige.persistence.AuditRepository;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteAuditRepository implements AuditRepository {
    private final ConnectionProvider connections;

    public SqliteAuditRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void append(AuditRecord record) {
        String sql = "INSERT INTO mp_audit_log (audit_id, actor_type, actor_uuid, actor_name, target_uuid, operation_id, "
                + "config_revision_id, provider_action, old_value, new_value, values_redacted, source_surface, reason, "
                + "outcome, failure_uncertainty, correlation_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        boolean sensitive = record.oldValue().map(AuditValue::sensitive).orElse(false)
                || record.newValue().map(AuditValue::sensitive).orElse(false);
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.auditId().toString());
            statement.setString(2, record.actor().type());
            setOptional(statement, 3, record.actor().uuid().map(UUID::toString));
            statement.setString(4, record.actor().displayName());
            setOptional(statement, 5, record.target().map(UUID::toString));
            setOptional(statement, 6, record.operationId().map(Object::toString));
            setOptional(statement, 7, record.configRevision().map(revision -> revision.value()));
            statement.setString(8, record.providerAction());
            setOptional(statement, 9, record.oldValue().map(value -> value.render(false)));
            setOptional(statement, 10, record.newValue().map(value -> value.render(false)));
            statement.setInt(11, sensitive ? 1 : 0);
            statement.setString(12, record.sourceSurface());
            statement.setString(13, record.reason());
            statement.setString(14, record.outcome().name());
            setOptional(statement, 15, record.failureOrUncertainty());
            statement.setString(16, record.correlationId().toString());
            statement.setString(17, record.timestamp().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not append audit record", exception);
        }
    }

    private static void setOptional(PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }
}
