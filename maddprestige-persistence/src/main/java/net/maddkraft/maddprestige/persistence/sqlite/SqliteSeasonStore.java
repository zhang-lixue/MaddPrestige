package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.season.SeasonDefinition;
import net.maddkraft.maddprestige.core.season.SeasonLifecycleState;
import net.maddkraft.maddprestige.core.season.SeasonRecord;
import net.maddkraft.maddprestige.core.season.SeasonStore;
import net.maddkraft.maddprestige.core.config.phase4.ResetDisposition;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteSeasonStore implements SeasonStore {
    private static final String COLUMNS = "season_id, display_name_snapshot, lifecycle_state, scope_id, "
            + "season_progress_policy, config_revision_id, started_at, ended_at, archived_at";
    private static final String INSERT_SEASON = "INSERT INTO mp_seasons (season_id, display_name_snapshot, "
            + "lifecycle_state, scope_id, season_progress_policy, config_revision_id, started_at) "
            + "VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)";
    private static final String INSERT_HISTORY = "INSERT INTO mp_season_history (history_id, season_id, event_type, "
            + "display_name_snapshot, scope_id, config_revision_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private final ConnectionProvider connections;

    public SqliteSeasonStore(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public SeasonRecord start(
            SeasonDefinition definition,
            ScopeId scopeId,
            ConfigRevisionId revision,
            Instant now) {
        if (active().seasonId().isPresent()) {
            throw new IllegalStateException("Only one active season is allowed");
        }
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SEASON)) {
                statement.setString(1, definition.id().value());
                statement.setString(2, definition.displayName());
                statement.setString(3, scopeId.value());
                ResetDisposition policy = definition.progressPolicy();
                statement.setString(4, policy.name());
                statement.setString(5, revision.value());
                statement.setString(6, now.toString());
                statement.executeUpdate();
                history(connection, definition.id(), "STARTED", definition.displayName(), scopeId, revision, now);
                connection.commit();
                return new SeasonRecord(definition.id(), definition.displayName(), SeasonLifecycleState.ACTIVE,
                        scopeId, policy, revision, now, Optional.empty(), Optional.empty());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not start season (only one active season is allowed)", exception);
        }
    }

    @Override
    public SeasonRecord endAndArchive(SeasonId seasonId, Instant now) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                SeasonRecord current = find(connection, seasonId).orElseThrow(
                        () -> new PersistenceException("Season does not exist: " + seasonId.value()));
                if (current.state() != SeasonLifecycleState.ACTIVE) {
                    throw new IllegalStateException("Season is not active: " + seasonId.value());
                }
                String sql = "UPDATE mp_seasons SET lifecycle_state = 'ARCHIVED', ended_at = ?, archived_at = ? "
                        + "WHERE season_id = ? AND lifecycle_state = 'ACTIVE'";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, now.toString());
                    statement.setString(2, now.toString());
                    statement.setString(3, seasonId.value());
                    if (statement.executeUpdate() != 1) {
                        throw new PersistenceException("Season lifecycle compare-and-set failed");
                    }
                }
                history(connection, seasonId, "ENDED", current.displayNameSnapshot(), current.scopeId(),
                        current.configRevision(), now);
                history(connection, seasonId, "ARCHIVED", current.displayNameSnapshot(), current.scopeId(),
                        current.configRevision(), now);
                connection.commit();
                return new SeasonRecord(seasonId, current.displayNameSnapshot(), SeasonLifecycleState.ARCHIVED,
                        current.scopeId(), current.progressPolicy(), current.configRevision(), current.startedAt(),
                        Optional.of(now), Optional.of(now));
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not end/archive season", exception);
        }
    }

    @Override
    public Optional<SeasonRecord> find(SeasonId seasonId) {
        try (Connection connection = connections.open()) {
            return find(connection, seasonId);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load season", exception);
        }
    }

    @Override
    public List<SeasonRecord> history(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Season history limit must be 1-1000");
        }
        String sql = "SELECT " + COLUMNS + " FROM mp_seasons ORDER BY started_at DESC, season_id LIMIT ?";
        ArrayList<SeasonRecord> result = new ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(read(rows));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load season archive", exception);
        }
    }

    @Override
    public ActiveSeasonContext active() {
        String sql = "SELECT season_id, scope_id FROM mp_seasons WHERE lifecycle_state = 'ACTIVE'";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet row = statement.executeQuery()) {
            return row.next() ? new ActiveSeasonContext(Optional.of(new SeasonId(row.getString(1))),
                    Optional.of(new ScopeId(row.getString(2)))) : ActiveSeasonContext.none();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load active season", exception);
        }
    }

    @Override
    public void enterPlayer(
            UUID playerId,
            SeasonId seasonId,
            List<RequirementBaseline> baselines,
            Instant now) {
        String sql = "INSERT INTO mp_player_season_state (player_uuid, season_id, progress_text, entered_at, "
                + "updated_at) SELECT ?, ?, ?, ?, ? FROM mp_seasons "
                + "WHERE season_id = ? AND lifecycle_state = 'ACTIVE' "
                + "ON CONFLICT(player_uuid, season_id) DO NOTHING";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                SeasonRecord season = find(connection, seasonId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown season"));
                if (season.state() != SeasonLifecycleState.ACTIVE) {
                    throw new IllegalStateException("Cannot enter an inactive season: " + seasonId.value());
                }
                ExactDecimal initial = season.progressPolicy() == ResetDisposition.PRESERVE
                        ? mostRecentArchivedProgress(connection, playerId) : ExactDecimal.ZERO;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, seasonId.value());
                    statement.setString(3, initial.toString());
                    statement.setString(4, now.toString());
                    statement.setString(5, now.toString());
                    statement.setString(6, seasonId.value());
                    statement.executeUpdate();
                }
                for (RequirementBaseline baseline : List.copyOf(baselines)) {
                    if (!baseline.key().playerId().equals(playerId)
                            || !baseline.key().scopeInstance().equals(season.scopeId())
                            || baseline.key().scope()
                                    != net.maddkraft.maddprestige.core.requirement.MeasurementScope.SINCE_SEASON_START) {
                        throw new IllegalArgumentException("Season baseline does not belong to this player/scope");
                    }
                    insertBaseline(connection, baseline);
                    verifyBaseline(connection, baseline);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not atomically establish player season boundary/baselines",
                    exception);
        }
    }

    @Override
    public void setPlayerProgress(UUID playerId, SeasonId seasonId, ExactDecimal progress, Instant now) {
        if (progress.asBigDecimal().signum() < 0 || progress.precision() > 38 || progress.scale() > 18) {
            throw new IllegalArgumentException("Season progress is outside exact decimal bounds");
        }
        String sql = "UPDATE mp_player_season_state SET progress_text = ?, updated_at = ? "
                + "WHERE player_uuid = ? AND season_id = ? AND EXISTS (SELECT 1 FROM mp_seasons "
                + "WHERE season_id = ? AND lifecycle_state = 'ACTIVE')";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, progress.toString());
            statement.setString(2, now.toString());
            statement.setString(3, playerId.toString());
            statement.setString(4, seasonId.value());
            statement.setString(5, seasonId.value());
            if (statement.executeUpdate() != 1) {
                SeasonRecord season = find(connection, seasonId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown season"));
                if (season.state() != SeasonLifecycleState.ACTIVE) {
                    throw new IllegalStateException("Cannot update progress for inactive season: "
                            + seasonId.value());
                }
                throw new PersistenceException("Player has not entered the season");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not update player season progress", exception);
        }
    }

    @Override
    public ExactDecimal playerProgress(UUID playerId, SeasonId seasonId) {
        String sql = "SELECT progress_text FROM mp_player_season_state WHERE player_uuid = ? AND season_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, seasonId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? ExactDecimal.parse(row.getString(1)) : ExactDecimal.ZERO;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load player season progress", exception);
        }
    }

    private static Optional<SeasonRecord> find(Connection connection, SeasonId seasonId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM mp_seasons WHERE season_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, seasonId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    private static SeasonRecord read(ResultSet row) throws SQLException {
        return new SeasonRecord(new SeasonId(row.getString(1)), row.getString(2),
                SeasonLifecycleState.valueOf(row.getString(3)), new ScopeId(row.getString(4)),
                ResetDisposition.valueOf(row.getString(5)), new ConfigRevisionId(row.getString(6)),
                Instant.parse(row.getString(7)), optional(row.getString(8)), optional(row.getString(9)));
    }

    private static ExactDecimal mostRecentArchivedProgress(Connection connection, UUID playerId)
            throws SQLException {
        String sql = "SELECT player.progress_text FROM mp_player_season_state player "
                + "JOIN mp_seasons season ON season.season_id = player.season_id "
                + "WHERE player.player_uuid = ? AND season.lifecycle_state = 'ARCHIVED' "
                + "ORDER BY season.archived_at DESC, season.season_id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? ExactDecimal.parse(row.getString(1)) : ExactDecimal.ZERO;
            }
        }
    }

    private static Optional<Instant> optional(String value) {
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static void insertBaseline(Connection connection, RequirementBaseline baseline) throws SQLException {
        String sql = "INSERT OR IGNORE INTO mp_requirement_baselines (player_uuid, requirement_id, "
                + "measurement_scope, scope_instance, semantic_fingerprint, value_type, value_text, "
                + "provider_generation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            var key = baseline.key();
            statement.setString(1, key.playerId().toString());
            statement.setString(2, key.requirementId().value());
            statement.setString(3, key.scope().name());
            statement.setString(4, key.scopeInstance().value());
            statement.setString(5, key.semanticFingerprint());
            statement.setString(6, baseline.value().type().name());
            statement.setString(7, baseline.value().canonical());
            statement.setLong(8, baseline.providerGeneration());
            statement.setString(9, baseline.createdAt().toString());
            statement.executeUpdate();
        }
    }

    private static void verifyBaseline(Connection connection, RequirementBaseline baseline) throws SQLException {
        String sql = "SELECT value_type, value_text, provider_generation FROM mp_requirement_baselines "
                + "WHERE player_uuid = ? AND requirement_id = ? AND measurement_scope = ? "
                + "AND scope_instance = ? AND semantic_fingerprint = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            var key = baseline.key();
            statement.setString(1, key.playerId().toString());
            statement.setString(2, key.requirementId().value());
            statement.setString(3, key.scope().name());
            statement.setString(4, key.scopeInstance().value());
            statement.setString(5, key.semanticFingerprint());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !row.getString(1).equals(baseline.value().type().name())
                        || !row.getString(2).equals(baseline.value().canonical())
                        || row.getLong(3) != baseline.providerGeneration()) {
                    throw new PersistenceException("Existing season baseline conflicts with the sealed boundary");
                }
            }
        }
    }

    private static void history(
            Connection connection,
            SeasonId id,
            String event,
            String display,
            ScopeId scope,
            ConfigRevisionId revision,
            Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_HISTORY)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, id.value());
            statement.setString(3, event);
            statement.setString(4, display);
            statement.setString(5, scope.value());
            statement.setString(6, revision.value());
            statement.setString(7, now.toString());
            statement.executeUpdate();
        }
    }
}
