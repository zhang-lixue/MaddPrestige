package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.core.manual.ManualProgressRecord;
import net.maddkraft.maddprestige.core.manual.ManualProgressRepository;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteManualProgressRepository implements ManualProgressRepository {
    private static final String LOAD = "SELECT player_uuid, value_type, value_text, update_version, provenance, "
            + "updated_at FROM mp_manual_progress WHERE provider_id = ? AND metric_id = ?";
    private static final String UPSERT = "INSERT INTO mp_manual_progress "
            + "(provider_id, metric_id, player_uuid, value_type, value_text, update_version, provenance, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(provider_id, metric_id, player_uuid) DO UPDATE SET "
            + "value_type = excluded.value_type, value_text = excluded.value_text, "
            + "update_version = excluded.update_version, provenance = excluded.provenance, "
            + "updated_at = excluded.updated_at WHERE excluded.update_version > mp_manual_progress.update_version";
    private final ConnectionProvider connections;

    public SqliteManualProgressRepository(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public Map<UUID, ManualProgressRecord> load(ProviderId providerId, MetricId metricId) {
        LinkedHashMap<UUID, ManualProgressRecord> records = new LinkedHashMap<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(LOAD)) {
            statement.setString(1, providerId.value());
            statement.setString(2, metricId.value());
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    UUID playerId = UUID.fromString(row.getString(1));
                    MetricValue value = MetricValue.parse(MetricValueType.valueOf(row.getString(2)), row.getString(3));
                    records.put(playerId, new ManualProgressRecord(providerId, metricId, playerId, value,
                            row.getLong(4), row.getString(5), Instant.parse(row.getString(6))));
                }
            }
            return Map.copyOf(records);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load manual progress", exception);
        }
    }

    @Override
    public void writeBatch(Collection<ManualProgressRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try (Connection connection = connections.open()) {
            boolean priorAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (ManualProgressRecord record : records) {
                    statement.setString(1, record.providerId().value());
                    statement.setString(2, record.metricId().value());
                    statement.setString(3, record.playerId().toString());
                    statement.setString(4, record.value().type().name());
                    statement.setString(5, record.value().canonical());
                    statement.setLong(6, record.updateVersion());
                    statement.setString(7, record.provenance());
                    statement.setString(8, record.updatedAt().toString());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(priorAutoCommit);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not flush manual progress batch", exception);
        }
    }
}
