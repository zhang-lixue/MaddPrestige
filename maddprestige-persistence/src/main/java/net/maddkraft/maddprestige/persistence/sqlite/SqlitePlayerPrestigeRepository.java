package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.PlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqlitePlayerPrestigeRepository implements PlayerPrestigeRepository {
    private static final String INSERT = "INSERT INTO mp_player_prestige_state (player_uuid, current_prestige, "
            + "lifetime_prestige, state_revision, config_revision_id, prestige_scope_id, last_prestiged_at, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String FIND = "SELECT current_prestige, lifetime_prestige, state_revision, "
            + "config_revision_id, prestige_scope_id, last_prestiged_at, created_at, updated_at "
            + "FROM mp_player_prestige_state WHERE player_uuid = ?";
    private final ConnectionProvider connections;

    public SqlitePlayerPrestigeRepository(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public Optional<PlayerPrestigeState> find(UUID playerId) {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(playerId, row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load player Prestige state", exception);
        }
    }

    @Override
    public void insert(PlayerPrestigeState state) {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bindInsert(statement, state);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not insert player Prestige state", exception);
        }
    }

    @Override
    public void update(PlayerPrestigeState replacement, long expectedRevision) {
        if (replacement.stateRevision() != Math.addExact(expectedRevision, 1)) {
            throw new IllegalArgumentException("Replacement Prestige revision must increment exactly once");
        }
        String sql = "UPDATE mp_player_prestige_state SET current_prestige = ?, lifetime_prestige = ?, "
                + "state_revision = ?, config_revision_id = ?, prestige_scope_id = ?, last_prestiged_at = ?, "
                + "updated_at = ? WHERE player_uuid = ? AND state_revision = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, replacement.currentPrestige());
            statement.setLong(2, replacement.lifetimePrestige());
            statement.setLong(3, replacement.stateRevision());
            statement.setString(4, replacement.configRevision().value());
            statement.setString(5, replacement.prestigeScope().value());
            setInstant(statement, 6, replacement.lastPrestigedAt());
            statement.setString(7, replacement.updatedAt().toString());
            statement.setString(8, replacement.playerId().toString());
            statement.setLong(9, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new StalePlayerStageStateException("Player Prestige compare-and-set failed");
            }
        } catch (StalePlayerStageStateException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not update player Prestige state", exception);
        }
    }

    private static void bindInsert(PreparedStatement statement, PlayerPrestigeState state) throws SQLException {
        statement.setString(1, state.playerId().toString());
        statement.setLong(2, state.currentPrestige());
        statement.setLong(3, state.lifetimePrestige());
        statement.setLong(4, state.stateRevision());
        statement.setString(5, state.configRevision().value());
        statement.setString(6, state.prestigeScope().value());
        setInstant(statement, 7, state.lastPrestigedAt());
        statement.setString(8, state.createdAt().toString());
        statement.setString(9, state.updatedAt().toString());
    }

    private static PlayerPrestigeState read(UUID playerId, ResultSet row) throws SQLException {
        return new PlayerPrestigeState(playerId, row.getLong(1), row.getLong(2), row.getLong(3),
                new ConfigRevisionId(row.getString(4)), new ScopeId(row.getString(5)),
                optionalInstant(row.getString(6)), Instant.parse(row.getString(7)), Instant.parse(row.getString(8)));
    }

    private static Optional<Instant> optionalInstant(String value) {
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static void setInstant(
            PreparedStatement statement,
            int index,
            Optional<Instant> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow().toString());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }
}
