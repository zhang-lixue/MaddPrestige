package net.maddkraft.maddprestige.persistence.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionExecution;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionState;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionStatus;
import net.maddkraft.maddprestige.core.admin.config.StageReferenceMigrationStore;
import net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot;
import net.maddkraft.maddprestige.core.admin.config.StageRemapEntry;
import net.maddkraft.maddprestige.core.admin.config.StageRemapExecution;
import net.maddkraft.maddprestige.core.admin.config.StageRemapReconciliation;
import net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;
import net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException;
import net.maddkraft.maddprestige.core.stage.StageTransitionFence;
import net.maddkraft.maddprestige.core.stage.StageTransitionLease;
import net.maddkraft.maddprestige.core.stage.StageTransitionPermit;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.StalePlayerStageStateException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteStageTransitionGuard;

public final class SqliteStageReferenceMigrationStore implements StageReferenceMigrationStore, StageTransitionFence {
    private final ConnectionProvider connections;

    public SqliteStageReferenceMigrationStore(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public StageReferenceSnapshot capture(Optional<StageRemapPlan> plan) {
        Objects.requireNonNull(plan, "plan");
        String sql = "SELECT player_uuid, stage_id, state_revision, config_revision_id "
                + "FROM mp_player_stage_state ORDER BY stage_id, player_uuid";
        LinkedHashMap<StageId, Long> counts = new LinkedHashMap<>();
        ArrayList<StageRemapEntry> entries = new ArrayList<>();
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                StageId source = new StageId(rows.getString(2));
                counts.merge(source, 1L, Long::sum);
                if (plan.isPresent() && plan.orElseThrow().mappings().containsKey(source)) {
                    entries.add(new StageRemapEntry(UUID.fromString(rows.getString(1)), source,
                            plan.orElseThrow().mappings().get(source), rows.getLong(3),
                            new ConfigRevisionId(rows.getString(4))));
                }
            }
            return new StageReferenceSnapshot(counts, plan.map(value -> StageRemapSnapshot.create(value, entries)));
        } catch (SQLException exception) {
            throw new PersistenceException("Could not capture exact player stage references", exception);
        }
    }

    @Override
    public ConfigurationStageTransitionExecution beginTransition(
            ConfigRevisionId configurationRevision,
            Optional<ConfigRevisionId> priorRevision,
            ContentHash candidateHash,
            Map<StageId, ConfigurationStageReservationKind> reservedStages,
            Optional<StageRemapSnapshot> remap,
            Actor actor,
            String reason,
            Instant occurredAt) {
        Objects.requireNonNull(configurationRevision, "configuration revision");
        Objects.requireNonNull(priorRevision, "prior revision");
        Objects.requireNonNull(candidateHash, "candidate hash");
        reservedStages = Map.copyOf(Objects.requireNonNull(reservedStages, "reserved stages"));
        Objects.requireNonNull(remap, "remap");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(occurredAt, "occurred at");
        if (reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("Configuration transition reason must contain 1-512 characters");
        }
        if (reservedStages.isEmpty()) {
            throw new IllegalArgumentException("Configuration transition must reserve at least one unsafe stage");
        }
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                deleteTerminalLeases(connection);
                Optional<ConfigurationStageTransitionExecution> existing = existingTransition(
                        connection, configurationRevision);
                if (existing.isPresent()) {
                    ConfigurationStageTransitionExecution adopted = existing.orElseThrow();
                    if (!adopted.priorRevision().equals(priorRevision)
                            || !adopted.candidateHash().equals(candidateHash)
                            || !adopted.reservedStages().equals(reservedStages)) {
                        throw new StageTransitionBlockedException(
                                "Configuration revision already owns different transition authority: "
                                        + configurationRevision.value());
                    }
                    requirePreparedConfigurationOwner(
                            connection, configurationRevision, priorRevision, candidateHash);
                    SqliteStageTransitionGuard.commit(connection);
                    return adopted;
                }
                requireNoOtherActiveTransition(connection);
                requireNoTransitionLeases(connection, reservedStages.keySet());
                insertTransition(connection, configurationRevision, priorRevision, candidateHash,
                        reservedStages, occurredAt);
                revalidateUnsafeReferences(connection, reservedStages.keySet(), remap);
                Optional<StageRemapExecution> execution = migrateIfRequired(connection, configurationRevision,
                        remap, actor, reason, occurredAt);
                if (execution.isPresent()) {
                    linkRemap(connection, configurationRevision, execution.orElseThrow().operationId());
                }
                SqliteStageTransitionGuard.commit(connection);
                return new ConfigurationStageTransitionExecution(configurationRevision, priorRevision,
                        candidateHash, reservedStages, execution);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (StalePlayerStageStateException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not atomically reserve the destructive configuration transition", exception);
        }
    }

    @Override
    public StageTransitionPermit acquire(
            OperationId operationId,
            StageId sourceStage,
            StageId targetStage,
            ConfigRevisionId configurationRevision,
            Instant acquiredAt) {
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(sourceStage, "source stage");
        Objects.requireNonNull(targetStage, "target stage");
        Objects.requireNonNull(configurationRevision, "configuration revision");
        Objects.requireNonNull(acquiredAt, "acquired at");
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                deleteTerminalLeases(connection);
                Optional<StageTransitionPermit> existing = existingPermit(connection, operationId);
                if (existing.isPresent()) {
                    StageTransitionPermit permit = existing.orElseThrow();
                    if (!permit.configurationRevision().equals(configurationRevision)
                            || !permit.targetStage().equals(targetStage)) {
                        throw new StageTransitionBlockedException(
                                "Operation already owns a different durable stage-transition lease: "
                                        + operationId);
                    }
                    if (!permit.sourceStage().equals(sourceStage)) {
                        upgradeLegacyParticipation(connection, permit, sourceStage);
                        permit = new StageTransitionPermit(operationId, sourceStage, targetStage,
                                configurationRevision, permit.leaseToken());
                    }
                    SqliteStageTransitionGuard.commit(connection);
                    return permit;
                }
                requireResumableOperationOwner(connection, operationId);
                SqliteStageTransitionGuard.requireNoPendingRemap(connection, List.of(sourceStage, targetStage));
                UUID leaseToken = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mp_stage_transition_leases (operation_id, source_stage_id, target_stage_id, "
                                + "config_revision_id, lease_token, owner_type, participation_complete, acquired_at) "
                                + "VALUES (?, ?, ?, ?, ?, 'OPERATION', 1, ?)")) {
                    statement.setString(1, operationId.toString());
                    statement.setString(2, sourceStage.value());
                    statement.setString(3, targetStage.value());
                    statement.setString(4, configurationRevision.value());
                    statement.setString(5, leaseToken.toString());
                    statement.setString(6, acquiredAt.toString());
                    statement.executeUpdate();
                }
                SqliteStageTransitionGuard.commit(connection);
                return new StageTransitionPermit(operationId, sourceStage, targetStage,
                        configurationRevision, leaseToken);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (StageTransitionBlockedException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not acquire durable stage-transition fence", exception);
        }
    }

    @Override
    public void release(StageTransitionPermit permit, Instant releasedAt) {
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(releasedAt, "released at");
        releaseTerminalOwner(permit.operationId(), Optional.of(permit.leaseToken()));
    }

    @Override
    public void release(OperationId operationId, Instant releasedAt) {
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(releasedAt, "released at");
        releaseTerminalOwner(operationId, Optional.empty());
    }

    @Override
    public int releaseTerminalLeases(Instant releasedAt) {
        Objects.requireNonNull(releasedAt, "released at");
        try (Connection connection = connections.open()) {
            return deleteTerminalLeases(connection);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not clean terminal stage-transition leases", exception);
        }
    }

    @Override
    public List<StageTransitionLease> leases(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Stage-transition lease limit must be 1-1000");
        }
        String sql = "SELECT lease.operation_id, lease.source_stage_id, lease.target_stage_id, "
                + "lease.config_revision_id, operation.state, lease.participation_complete, lease.acquired_at "
                + "FROM mp_stage_transition_leases lease LEFT JOIN mp_operations operation "
                + "ON operation.operation_id = lease.operation_id ORDER BY lease.acquired_at, lease.operation_id "
                + "LIMIT ?";
        ArrayList<StageTransitionLease> result = new ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StageTransitionLease(
                            new OperationId(UUID.fromString(rows.getString(1))),
                            Optional.ofNullable(rows.getString(2)).map(StageId::new),
                            new StageId(rows.getString(3)), new ConfigRevisionId(rows.getString(4)),
                            Optional.ofNullable(rows.getString(5)).map(OperationState::valueOf),
                            rows.getInt(6) != 0, Instant.parse(rows.getString(7))));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not inspect durable stage-transition leases", exception);
        }
    }

    @Override
    public void markTransitionApplied(ConfigRevisionId configurationRevision, Instant occurredAt) {
        finishTransition(configurationRevision, ConfigurationStageTransitionStatus.CONFIG_APPLIED,
                "Configuration revision and runtime activated with coherent player-stage references.", occurredAt);
    }

    @Override
    public void markTransitionFailedSafe(
            ConfigRevisionId configurationRevision,
            String detail,
            Instant occurredAt) {
        finishTransition(configurationRevision, ConfigurationStageTransitionStatus.CONFIG_FAILED_SAFE,
                detail, occurredAt);
    }

    @Override
    public void markTransitionNeedsReconciliation(
            ConfigRevisionId configurationRevision,
            String detail,
            Instant occurredAt) {
        Objects.requireNonNull(configurationRevision, "configuration revision");
        Objects.requireNonNull(occurredAt, "occurred at");
        requireDetail(detail);
        String sql = "UPDATE mp_configuration_stage_transitions SET status = 'NEEDS_RECONCILIATION', "
                + "updated_at = ?, detail = ? WHERE config_revision_id = ? "
                + "AND status IN ('RESERVED','NEEDS_RECONCILIATION')";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, occurredAt.toString());
            statement.setString(2, boundedDetail(detail));
            statement.setString(3, configurationRevision.value());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Configuration transition is absent or terminal: "
                        + configurationRevision.value());
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not retain configuration-transition recovery authority", exception);
        }
    }

    @Override
    public List<ConfigurationStageTransitionState> transitions(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Configuration transition limit must be 1-1000");
        }
        String sql = "SELECT transition.config_revision_id, transition.prior_revision_id, "
                + "transition.candidate_hash, transition.status, CASE WHEN transition.scope_complete = 1 "
                + "AND EXISTS (SELECT 1 FROM mp_configuration_transition_stages declared "
                + "WHERE declared.config_revision_id = transition.config_revision_id) "
                + "AND NOT EXISTS (SELECT 1 FROM mp_configuration_transition_stages declared "
                + "WHERE declared.config_revision_id = transition.config_revision_id "
                + "AND NOT EXISTS (SELECT 1 FROM mp_configuration_stage_reservations active "
                + "WHERE active.config_revision_id = declared.config_revision_id "
                + "AND active.stage_id = declared.stage_id)) "
                + "AND NOT EXISTS (SELECT 1 FROM mp_configuration_stage_reservations active "
                + "WHERE active.config_revision_id = transition.config_revision_id "
                + "AND NOT EXISTS (SELECT 1 FROM mp_configuration_transition_stages declared "
                + "WHERE declared.config_revision_id = active.config_revision_id "
                + "AND declared.stage_id = active.stage_id)) THEN 1 ELSE 0 END, "
                + "history.application_status, transition.updated_at, transition.detail "
                + "FROM mp_configuration_stage_transitions transition "
                + "LEFT JOIN mp_configuration_revisions_v2 history "
                + "ON history.revision_id = transition.config_revision_id "
                + "WHERE transition.status IN ('RESERVED','NEEDS_RECONCILIATION') "
                + "OR EXISTS (SELECT 1 FROM mp_configuration_stage_reservations reservation "
                + "WHERE reservation.config_revision_id = transition.config_revision_id) "
                + "ORDER BY transition.updated_at, transition.config_revision_id LIMIT ?";
        ArrayList<ConfigurationStageTransitionState> result = new ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ConfigRevisionId revision = new ConfigRevisionId(rows.getString(1));
                    result.add(new ConfigurationStageTransitionState(revision,
                            Optional.ofNullable(rows.getString(2)).map(ConfigRevisionId::new),
                            new ContentHash(rows.getString(3)),
                            ConfigurationStageTransitionStatus.valueOf(rows.getString(4)),
                            rows.getInt(5) != 0,
                            transitionStages(connection, revision),
                            Optional.ofNullable(rows.getString(6)).map(ConfigurationApplicationStatus::valueOf),
                            Instant.parse(rows.getString(7)), rows.getString(8)));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not inspect configuration stage transitions", exception);
        }
    }

    @Override
    public List<StageRemapReconciliation> unresolved(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Stage remap reconciliation limit must be 1-1000");
        }
        String sql = "SELECT operation_id, config_revision_id, status, migrated_players, updated_at, detail "
                + "FROM mp_stage_remap_operations WHERE status <> 'CONFIG_APPLIED' "
                + "ORDER BY updated_at, operation_id LIMIT ?";
        ArrayList<StageRemapReconciliation> result = new ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new StageRemapReconciliation(UUID.fromString(rows.getString(1)),
                            new ConfigRevisionId(rows.getString(2)), rows.getString(3), rows.getInt(4),
                            Instant.parse(rows.getString(5)), rows.getString(6)));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not inspect stage remap reconciliation state", exception);
        }
    }

    private static Optional<ConfigurationStageTransitionExecution> existingTransition(
            Connection connection,
            ConfigRevisionId configurationRevision) throws SQLException {
        String sql = "SELECT prior_revision_id, candidate_hash, status, scope_complete, remap_operation_id "
                + "FROM mp_configuration_stage_transitions WHERE config_revision_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, configurationRevision.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                ConfigurationStageTransitionStatus status = ConfigurationStageTransitionStatus.valueOf(
                        row.getString(3));
                if (status != ConfigurationStageTransitionStatus.RESERVED || row.getInt(4) == 0) {
                    throw new StageTransitionBlockedException("Configuration transition cannot be adopted in state "
                            + status + " with complete-scope=" + (row.getInt(4) != 0) + ": "
                            + configurationRevision.value());
                }
                Optional<StageRemapExecution> remap = Optional.empty();
                String operationId = row.getString(5);
                if (operationId != null) {
                    remap = Optional.of(remapExecution(connection, UUID.fromString(operationId)));
                }
                return Optional.of(new ConfigurationStageTransitionExecution(configurationRevision,
                        Optional.ofNullable(row.getString(1)).map(ConfigRevisionId::new),
                        new ContentHash(row.getString(2)), transitionStages(connection, configurationRevision),
                        remap));
            }
        }
    }

    private static void requireNoOtherActiveTransition(Connection connection) throws SQLException {
        String sql = "SELECT config_revision_id, status FROM mp_configuration_stage_transitions "
                + "WHERE status IN ('RESERVED','NEEDS_RECONCILIATION') LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            if (row.next()) {
                throw new StageTransitionBlockedException("Configuration transition " + row.getString(1)
                        + " already owns destructive-stage authority in state " + row.getString(2));
            }
        }
        String legacy = "SELECT remap.operation_id, remap.config_revision_id "
                + "FROM mp_stage_remap_operations remap LEFT JOIN mp_configuration_stage_transitions transition "
                + "ON transition.config_revision_id = remap.config_revision_id "
                + "WHERE remap.status = 'MIGRATED_PENDING_CONFIG' "
                + "AND transition.config_revision_id IS NULL LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(legacy); ResultSet row = statement.executeQuery()) {
            if (row.next()) {
                throw new StageTransitionBlockedException("Legacy pending remap " + row.getString(1)
                        + " must be reconciled before another configuration transition can begin");
            }
        }
    }

    private static void insertTransition(
            Connection connection,
            ConfigRevisionId configurationRevision,
            Optional<ConfigRevisionId> priorRevision,
            ContentHash candidateHash,
            Map<StageId, ConfigurationStageReservationKind> reservedStages,
            Instant occurredAt) throws SQLException {
        requirePreparedConfigurationOwner(connection, configurationRevision, priorRevision, candidateHash);
        String transitionSql = "INSERT INTO mp_configuration_stage_transitions (config_revision_id, "
                + "prior_revision_id, candidate_hash, remap_operation_id, status, scope_complete, created_at, "
                + "updated_at, detail) VALUES (?, ?, ?, NULL, 'RESERVED', 1, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(transitionSql)) {
            statement.setString(1, configurationRevision.value());
            setOptionalRevision(statement, 2, priorRevision);
            statement.setString(3, candidateHash.value());
            statement.setString(4, occurredAt.toString());
            statement.setString(5, occurredAt.toString());
            statement.setString(6, "Every removed or disabled stage is reserved; configuration activation pending.");
            statement.executeUpdate();
        }
        String stageSql = "INSERT INTO mp_configuration_transition_stages "
                + "(config_revision_id, stage_id, reservation_kind) VALUES (?, ?, ?)";
        String reservationSql = "INSERT INTO mp_configuration_stage_reservations "
                + "(stage_id, config_revision_id, reserved_at) VALUES (?, ?, ?)";
        for (Map.Entry<StageId, ConfigurationStageReservationKind> entry : reservedStages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.value().compareTo(right.value()))).toList()) {
            try (PreparedStatement statement = connection.prepareStatement(stageSql)) {
                statement.setString(1, configurationRevision.value());
                statement.setString(2, entry.getKey().value());
                statement.setString(3, entry.getValue().name());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(reservationSql)) {
                statement.setString(1, entry.getKey().value());
                statement.setString(2, configurationRevision.value());
                statement.setString(3, occurredAt.toString());
                statement.executeUpdate();
            }
        }
    }

    private static void requirePreparedConfigurationOwner(
            Connection connection,
            ConfigRevisionId configurationRevision,
            Optional<ConfigRevisionId> priorRevision,
            ContentHash candidateHash) throws SQLException {
        String sql = "SELECT parent_revision_id, canonical_content_hash, application_status "
                + "FROM mp_configuration_revisions_v2 WHERE revision_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, configurationRevision.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()
                        || !priorRevision.map(ConfigRevisionId::value).equals(Optional.ofNullable(row.getString(1)))
                        || !candidateHash.value().equals(row.getString(2))
                        || !ConfigurationApplicationStatus.ATTEMPTED.name().equals(row.getString(3))) {
                    throw new StageTransitionBlockedException(
                            "Configuration transition requires exact ATTEMPTED history ownership: "
                                    + configurationRevision.value());
                }
            }
        }
    }

    private static void revalidateUnsafeReferences(
            Connection connection,
            Set<StageId> unsafeStages,
            Optional<StageRemapSnapshot> remap) throws SQLException {
        Map<StageId, Long> expected = remap.map(StageRemapSnapshot::countsBySource).orElse(Map.of());
        if (remap.isPresent()
                && (!unsafeStages.containsAll(remap.orElseThrow().plan().mappings().keySet())
                        || !remap.orElseThrow().plan().mappings().keySet().equals(expected.keySet()))) {
            throw new StalePlayerStageStateException(
                    "Remap authority does not exactly cover the currently referenced unsafe-stage subset");
        }
        for (StageId stage : unsafeStages) {
            long current = countStageReferences(connection, stage);
            Long sealed = expected.get(stage);
            if ((current > 0 && sealed == null) || (sealed != null && current != sealed.longValue())) {
                throw new StalePlayerStageStateException(
                        "Persisted references changed for unsafe stage " + stage.value());
            }
        }
        if (remap.isPresent()) {
            revalidateExactSnapshot(connection, remap.orElseThrow());
        }
    }

    private static long countStageReferences(Connection connection, StageId stage) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM mp_player_stage_state WHERE stage_id = ?")) {
            statement.setString(1, stage.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        }
    }

    private static Optional<StageRemapExecution> migrateIfRequired(
            Connection connection,
            ConfigRevisionId configurationRevision,
            Optional<StageRemapSnapshot> remap,
            Actor actor,
            String reason,
            Instant occurredAt) throws SQLException {
        if (remap.isEmpty()) {
            return Optional.empty();
        }
        StageRemapSnapshot snapshot = remap.orElseThrow();
        if (snapshot.entries().isEmpty()) {
            throw new StalePlayerStageStateException("Sealed remap has no referenced player rows");
        }
        UUID operationId = UUID.randomUUID();
        insertOperation(connection, operationId, configurationRevision, snapshot, actor, reason, occurredAt);
        for (StageRemapEntry entry : snapshot.entries()) {
            migrateEntry(connection, operationId, configurationRevision, entry, actor, reason, occurredAt);
        }
        return Optional.of(new StageRemapExecution(operationId, snapshot.entries().size()));
    }

    private static void linkRemap(
            Connection connection,
            ConfigRevisionId configurationRevision,
            UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE mp_configuration_stage_transitions SET remap_operation_id = ? "
                        + "WHERE config_revision_id = ? AND remap_operation_id IS NULL AND status = 'RESERVED'")) {
            statement.setString(1, operationId.toString());
            statement.setString(2, configurationRevision.value());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Could not bind remap journal to configuration transition");
            }
        }
    }

    private static StageRemapExecution remapExecution(Connection connection, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT migrated_players FROM mp_stage_remap_operations WHERE operation_id = ?")) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new StageTransitionBlockedException("Configuration transition references missing remap "
                            + operationId);
                }
                return new StageRemapExecution(operationId, row.getInt(1));
            }
        }
    }

    private static Map<StageId, ConfigurationStageReservationKind> transitionStages(
            Connection connection,
            ConfigRevisionId configurationRevision) throws SQLException {
        LinkedHashMap<StageId, ConfigurationStageReservationKind> stages = new LinkedHashMap<>();
        String sql = "SELECT stage_id, reservation_kind FROM mp_configuration_transition_stages "
                + "WHERE config_revision_id = ? ORDER BY stage_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, configurationRevision.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    stages.put(new StageId(rows.getString(1)),
                            ConfigurationStageReservationKind.valueOf(rows.getString(2)));
                }
            }
        }
        return Map.copyOf(stages);
    }

    private void finishTransition(
            ConfigRevisionId configurationRevision,
            ConfigurationStageTransitionStatus replacement,
            String detail,
            Instant occurredAt) {
        Objects.requireNonNull(configurationRevision, "configuration revision");
        Objects.requireNonNull(replacement, "replacement status");
        Objects.requireNonNull(occurredAt, "occurred at");
        requireDetail(detail);
        if (replacement.active()) {
            throw new IllegalArgumentException("Terminal configuration transition status is required");
        }
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                ConfigurationStageTransitionStatus current = transitionStatus(connection, configurationRevision);
                requireTerminalConfigurationOwner(connection, configurationRevision, replacement);
                if (current != replacement && !current.active()) {
                    throw new PersistenceException("Configuration transition is already terminal as " + current);
                }
                if (current.active()) {
                    String update = "UPDATE mp_configuration_stage_transitions SET status = ?, updated_at = ?, "
                            + "detail = ? WHERE config_revision_id = ? "
                            + "AND status IN ('RESERVED','NEEDS_RECONCILIATION')";
                    try (PreparedStatement statement = connection.prepareStatement(update)) {
                        statement.setString(1, replacement.name());
                        statement.setString(2, occurredAt.toString());
                        statement.setString(3, boundedDetail(detail));
                        statement.setString(4, configurationRevision.value());
                        if (statement.executeUpdate() != 1) {
                            throw new PersistenceException("Configuration transition status changed concurrently");
                        }
                    }
                }
                updateLinkedRemap(connection, configurationRevision, replacement, detail, occurredAt);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM mp_configuration_stage_reservations WHERE config_revision_id = ?")) {
                    statement.setString(1, configurationRevision.value());
                    statement.executeUpdate();
                }
                SqliteStageTransitionGuard.commit(connection);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not finish configuration stage transition", exception);
        }
    }

    private static ConfigurationStageTransitionStatus transitionStatus(
            Connection connection,
            ConfigRevisionId configurationRevision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM mp_configuration_stage_transitions WHERE config_revision_id = ?")) {
            statement.setString(1, configurationRevision.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new PersistenceException("Unknown configuration transition: "
                            + configurationRevision.value());
                }
                return ConfigurationStageTransitionStatus.valueOf(row.getString(1));
            }
        }
    }

    private static void requireTerminalConfigurationOwner(
            Connection connection,
            ConfigRevisionId configurationRevision,
            ConfigurationStageTransitionStatus replacement) throws SQLException {
        ConfigurationApplicationStatus required = replacement == ConfigurationStageTransitionStatus.CONFIG_APPLIED
                ? ConfigurationApplicationStatus.APPLIED : ConfigurationApplicationStatus.FAILED;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT application_status FROM mp_configuration_revisions_v2 WHERE revision_id = ?")) {
            statement.setString(1, configurationRevision.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !required.name().equals(row.getString(1))) {
                    throw new StageTransitionBlockedException("Configuration transition release requires durable "
                            + required + " history evidence: " + configurationRevision.value());
                }
            }
        }
    }

    private static void updateLinkedRemap(
            Connection connection,
            ConfigRevisionId configurationRevision,
            ConfigurationStageTransitionStatus replacement,
            String detail,
            Instant occurredAt) throws SQLException {
        String remapStatus = replacement == ConfigurationStageTransitionStatus.CONFIG_APPLIED
                ? "CONFIG_APPLIED" : "CONFIG_FAILED_SAFE";
        String sql = "UPDATE mp_stage_remap_operations SET status = ?, updated_at = ?, detail = ? "
                + "WHERE operation_id = (SELECT remap_operation_id FROM mp_configuration_stage_transitions "
                + "WHERE config_revision_id = ?) AND status = 'MIGRATED_PENDING_CONFIG'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, remapStatus);
            statement.setString(2, occurredAt.toString());
            statement.setString(3, boundedDetail(detail));
            statement.setString(4, configurationRevision.value());
            statement.executeUpdate();
        }
    }

    private static void setOptionalRevision(
            PreparedStatement statement,
            int index,
            Optional<ConfigRevisionId> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow().value());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void requireDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("Configuration transition detail cannot be blank");
        }
    }

    private static String boundedDetail(String detail) {
        return detail.length() > 2048 ? detail.substring(0, 2048) : detail;
    }

    private static void revalidateExactSnapshot(Connection connection, StageRemapSnapshot snapshot)
            throws SQLException {
        for (Map.Entry<StageId, Long> expected : snapshot.countsBySource().entrySet()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM mp_player_stage_state WHERE stage_id = ?")) {
                statement.setString(1, expected.getKey().value());
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next() || row.getLong(1) != expected.getValue()) {
                        throw new StalePlayerStageStateException(
                                "Persisted references changed for stage " + expected.getKey().value());
                    }
                }
            }
        }
        String sql = "SELECT stage_id, state_revision, config_revision_id FROM mp_player_stage_state "
                + "WHERE player_uuid = ?";
        for (StageRemapEntry entry : snapshot.entries()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, entry.playerId().toString());
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next() || !row.getString(1).equals(entry.sourceStage().value())
                            || row.getLong(2) != entry.expectedStateRevision()
                            || !row.getString(3).equals(entry.sourceConfigRevision().value())) {
                        throw new StalePlayerStageStateException(
                                "Player stage changed after remap preview: " + entry.playerId());
                    }
                }
            }
        }
    }

    private static Optional<StageTransitionPermit> existingPermit(
            Connection connection,
            OperationId operationId) throws SQLException {
        String sql = "SELECT source_stage_id, target_stage_id, config_revision_id, lease_token, "
                + "participation_complete FROM mp_stage_transition_leases "
                + "WHERE operation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                StageId target = new StageId(row.getString(2));
                StageId source = row.getInt(5) == 0 ? target : new StageId(row.getString(1));
                return Optional.of(new StageTransitionPermit(operationId, source, target,
                        new ConfigRevisionId(row.getString(3)), UUID.fromString(row.getString(4))));
            }
        }
    }

    private static void requireNoTransitionLeases(Connection connection, Set<StageId> removedStages)
            throws SQLException {
        String sql = "SELECT operation_id, source_stage_id, target_stage_id, participation_complete "
                + "FROM mp_stage_transition_leases WHERE participation_complete = 0 "
                + "OR source_stage_id = ? OR target_stage_id = ? LIMIT 1";
        for (StageId removedStage : removedStages) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, removedStage.value());
                statement.setString(2, removedStage.value());
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) {
                        throw new StageTransitionBlockedException("Configuration stage remap cannot begin while "
                                + "operation " + row.getString(1)
                                + (row.getInt(4) == 0 ? " owns legacy participation with unknown source stage"
                                        : " participates as source " + row.getString(2)
                                                + " and target " + row.getString(3)));
                    }
                }
            }
        }
    }

    private static void requireResumableOperationOwner(Connection connection, OperationId operationId)
            throws SQLException {
        String sql = "SELECT state FROM mp_operations WHERE operation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new StageTransitionBlockedException(
                            "Durable stage-transition lease requires an existing operation journal owner: "
                                    + operationId);
                }
                if (terminal(row.getString(1))) {
                    throw new StageTransitionBlockedException(
                            "Terminal operation cannot acquire stage-transition authority: " + operationId);
                }
            }
        }
    }

    private static void upgradeLegacyParticipation(
            Connection connection,
            StageTransitionPermit permit,
            StageId sourceStage) throws SQLException {
        String sql = "UPDATE mp_stage_transition_leases SET source_stage_id = ?, participation_complete = 1 "
                + "WHERE operation_id = ? AND lease_token = ? AND participation_complete = 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceStage.value());
            statement.setString(2, permit.operationId().toString());
            statement.setString(3, permit.leaseToken().toString());
            if (statement.executeUpdate() != 1) {
                throw new StageTransitionBlockedException(
                        "Operation already owns different complete stage participation: " + permit.operationId());
            }
        }
    }

    private void releaseTerminalOwner(OperationId operationId, Optional<UUID> leaseToken) {
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                String sql = "DELETE FROM mp_stage_transition_leases WHERE operation_id = ? "
                        + (leaseToken.isPresent() ? "AND lease_token = ? " : "")
                        + "AND EXISTS (SELECT 1 FROM mp_operations operation "
                        + "WHERE operation.operation_id = mp_stage_transition_leases.operation_id "
                        + "AND operation.state IN ('COMPLETED','COMPENSATED','FAILED'))";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, operationId.toString());
                    if (leaseToken.isPresent()) {
                        statement.setString(2, leaseToken.orElseThrow().toString());
                    }
                    statement.executeUpdate();
                }
                SqliteStageTransitionGuard.commit(connection);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not release terminal stage-transition lease", exception);
        }
    }

    private static int deleteTerminalLeases(Connection connection) throws SQLException {
        String sql = "DELETE FROM mp_stage_transition_leases WHERE NOT EXISTS ("
                + "SELECT 1 FROM mp_operations operation WHERE operation.operation_id = "
                + "mp_stage_transition_leases.operation_id) OR EXISTS (SELECT 1 FROM mp_operations operation "
                + "WHERE operation.operation_id = mp_stage_transition_leases.operation_id "
                + "AND operation.state IN ('COMPLETED','COMPENSATED','FAILED'))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        }
    }

    private static boolean terminal(String state) {
        return "COMPLETED".equals(state) || "COMPENSATED".equals(state) || "FAILED".equals(state);
    }

    private static void insertOperation(
            Connection connection,
            UUID operationId,
            ConfigRevisionId configurationRevision,
            StageRemapSnapshot snapshot,
            Actor actor,
            String reason,
            Instant occurredAt) throws SQLException {
        String sql = "INSERT INTO mp_stage_remap_operations (operation_id, config_revision_id, plan_revision, "
                + "plan_hash, actor_type, actor_uuid, actor_name, reason, status, migrated_players, created_at, "
                + "updated_at, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MIGRATED_PENDING_CONFIG', ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, configurationRevision.value());
            statement.setString(3, snapshot.plan().planRevision());
            statement.setString(4, snapshot.seal().value());
            statement.setString(5, actor.type());
            setOptionalUuid(statement, 6, actor.uuid());
            statement.setString(7, actor.displayName());
            statement.setString(8, reason);
            statement.setInt(9, snapshot.entries().size());
            statement.setString(10, occurredAt.toString());
            statement.setString(11, occurredAt.toString());
            statement.setString(12, "Player rows migrated; configuration activation is pending.");
            statement.executeUpdate();
        }
    }

    private static void migrateEntry(
            Connection connection,
            UUID operationId,
            ConfigRevisionId configurationRevision,
            StageRemapEntry entry,
            Actor actor,
            String reason,
            Instant occurredAt) throws SQLException {
        long resultingRevision = Math.addExact(entry.expectedStateRevision(), 1);
        String update = "UPDATE mp_player_stage_state SET stage_id = ?, state_revision = ?, "
                + "config_revision_id = ?, stage_entered_at = ?, updated_at = ? WHERE player_uuid = ? "
                + "AND stage_id = ? AND state_revision = ? AND config_revision_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setString(1, entry.targetStage().value());
            statement.setLong(2, resultingRevision);
            statement.setString(3, configurationRevision.value());
            statement.setString(4, occurredAt.toString());
            statement.setString(5, occurredAt.toString());
            statement.setString(6, entry.playerId().toString());
            statement.setString(7, entry.sourceStage().value());
            statement.setLong(8, entry.expectedStateRevision());
            statement.setString(9, entry.sourceConfigRevision().value());
            if (statement.executeUpdate() != 1) {
                throw new StalePlayerStageStateException(
                        "Player stage compare-and-set failed during remap: " + entry.playerId());
            }
        }
        String remapEntry = "INSERT INTO mp_stage_remap_entries (operation_id, player_uuid, source_stage_id, "
                + "target_stage_id, expected_state_revision, resulting_state_revision, "
                + "source_config_revision_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(remapEntry)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, entry.playerId().toString());
            statement.setString(3, entry.sourceStage().value());
            statement.setString(4, entry.targetStage().value());
            statement.setLong(5, entry.expectedStateRevision());
            statement.setLong(6, resultingRevision);
            statement.setString(7, entry.sourceConfigRevision().value());
            statement.executeUpdate();
        }
        String history = "INSERT INTO mp_stage_history (history_id, player_uuid, stage_id, entered_at, "
                + "operation_id, actor_type, actor_uuid, actor_name, reason, config_revision_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(history)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, entry.playerId().toString());
            statement.setString(3, entry.targetStage().value());
            statement.setString(4, occurredAt.toString());
            statement.setNull(5, Types.VARCHAR);
            statement.setString(6, actor.type());
            setOptionalUuid(statement, 7, actor.uuid());
            statement.setString(8, actor.displayName());
            statement.setString(9, "configuration-stage-remap[" + operationId + "]: " + reason);
            statement.setString(10, configurationRevision.value());
            statement.executeUpdate();
        }
    }

    private static void setOptionalUuid(
            PreparedStatement statement,
            int index,
            Optional<UUID> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow().toString());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }
}
