package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationActionPlan;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.core.operation.OperationStateMachine;
import net.maddkraft.maddprestige.persistence.OperationRepository;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.StoredOperation;
import net.maddkraft.maddprestige.persistence.StoredOperationAction;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteOperationRepository implements OperationRepository {
    private final ConnectionProvider connections;
    private final OperationStateMachine stateMachine = new OperationStateMachine();

    public SqliteOperationRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insertPrepared(OperationPlan plan) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                insertOperation(connection, plan);
                for (int index = 0; index < plan.actions().size(); index++) {
                    insertAction(connection, plan.id(), index, plan.actions().get(index));
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not persist prepared operation (duplicate idempotency keys are rejected)", exception);
        }
    }

    @Override
    public Optional<StoredOperation> find(OperationId operationId) {
        String sql = "SELECT operation_type, target_uuid, idempotency_key, state, created_at, updated_at "
                + "FROM mp_operations WHERE operation_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredOperation(operationId, row.getString(1), UUID.fromString(row.getString(2)),
                        row.getString(3), OperationState.valueOf(row.getString(4)), Instant.parse(row.getString(5)),
                        Instant.parse(row.getString(6))));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load operation", exception);
        }
    }

    @Override
    public Optional<StoredOperation> findByIdempotency(
            String operationType, UUID target, String idempotencyKey) {
        String sql = "SELECT operation_id, state, created_at, updated_at FROM mp_operations "
                + "WHERE operation_type = ? AND target_uuid = ? AND idempotency_key = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationType);
            statement.setString(2, target.toString());
            statement.setString(3, idempotencyKey);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredOperation(new OperationId(UUID.fromString(row.getString(1))),
                        operationType, target, idempotencyKey, OperationState.valueOf(row.getString(2)),
                        Instant.parse(row.getString(3)), Instant.parse(row.getString(4))));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load operation by idempotency key", exception);
        }
    }

    @Override
    public Optional<StoredOperationAction> findAction(OperationId operationId, String actionId) {
        String sql = "SELECT state, failure_reason, updated_at FROM mp_operation_actions "
                + "WHERE operation_id = ? AND action_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, actionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredOperationAction(operationId, actionId,
                        ActionState.valueOf(row.getString(1)), Optional.ofNullable(row.getString(2)),
                        Instant.parse(row.getString(3))));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load operation action", exception);
        }
    }

    @Override
    public void transition(OperationId operationId, OperationState expected, OperationState replacement) {
        stateMachine.transition(expected, replacement);
        String sql = "UPDATE mp_operations SET state = ?, updated_at = ? WHERE operation_id = ? AND state = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, replacement.name());
            statement.setString(2, Instant.now().toString());
            statement.setString(3, operationId.toString());
            statement.setString(4, expected.name());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Operation state compare-and-set failed");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not transition operation", exception);
        }
    }

    @Override
    public void transitionAction(
            OperationId operationId,
            String actionId,
            ActionState expected,
            ActionState replacement,
            Optional<String> failureReason) {
        stateMachine.transition(expected, replacement);
        String sql = "UPDATE mp_operation_actions SET state = ?, failure_reason = ?, updated_at = ? "
                + "WHERE operation_id = ? AND action_id = ? AND state = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, replacement.name());
            if (failureReason.isPresent()) {
                statement.setString(2, failureReason.orElseThrow());
            } else {
                statement.setNull(2, Types.VARCHAR);
            }
            statement.setString(3, Instant.now().toString());
            statement.setString(4, operationId.toString());
            statement.setString(5, actionId);
            statement.setString(6, expected.name());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Operation action state compare-and-set failed");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not transition operation action", exception);
        }
    }

    private static void insertOperation(Connection connection, OperationPlan plan) throws SQLException {
        String sql = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, state, "
                + "expected_state_revision, config_revision_id, provider_generations, redacted_preview, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.id().toString());
            statement.setString(2, plan.operationType());
            statement.setString(3, plan.target().toString());
            statement.setString(4, plan.idempotencyKey());
            statement.setString(5, OperationState.PREPARED.name());
            statement.setLong(6, plan.expectedStateRevision());
            statement.setString(7, plan.configRevision().value());
            String generations = plan.providerGenerations().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                    .map(entry -> entry.getKey().value() + "=" + entry.getValue()).reduce((a, b) -> a + "," + b).orElse("");
            statement.setString(8, generations);
            statement.setString(9, plan.redactedPreview());
            statement.setString(10, now.toString());
            statement.setString(11, now.toString());
            statement.executeUpdate();
        }
    }

    private static void insertAction(Connection connection, OperationId operationId, int index, OperationActionPlan action)
            throws SQLException {
        String sql = "INSERT INTO mp_operation_actions (operation_id, action_index, action_id, provider_id, action_type, "
                + "state, redacted_description, reversible, idempotent, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setInt(2, index);
            statement.setString(3, action.actionId());
            statement.setString(4, action.providerId().value());
            statement.setString(5, action.actionType());
            statement.setString(6, ActionState.PENDING.name());
            statement.setString(7, action.redactedDescription());
            statement.setInt(8, action.reversible() ? 1 : 0);
            statement.setInt(9, action.idempotent() ? 1 : 0);
            statement.setString(10, Instant.now().toString());
            statement.executeUpdate();
        }
    }
}
