package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.PlayerStageRepository;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.StageHistoryRecord;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqlitePlayerStageRepository implements PlayerStageRepository {
    private static final String COLUMNS = "player_uuid, stage_id, state_revision, config_revision_id, "
            + "stage_entered_at, created_at, updated_at, last_reconciled_at, last_provider_generation, imported_at";
    private static final String INSERT_SQL = """
            INSERT INTO mp_player_stage_state (
                player_uuid, stage_id, state_revision, config_revision_id, stage_entered_at,
                created_at, updated_at, last_reconciled_at, last_provider_generation, imported_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String IMPORT_ONCE_INSERT_SQL = """
            INSERT INTO mp_player_stage_state (
                player_uuid, stage_id, state_revision, config_revision_id, stage_entered_at,
                created_at, updated_at, last_reconciled_at, last_provider_generation, imported_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO NOTHING
            """;
    private final ConnectionProvider connections;

    public SqlitePlayerStageRepository(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public Optional<PlayerStageState> find(UUID playerId) {
        String sql = "SELECT " + COLUMNS + " FROM mp_player_stage_state WHERE player_uuid = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load player stage state", exception);
        }
    }

    @Override
    public void insert(PlayerStageState state) {
        if (!insertInternal(state, false)) {
            throw new PersistenceException("Player stage state already exists: " + state.playerId());
        }
    }

    @Override
    public boolean importOnce(PlayerStageState state) {
        if (state.importedAt().isEmpty()) {
            throw new IllegalArgumentException("Import-once state must record importedAt");
        }
        return insertInternal(state, true);
    }

    @Override
    public void update(PlayerStageState replacement, long expectedRevision) {
        if (replacement.stateRevision() != Math.addExact(expectedRevision, 1)) {
            throw new IllegalArgumentException("Replacement revision must increment expected revision exactly once");
        }
        String sql = "UPDATE mp_player_stage_state SET stage_id = ?, state_revision = ?, config_revision_id = ?, "
                + "stage_entered_at = ?, updated_at = ?, last_reconciled_at = ?, last_provider_generation = ?, "
                + "imported_at = ? WHERE player_uuid = ? AND state_revision = ?";
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                StageId sourceStage = currentStage(connection, replacement.playerId(), expectedRevision);
                SqliteStageTransitionGuard.requireNoPendingRemap(
                        connection, java.util.List.of(sourceStage, replacement.stageId()));
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, replacement.stageId().value());
                    statement.setLong(2, replacement.stateRevision());
                    statement.setString(3, replacement.configRevision().value());
                    statement.setString(4, replacement.stageEnteredAt().toString());
                    statement.setString(5, replacement.updatedAt().toString());
                    setOptionalInstant(statement, 6, replacement.lastReconciledAt());
                    setOptionalLong(statement, 7, replacement.lastProviderGeneration());
                    setOptionalInstant(statement, 8, replacement.importedAt());
                    statement.setString(9, replacement.playerId().toString());
                    statement.setLong(10, expectedRevision);
                    if (statement.executeUpdate() != 1) {
                        throw new StalePlayerStageStateException(
                                "Player stage compare-and-set failed for " + replacement.playerId());
                    }
                }
                SqliteStageTransitionGuard.commit(connection);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (StalePlayerStageStateException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not update player stage state", exception);
        }
    }

    @Override
    public void updateAndAppendHistory(
            PlayerStageState replacement,
            long expectedRevision,
            StageHistoryRecord history) {
        if (!history.playerId().equals(replacement.playerId())
                || !history.stageId().equals(replacement.stageId())
                || !history.configRevision().equals(replacement.configRevision())
                || !history.enteredAt().equals(replacement.stageEnteredAt())) {
            throw new IllegalArgumentException("Stage history must describe the exact replacement state");
        }
        if (replacement.stateRevision() != Math.addExact(expectedRevision, 1)) {
            throw new IllegalArgumentException("Replacement revision must increment expected revision exactly once");
        }
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                StageId sourceStage = currentStage(connection, replacement.playerId(), expectedRevision);
                SqliteStageTransitionGuard.requireNoPendingRemap(
                        connection, java.util.List.of(sourceStage, replacement.stageId()));
                update(connection, replacement, expectedRevision);
                insertHistory(connection, history);
                SqliteStageTransitionGuard.commit(connection);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (StalePlayerStageStateException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not atomically update stage and append history", exception);
        }
    }

    @Override
    public java.util.List<StageHistoryRecord> history(UUID playerId, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Stage history limit must be 1-1000");
        }
        String sql = "SELECT stage_id, entered_at, operation_id, actor_type, actor_uuid, actor_name, reason, "
                + "config_revision_id FROM mp_stage_history WHERE player_uuid = ? "
                + "ORDER BY entered_at DESC, history_id LIMIT ?";
        java.util.ArrayList<StageHistoryRecord> result = new java.util.ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StageHistoryRecord(playerId, new StageId(rows.getString(1)),
                            Instant.parse(rows.getString(2)),
                            new net.maddkraft.maddprestige.api.id.OperationId(UUID.fromString(rows.getString(3))),
                            new net.maddkraft.maddprestige.api.operation.Actor(rows.getString(4),
                                    Optional.ofNullable(rows.getString(5)).map(UUID::fromString), rows.getString(6)),
                            rows.getString(7), new ConfigRevisionId(rows.getString(8))));
                }
            }
            return java.util.List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load stage history", exception);
        }
    }

    @Override
    public Map<StageId, Long> countByStage() {
        String sql = "SELECT stage_id, COUNT(*) FROM mp_player_stage_state GROUP BY stage_id ORDER BY stage_id";
        LinkedHashMap<StageId, Long> counts = new LinkedHashMap<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                counts.put(new StageId(rows.getString(1)), rows.getLong(2));
            }
            return Map.copyOf(counts);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not count player stage references", exception);
        }
    }

    private boolean insertInternal(PlayerStageState state, boolean ignoreExisting) {
        String sql = ignoreExisting ? IMPORT_ONCE_INSERT_SQL : INSERT_SQL;
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                SqliteStageTransitionGuard.requireNoPendingRemap(connection, java.util.List.of(state.stageId()));
                boolean inserted;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bind(statement, state);
                    inserted = statement.executeUpdate() == 1;
                }
                SqliteStageTransitionGuard.commit(connection);
                return inserted;
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not insert player stage state", exception);
        }
    }

    private static StageId currentStage(Connection connection, UUID playerId, long expectedRevision)
            throws SQLException {
        String sql = "SELECT stage_id FROM mp_player_stage_state WHERE player_uuid = ? AND state_revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, expectedRevision);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new StalePlayerStageStateException(
                            "Player stage compare-and-set failed for " + playerId);
                }
                return new StageId(row.getString(1));
            }
        }
    }

    private static void update(
            Connection connection,
            PlayerStageState replacement,
            long expectedRevision) throws SQLException {
        String sql = "UPDATE mp_player_stage_state SET stage_id = ?, state_revision = ?, config_revision_id = ?, "
                + "stage_entered_at = ?, updated_at = ?, last_reconciled_at = ?, last_provider_generation = ?, "
                + "imported_at = ? WHERE player_uuid = ? AND state_revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, replacement.stageId().value());
            statement.setLong(2, replacement.stateRevision());
            statement.setString(3, replacement.configRevision().value());
            statement.setString(4, replacement.stageEnteredAt().toString());
            statement.setString(5, replacement.updatedAt().toString());
            setOptionalInstant(statement, 6, replacement.lastReconciledAt());
            setOptionalLong(statement, 7, replacement.lastProviderGeneration());
            setOptionalInstant(statement, 8, replacement.importedAt());
            statement.setString(9, replacement.playerId().toString());
            statement.setLong(10, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new StalePlayerStageStateException(
                        "Player stage compare-and-set failed for " + replacement.playerId());
            }
        }
    }

    private static void insertHistory(Connection connection, StageHistoryRecord history) throws SQLException {
        String sql = "INSERT INTO mp_stage_history (history_id, player_uuid, stage_id, entered_at, operation_id, "
                + "actor_type, actor_uuid, actor_name, reason, config_revision_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, history.playerId().toString());
            statement.setString(3, history.stageId().value());
            statement.setString(4, history.enteredAt().toString());
            statement.setString(5, history.operationId().toString());
            statement.setString(6, history.actor().type());
            if (history.actor().uuid().isPresent()) {
                statement.setString(7, history.actor().uuid().orElseThrow().toString());
            } else {
                statement.setNull(7, Types.VARCHAR);
            }
            statement.setString(8, history.actor().displayName());
            statement.setString(9, history.reason());
            statement.setString(10, history.configRevision().value());
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, PlayerStageState state) throws SQLException {
        statement.setString(1, state.playerId().toString());
        statement.setString(2, state.stageId().value());
        statement.setLong(3, state.stateRevision());
        statement.setString(4, state.configRevision().value());
        statement.setString(5, state.stageEnteredAt().toString());
        statement.setString(6, state.createdAt().toString());
        statement.setString(7, state.updatedAt().toString());
        setOptionalInstant(statement, 8, state.lastReconciledAt());
        setOptionalLong(statement, 9, state.lastProviderGeneration());
        setOptionalInstant(statement, 10, state.importedAt());
    }

    private static PlayerStageState read(ResultSet row) throws SQLException {
        long providerGeneration = row.getLong(9);
        Optional<Long> generation = row.wasNull() ? Optional.empty() : Optional.of(providerGeneration);
        return new PlayerStageState(UUID.fromString(row.getString(1)), new StageId(row.getString(2)), row.getLong(3),
                new ConfigRevisionId(row.getString(4)), Instant.parse(row.getString(5)), Instant.parse(row.getString(6)),
                Instant.parse(row.getString(7)), optionalInstant(row.getString(8)), generation,
                optionalInstant(row.getString(10)));
    }

    private static Optional<Instant> optionalInstant(String value) {
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static void setOptionalInstant(
            PreparedStatement statement, int index, Optional<Instant> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow().toString());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void setOptionalLong(
            PreparedStatement statement, int index, Optional<Long> value) throws SQLException {
        if (value.isPresent()) {
            statement.setLong(index, value.orElseThrow());
        } else {
            statement.setNull(index, Types.BIGINT);
        }
    }
}
