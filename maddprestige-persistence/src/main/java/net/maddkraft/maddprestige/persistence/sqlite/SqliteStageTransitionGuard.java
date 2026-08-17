package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;

/** SQLite-wide serialization shared by direct stage writes and destructive configuration remaps. */
public final class SqliteStageTransitionGuard {
    private SqliteStageTransitionGuard() {
    }

    public static void beginImmediate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
        }
    }

    public static void commit(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("COMMIT");
        }
    }

    public static void rollback(Connection connection, Throwable failure) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    public static void requireNoPendingRemap(Connection connection, Collection<StageId> stages)
            throws SQLException {
        LinkedHashSet<StageId> unique = new LinkedHashSet<>(Objects.requireNonNull(stages, "stages"));
        requireNoMalformedConfigurationTransition(connection);
        String reservationSql = "SELECT reservation.config_revision_id, transition.status "
                + "FROM mp_configuration_stage_reservations reservation "
                + "JOIN mp_configuration_stage_transitions transition "
                + "ON transition.config_revision_id = reservation.config_revision_id "
                + "WHERE reservation.stage_id = ? LIMIT 1";
        for (StageId stage : unique) {
            try (PreparedStatement statement = connection.prepareStatement(reservationSql)) {
                statement.setString(1, stage.value());
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) {
                        throw new StageTransitionBlockedException("Stage " + stage.value()
                                + " is reserved by configuration transition " + row.getString(1)
                                + " in state " + row.getString(2));
                    }
                }
            }
        }
        String legacySql = "SELECT o.operation_id, o.config_revision_id FROM mp_stage_remap_operations o "
                + "JOIN mp_stage_remap_entries e ON e.operation_id = o.operation_id "
                + "WHERE o.status = 'MIGRATED_PENDING_CONFIG' AND e.source_stage_id = ? LIMIT 1";
        for (StageId stage : unique) {
            try (PreparedStatement statement = connection.prepareStatement(legacySql)) {
                statement.setString(1, stage.value());
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) {
                        throw new StageTransitionBlockedException("Stage " + stage.value()
                                + " is fenced by pending configuration transition " + row.getString(1)
                                + " for revision " + row.getString(2));
                    }
                }
            }
        }
    }

    private static void requireNoMalformedConfigurationTransition(Connection connection) throws SQLException {
        String malformed = "SELECT transition.config_revision_id, transition.status "
                + "FROM mp_configuration_stage_transitions transition "
                + "LEFT JOIN mp_configuration_stage_reservations reservation "
                + "ON reservation.config_revision_id = transition.config_revision_id "
                + "WHERE (transition.scope_complete = 0 "
                + "AND transition.status IN ('RESERVED','NEEDS_RECONCILIATION')) "
                + "OR (transition.status IN ('RESERVED','NEEDS_RECONCILIATION') "
                + "AND reservation.stage_id IS NULL) "
                + "OR (transition.status IN ('CONFIG_APPLIED','CONFIG_FAILED_SAFE') "
                + "AND reservation.stage_id IS NOT NULL) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(malformed);
                ResultSet row = statement.executeQuery()) {
            if (row.next()) {
                throw new StageTransitionBlockedException("Configuration transition " + row.getString(1)
                        + " has incomplete or contradictory durable reservation state " + row.getString(2));
            }
        }
        String scopeMismatch = "SELECT transition.config_revision_id, transition.status "
                + "FROM mp_configuration_stage_transitions transition "
                + "WHERE transition.status IN ('RESERVED','NEEDS_RECONCILIATION') AND ("
                + "EXISTS (SELECT 1 FROM mp_configuration_transition_stages stages "
                + "WHERE stages.config_revision_id = transition.config_revision_id "
                + "AND NOT EXISTS (SELECT 1 FROM mp_configuration_stage_reservations reservation "
                + "WHERE reservation.config_revision_id = stages.config_revision_id "
                + "AND reservation.stage_id = stages.stage_id)) OR "
                + "EXISTS (SELECT 1 FROM mp_configuration_stage_reservations reservation "
                + "WHERE reservation.config_revision_id = transition.config_revision_id "
                + "AND NOT EXISTS (SELECT 1 FROM mp_configuration_transition_stages stages "
                + "WHERE stages.config_revision_id = reservation.config_revision_id "
                + "AND stages.stage_id = reservation.stage_id))) LIMIT 1";
        rejectMalformed(connection, scopeMismatch, "has mismatched declared and active stage scope");
        String ownerMismatch = "SELECT transition.config_revision_id, transition.status "
                + "FROM mp_configuration_stage_transitions transition "
                + "LEFT JOIN mp_configuration_revisions_v2 history "
                + "ON history.revision_id = transition.config_revision_id "
                + "WHERE transition.status IN ('RESERVED','NEEDS_RECONCILIATION') "
                + "AND (history.revision_id IS NULL OR history.application_status <> 'ATTEMPTED') LIMIT 1";
        rejectMalformed(connection, ownerMismatch, "has missing or terminal configuration-history ownership");
        String ownerlessLegacy = "SELECT remap.operation_id, remap.config_revision_id "
                + "FROM mp_stage_remap_operations remap "
                + "LEFT JOIN mp_configuration_stage_transitions transition "
                + "ON transition.config_revision_id = remap.config_revision_id "
                + "WHERE remap.status = 'MIGRATED_PENDING_CONFIG' "
                + "AND transition.config_revision_id IS NULL LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(ownerlessLegacy);
                ResultSet row = statement.executeQuery()) {
            if (row.next()) {
                throw new StageTransitionBlockedException("Legacy pending remap " + row.getString(1)
                        + " has no complete configuration-transition owner for revision " + row.getString(2));
            }
        }
    }

    private static void rejectMalformed(Connection connection, String sql, String detail) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            if (row.next()) {
                throw new StageTransitionBlockedException("Configuration transition " + row.getString(1) + " "
                        + detail + " in state " + row.getString(2));
            }
        }
    }
}
