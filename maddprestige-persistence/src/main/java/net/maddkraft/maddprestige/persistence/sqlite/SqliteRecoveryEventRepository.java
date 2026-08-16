package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.RecoveryEvent;
import net.maddkraft.maddprestige.persistence.RecoveryEventRepository;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteRecoveryEventRepository implements RecoveryEventRepository {
    private final ConnectionProvider connections;

    public SqliteRecoveryEventRepository(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public void append(RecoveryEvent event) {
        String sql = "INSERT INTO mp_recovery_events (recovery_id, operation_id, previous_state, "
                + "resulting_state, decision, detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.recoveryId().toString());
            statement.setString(2, event.operationId().toString());
            statement.setString(3, event.previousState().name());
            statement.setString(4, event.resultingState().name());
            statement.setString(5, event.decision());
            statement.setString(6, event.detail());
            statement.setString(7, event.occurredAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not append recovery event", exception);
        }
    }

    @Override
    public List<RecoveryEvent> find(OperationId operationId, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Recovery event limit must be 1-1000");
        }
        String sql = "SELECT recovery_id, previous_state, resulting_state, decision, detail, occurred_at "
                + "FROM mp_recovery_events WHERE operation_id = ? ORDER BY occurred_at, recovery_id LIMIT ?";
        ArrayList<RecoveryEvent> result = new ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new RecoveryEvent(UUID.fromString(rows.getString(1)), operationId,
                            OperationState.valueOf(rows.getString(2)), OperationState.valueOf(rows.getString(3)),
                            rows.getString(4), rows.getString(5), Instant.parse(rows.getString(6))));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recovery events", exception);
        }
    }
}
