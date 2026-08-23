package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

/** Explicit post-PRE lifecycle that atomically establishes initial stage and Prestige rows. */
public final class SqlitePlayerInitializationStore {
    private final ConnectionProvider connections;

    public SqlitePlayerInitializationStore(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connection provider");
    }

    public void initialize(
            UUID playerId,
            StageId stageId,
            ConfigRevisionId revision,
            ScopeId prestigeScope,
            Instant now) {
        initialize(playerId, stageId, revision, prestigeScope, now, List.of());
    }

    public void initialize(
            UUID playerId,
            StageId stageId,
            ConfigRevisionId revision,
            ScopeId prestigeScope,
            Instant now,
            List<RequirementBaseline> baselines) {
        Objects.requireNonNull(playerId, "player ID");
        Objects.requireNonNull(stageId, "stage ID");
        Objects.requireNonNull(revision, "configuration revision");
        Objects.requireNonNull(prestigeScope, "Prestige scope");
        Objects.requireNonNull(now, "initialization time");
        List<RequirementBaseline> pinnedBaselines = List.copyOf(Objects.requireNonNull(baselines, "baselines"));
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                SqliteStageTransitionGuard.requireNoPendingRemap(connection, java.util.List.of(stageId));
                insertStage(connection, playerId, stageId, revision, now);
                insertPrestige(connection, playerId, revision, prestigeScope, now);
                for (RequirementBaseline baseline : pinnedBaselines) {
                    if (!baseline.key().playerId().equals(playerId)
                            || !baseline.key().scopeInstance().equals(prestigeScope)) {
                        throw new PersistenceException("Initial baseline does not belong to the player lifecycle");
                    }
                    insertBaseline(connection, baseline);
                }
                verify(connection, playerId, stageId, revision, prestigeScope);
                for (RequirementBaseline baseline : pinnedBaselines) {
                    verifyBaseline(connection, baseline);
                }
                SqliteStageTransitionGuard.commit(connection);
            } catch (SQLException | RuntimeException failure) {
                SqliteStageTransitionGuard.rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("Could not atomically initialize player progression lifecycle", failure);
        }
    }

    private static void insertBaseline(Connection connection, RequirementBaseline baseline) throws SQLException {
        String sql = "INSERT OR IGNORE INTO mp_requirement_baselines "
                + "(player_uuid, requirement_id, measurement_scope, scope_instance, semantic_fingerprint, "
                + "value_type, value_text, provider_generation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                if (!row.next() || !baseline.value().type().name().equals(row.getString(1))
                        || !baseline.value().canonical().equals(row.getString(2))
                        || baseline.providerGeneration() != row.getLong(3)) {
                    throw new PersistenceException("Initial requirement baseline raced with different state");
                }
            }
        }
    }

    private static void insertStage(
            Connection connection,
            UUID playerId,
            StageId stageId,
            ConfigRevisionId revision,
            Instant now) throws SQLException {
        String sql = "INSERT OR IGNORE INTO mp_player_stage_state (player_uuid, stage_id, state_revision, "
                + "config_revision_id, stage_entered_at, created_at, updated_at, last_reconciled_at, "
                + "last_provider_generation, imported_at) VALUES (?, ?, 0, ?, ?, ?, ?, NULL, NULL, NULL)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, stageId.value());
            statement.setString(3, revision.value());
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.setString(6, now.toString());
            statement.executeUpdate();
        }
    }

    private static void insertPrestige(
            Connection connection,
            UUID playerId,
            ConfigRevisionId revision,
            ScopeId prestigeScope,
            Instant now) throws SQLException {
        String sql = "INSERT OR IGNORE INTO mp_player_prestige_state (player_uuid, current_prestige, "
                + "lifetime_prestige, state_revision, config_revision_id, prestige_scope_id, last_prestiged_at, "
                + "created_at, updated_at) VALUES (?, 0, 0, 0, ?, ?, NULL, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, revision.value());
            statement.setString(3, prestigeScope.value());
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.executeUpdate();
        }
    }

    private static void verify(
            Connection connection,
            UUID playerId,
            StageId stageId,
            ConfigRevisionId revision,
            ScopeId prestigeScope) throws SQLException {
        String sql = "SELECT s.stage_id, s.state_revision, s.config_revision_id, p.current_prestige, "
                + "p.lifetime_prestige, p.state_revision, p.config_revision_id, p.prestige_scope_id "
                + "FROM mp_player_stage_state s JOIN mp_player_prestige_state p ON p.player_uuid = s.player_uuid "
                + "WHERE s.player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !stageId.value().equals(row.getString(1)) || row.getLong(2) != 0
                        || !revision.value().equals(row.getString(3)) || row.getLong(4) != 0 || row.getLong(5) != 0
                        || row.getLong(6) != 0 || !revision.value().equals(row.getString(7))
                        || !prestigeScope.value().equals(row.getString(8))) {
                    throw new PersistenceException("Player initialization raced with different durable state");
                }
            }
        }
    }
}
