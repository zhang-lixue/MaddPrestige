package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.LatchKey;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementLatch;
import net.maddkraft.maddprestige.core.requirement.RequirementStateWriter;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteRequirementStateRepository implements RequirementStateWriter {
    private static final String FIND_BASELINE = "SELECT value_type, value_text, provider_generation, created_at "
            + "FROM mp_requirement_baselines WHERE player_uuid = ? AND requirement_id = ? "
            + "AND measurement_scope = ? AND scope_instance = ? AND semantic_fingerprint = ?";
    private static final String FIND_LATCH = "SELECT completed_at FROM mp_requirement_latches "
            + "WHERE player_uuid = ? AND requirement_id = ? AND measurement_scope = ? "
            + "AND scope_instance = ? AND semantic_fingerprint = ?";
    private static final String INSERT_BASELINE = "INSERT OR IGNORE INTO mp_requirement_baselines "
            + "(player_uuid, requirement_id, measurement_scope, scope_instance, semantic_fingerprint, value_type, "
            + "value_text, provider_generation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_LATCH = "INSERT OR IGNORE INTO mp_requirement_latches "
            + "(player_uuid, requirement_id, measurement_scope, scope_instance, semantic_fingerprint, completed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
    private final ConnectionProvider connections;

    public SqliteRequirementStateRepository(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(FIND_BASELINE)) {
            bindKey(statement, key.playerId().toString(), key.requirementId().value(), key.scope().name(),
                    key.scopeInstance().value(), key.semanticFingerprint());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                MetricValue value = MetricValue.parse(MetricValueType.valueOf(row.getString(1)), row.getString(2));
                return Optional.of(new RequirementBaseline(key, value, row.getLong(3), Instant.parse(row.getString(4))));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load requirement baseline", exception);
        }
    }

    @Override
    public Optional<RequirementLatch> findLatch(LatchKey key) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(FIND_LATCH)) {
            bindKey(statement, key.playerId().toString(), key.requirementId().value(), key.scope().name(),
                    key.scopeInstance().value(), key.semanticFingerprint());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(new RequirementLatch(key, Instant.parse(row.getString(1))))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load requirement latch", exception);
        }
    }

    @Override
    public RequirementBaseline initializeBaseline(RequirementBaseline baseline) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(INSERT_BASELINE)) {
            BaselineKey key = baseline.key();
            bindKey(statement, key.playerId().toString(), key.requirementId().value(), key.scope().name(),
                    key.scopeInstance().value(), key.semanticFingerprint());
            statement.setString(6, baseline.value().type().name());
            statement.setString(7, baseline.value().canonical());
            statement.setLong(8, baseline.providerGeneration());
            statement.setString(9, baseline.createdAt().toString());
            statement.executeUpdate();
            return findBaseline(key).orElseThrow(() -> new PersistenceException("Baseline insert was not observable"));
        } catch (SQLException exception) {
            throw new PersistenceException("Could not initialize requirement baseline", exception);
        }
    }

    @Override
    public RequirementLatch recordLatch(RequirementLatch latch) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(INSERT_LATCH)) {
            LatchKey key = latch.key();
            bindKey(statement, key.playerId().toString(), key.requirementId().value(), key.scope().name(),
                    key.scopeInstance().value(), key.semanticFingerprint());
            statement.setString(6, latch.completedAt().toString());
            statement.executeUpdate();
            return findLatch(key).orElseThrow(() -> new PersistenceException("Latch insert was not observable"));
        } catch (SQLException exception) {
            throw new PersistenceException("Could not record requirement latch", exception);
        }
    }

    private static void bindKey(
            PreparedStatement statement,
            String playerId,
            String requirementId,
            String scope,
            String instance,
            String fingerprint) throws SQLException {
        statement.setString(1, playerId);
        statement.setString(2, requirementId);
        statement.setString(3, scope);
        statement.setString(4, instance);
        statement.setString(5, fingerprint);
    }
}
