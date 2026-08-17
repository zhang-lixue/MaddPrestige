package net.maddkraft.maddprestige.persistence.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdjustment;
import net.maddkraft.maddprestige.core.admin.PrestigeAdministrationStore;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqlitePrestigeAdministrationStore implements PrestigeAdministrationStore {
    private static final String FIND = "SELECT current_prestige, lifetime_prestige, state_revision, "
            + "config_revision_id, prestige_scope_id, last_prestiged_at, created_at, updated_at "
            + "FROM mp_player_prestige_state WHERE player_uuid = ?";
    private final ConnectionProvider connections;
    private final Clock clock;

    public SqlitePrestigeAdministrationStore(ConnectionProvider connections, Clock clock) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlayerPrestigeState adjust(ManualPrestigeAdjustment adjustment) {
        try (Connection connection = connections.open()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                PlayerPrestigeState current = find(connection, adjustment.playerId()).orElseThrow(() ->
                        new PersistenceException("Player has no authoritative Prestige state"));
                if (current.stateRevision() != adjustment.expectedStateRevision()) {
                    throw new StalePlayerStageStateException("Manual Prestige compare-and-set failed");
                }
                Instant now = Instant.now(clock);
                PlayerPrestigeState replacement = new PlayerPrestigeState(current.playerId(),
                        adjustment.currentPrestige(), adjustment.lifetimePrestige(),
                        Math.addExact(current.stateRevision(), 1), adjustment.configRevision(),
                        current.prestigeScope(), current.lastPrestigedAt(), current.createdAt(), now);
                update(connection, replacement, current.stateRevision());
                appendAudit(connection, adjustment, current, replacement, now);
                connection.commit();
                connection.setAutoCommit(originalAutoCommit);
                return replacement;
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                connection.setAutoCommit(originalAutoCommit);
                if (exception instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new PersistenceException("Could not adjust player Prestige", exception);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not open Prestige administration transaction", exception);
        }
    }

    private static Optional<PlayerPrestigeState> find(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerPrestigeState(playerId, row.getLong(1), row.getLong(2), row.getLong(3),
                        new ConfigRevisionId(row.getString(4)), new ScopeId(row.getString(5)),
                        optionalInstant(row.getString(6)), Instant.parse(row.getString(7)),
                        Instant.parse(row.getString(8))));
            }
        }
    }

    private static void update(
            Connection connection,
            PlayerPrestigeState replacement,
            long expectedRevision) throws SQLException {
        String sql = "UPDATE mp_player_prestige_state SET current_prestige = ?, lifetime_prestige = ?, "
                + "state_revision = ?, config_revision_id = ?, updated_at = ? "
                + "WHERE player_uuid = ? AND state_revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, replacement.currentPrestige());
            statement.setLong(2, replacement.lifetimePrestige());
            statement.setLong(3, replacement.stateRevision());
            statement.setString(4, replacement.configRevision().value());
            statement.setString(5, replacement.updatedAt().toString());
            statement.setString(6, replacement.playerId().toString());
            statement.setLong(7, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new StalePlayerStageStateException("Manual Prestige compare-and-set failed");
            }
        }
    }

    private static void appendAudit(
            Connection connection,
            ManualPrestigeAdjustment adjustment,
            PlayerPrestigeState oldState,
            PlayerPrestigeState newState,
            Instant now) throws SQLException {
        String sql = "INSERT INTO mp_audit_log (audit_id, actor_type, actor_uuid, actor_name, target_uuid, "
                + "operation_id, config_revision_id, provider_action, old_value, new_value, values_redacted, "
                + "source_surface, reason, outcome, failure_uncertainty, correlation_id, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, adjustment.actor().type());
            setOptional(statement, 3, adjustment.actor().uuid().map(UUID::toString));
            statement.setString(4, adjustment.actor().displayName());
            statement.setString(5, adjustment.playerId().toString());
            statement.setNull(6, Types.VARCHAR);
            statement.setString(7, adjustment.configRevision().value());
            statement.setString(8, "player.prestige.manual_set");
            statement.setString(9, values(oldState));
            statement.setString(10, values(newState));
            statement.setInt(11, 0);
            statement.setString(12, adjustment.sourceSurface());
            statement.setString(13, adjustment.reason());
            statement.setString(14, "SUCCESS");
            statement.setNull(15, Types.VARCHAR);
            statement.setString(16, UUID.randomUUID().toString());
            statement.setString(17, now.toString());
            statement.executeUpdate();
        }
    }

    private static String values(PlayerPrestigeState state) {
        return "current=" + state.currentPrestige() + ",lifetime=" + state.lifetimePrestige()
                + ",state-revision=" + state.stateRevision();
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
