package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.OperationActionPlan;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.core.currency.CurrencyMutationKind;
import net.maddkraft.maddprestige.core.prestige.CurrencyConsequence;
import net.maddkraft.maddprestige.core.prestige.MilestoneConsequence;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.PrestigeHistoryRecord;
import net.maddkraft.maddprestige.persistence.PrestigeLifecycleRepository;
import net.maddkraft.maddprestige.persistence.StoredPrestigeOperation;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqlitePrestigeLifecycleRepository implements PrestigeLifecycleRepository {
    private static final String INSERT_DETAIL = "INSERT INTO mp_prestige_operation_details (operation_id, "
            + "source_stage_id, reset_stage_id, expected_prestige_revision, current_before, current_after, "
            + "lifetime_before, lifetime_after, scope_before, scope_after, stage_config_provenance, "
            + "prestige_config_provenance, confirmation_snapshot, planned_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private final ConnectionProvider connections;

    public SqlitePrestigeLifecycleRepository(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public void insertPrepared(PrestigePlan plan) {
        requireAuthorized(plan);
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                insertOperation(connection, plan.operationPlan());
                for (int index = 0; index < plan.operationPlan().actions().size(); index++) {
                    insertAction(connection, plan.operationId(), index, plan.operationPlan().actions().get(index));
                }
                insertDetail(connection, plan);
                for (PlannedCost cost : plan.costs()) {
                    insertRecoveryCost(connection, cost);
                }
                for (PlannedReward reward : plan.rewards()) {
                    insertRecoveryReward(connection, reward);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not atomically persist prepared Prestige operation", exception);
        }
    }

    @Override
    public void commitInternal(PrestigePlan plan, Instant now) {
        requireAuthorized(plan);
        try (Connection connection = connections.open()) {
            SqliteStageTransitionGuard.beginImmediate(connection);
            try {
                requireExecuting(connection, plan.operationId());
                SqliteStageTransitionGuard.requireNoPendingRemap(connection,
                        java.util.List.of(plan.simulation().sourceStage(), plan.simulation().resetStage()));
                updateStage(connection, plan, now);
                updatePrestige(connection, plan, now);
                insertPrestigeBaselines(connection, plan, now);
                for (CurrencyConsequence currency : plan.simulation().currencyChanges()) {
                    resetCurrency(connection, plan, currency, now);
                }
                for (MilestoneConsequence milestone : plan.simulation().milestoneConsequences()) {
                    insertMilestone(connection, plan, milestone, now);
                }
                insertStageHistory(connection, plan, now);
                insertPrestigeHistory(connection, plan, now);
                SqliteStageTransitionGuard.commit(connection);
            } catch (SQLException | RuntimeException exception) {
                SqliteStageTransitionGuard.rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not atomically commit authoritative Prestige state", exception);
        }
    }

    private static void requireAuthorized(PrestigePlan plan) {
        if (plan == null || !plan.executionAllowed() || !plan.blockers().isEmpty()
                || !plan.authorization().matches(plan)) {
            throw new SecurityException("Prestige persistence requires the exact canonical authorization seal");
        }
    }

    @Override
    public boolean internalCommitObserved(OperationId operationId) {
        String sql = "SELECT 1 FROM mp_prestige_history WHERE operation_id = ? AND event_type = 'STATE_COMMITTED'";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not reconcile Prestige commit evidence", exception);
        }
    }

    @Override
    public void recordResult(OperationId operationId, String result) {
        if (result == null || result.isBlank() || result.length() > 64) {
            throw new IllegalArgumentException("Prestige history result must be 1-64 characters");
        }
        String sql = "UPDATE mp_prestige_history SET result = ? "
                + "WHERE operation_id = ? AND event_type = 'STATE_COMMITTED'";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, result);
            statement.setString(2, operationId.toString());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Committed Prestige history is absent for result update");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not update Prestige history result", exception);
        }
    }

    @Override
    public Optional<StoredPrestigeOperation> findPrepared(OperationId operationId) {
        String sql = "SELECT source_stage_id, reset_stage_id, expected_prestige_revision, current_before, "
                + "current_after, lifetime_before, lifetime_after, scope_before, scope_after, "
                + "stage_config_provenance, prestige_config_provenance, planned_at "
                + "FROM mp_prestige_operation_details WHERE operation_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredPrestigeOperation(operationId, new StageId(row.getString(1)),
                        new StageId(row.getString(2)), row.getLong(3), row.getLong(4), row.getLong(5),
                        row.getLong(6), row.getLong(7), new ScopeId(row.getString(8)), new ScopeId(row.getString(9)),
                        new ConfigRevisionId(row.getString(10)), new ConfigRevisionId(row.getString(11)),
                        Instant.parse(row.getString(12))));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load prepared Prestige operation", exception);
        }
    }

    @Override
    public Optional<PlannedReward> findRecoveryReward(OperationId operationId, String actionId) {
        String sql = "SELECT player_uuid, reward_id, provider_id, reward_type, value_type, value_text, "
                + "metadata_text, display_name, failure_policy, repeatability, config_revision_id, "
                + "provider_generation, idempotent, reversible, reconcilable, external_uncertainty, "
                + "redacted_preview FROM mp_prestige_recovery_rewards WHERE operation_id = ? AND action_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, actionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                UUID playerId = UUID.fromString(row.getString(1));
                ProviderId providerId = new ProviderId(row.getString(3));
                RewardDefinition definition = new RewardDefinition(new RewardId(row.getString(2)), providerId,
                        row.getString(4), MetricValue.parse(MetricValueType.valueOf(row.getString(5)),
                                row.getString(6)),
                        decodeMetadata(row.getString(7)), row.getString(8),
                        RewardFailurePolicy.valueOf(row.getString(9)),
                        RewardRepeatability.valueOf(row.getString(10)));
                ActionCharacteristics characteristics = new ActionCharacteristics(row.getInt(13) != 0,
                        row.getInt(14) != 0, row.getInt(15) != 0, row.getInt(16) != 0);
                return Optional.of(new PlannedReward(operationId, actionId, playerId, definition,
                        new ConfigRevisionId(row.getString(11)), row.getLong(12), characteristics,
                        row.getString(17)));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not reconstruct sealed reward recovery action", exception);
        }
    }

    @Override
    public Optional<PlannedCost> findRecoveryCost(OperationId operationId, String actionId) {
        String sql = "SELECT player_uuid, cost_id, provider_id, cost_type, value_type, value_text, metadata_text, "
                + "display_name, config_revision_id, provider_generation, idempotent, reversible, reconcilable, "
                + "external_uncertainty, redacted_preview FROM mp_prestige_recovery_costs "
                + "WHERE operation_id = ? AND action_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, actionId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                UUID playerId = UUID.fromString(row.getString(1));
                ProviderId providerId = new ProviderId(row.getString(3));
                CostDefinition definition = new CostDefinition(new CostId(row.getString(2)), providerId,
                        row.getString(4), MetricValue.parse(MetricValueType.valueOf(row.getString(5)),
                                row.getString(6)),
                        decodeMetadata(row.getString(7)), row.getString(8));
                ActionCharacteristics characteristics = new ActionCharacteristics(row.getInt(11) != 0,
                        row.getInt(12) != 0, row.getInt(13) != 0, row.getInt(14) != 0);
                return Optional.of(new PlannedCost(operationId, actionId, playerId, definition,
                        new ConfigRevisionId(row.getString(9)), row.getLong(10), characteristics,
                        row.getString(15)));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not reconstruct sealed cost recovery action", exception);
        }
    }

    @Override
    public List<PrestigeHistoryRecord> history(UUID playerId, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Prestige history limit must be 1-1000");
        }
        String sql = "SELECT operation_id, source_stage_id, reset_stage_id, current_before, current_after, "
                + "lifetime_before, lifetime_after, result, costs_snapshot, rewards_snapshot, config_revision_id, "
                + "occurred_at FROM mp_prestige_history WHERE player_uuid = ? "
                + "ORDER BY occurred_at DESC, history_id LIMIT ?";
        ArrayList<PrestigeHistoryRecord> result = new ArrayList<>();
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new PrestigeHistoryRecord(playerId,
                            new OperationId(UUID.fromString(rows.getString(1))), new StageId(rows.getString(2)),
                            new StageId(rows.getString(3)), rows.getLong(4), rows.getLong(5), rows.getLong(6),
                            rows.getLong(7), rows.getString(8), rows.getString(9), rows.getString(10),
                            new ConfigRevisionId(rows.getString(11)), Instant.parse(rows.getString(12))));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load Prestige history", exception);
        }
    }

    @Override
    public boolean awarded(UUID playerId, MilestoneId milestoneId, String repeatabilityKey) {
        String sql = "SELECT 1 FROM mp_milestone_awards WHERE player_uuid = ? AND milestone_id = ? "
                + "AND repeatability_key = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, milestoneId.value());
            statement.setString(3, repeatabilityKey);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load milestone award state", exception);
        }
    }

    private static void insertOperation(Connection connection, OperationPlan plan) throws SQLException {
        String sql = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, state, "
                + "expected_state_revision, config_revision_id, provider_generations, redacted_preview, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                    .map(entry -> entry.getKey().value() + "=" + entry.getValue())
                    .reduce((left, right) -> left + "," + right).orElse("");
            statement.setString(8, generations);
            statement.setString(9, plan.redactedPreview());
            statement.setString(10, now.toString());
            statement.setString(11, now.toString());
            statement.executeUpdate();
        }
    }

    private static void insertAction(
            Connection connection,
            OperationId operationId,
            int index,
            OperationActionPlan action) throws SQLException {
        String sql = "INSERT INTO mp_operation_actions (operation_id, action_index, action_id, provider_id, "
                + "action_type, state, redacted_description, reversible, idempotent, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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

    private static void insertDetail(Connection connection, PrestigePlan plan) throws SQLException {
        var simulation = plan.simulation();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DETAIL)) {
            statement.setString(1, plan.operationId().toString());
            statement.setString(2, simulation.sourceStage().value());
            statement.setString(3, simulation.resetStage().value());
            statement.setLong(4, plan.expectedPrestigeRevision());
            statement.setLong(5, simulation.currentPrestigeBefore());
            statement.setLong(6, simulation.currentPrestigeAfter());
            statement.setLong(7, simulation.lifetimePrestigeBefore());
            statement.setLong(8, simulation.lifetimePrestigeAfter());
            statement.setString(9, simulation.prestigeScopeBefore().value());
            statement.setString(10, simulation.prestigeScopeAfter().value());
            statement.setString(11, simulation.playerStageProvenance().value());
            statement.setString(12, simulation.playerPrestigeProvenance().value());
            statement.setString(13, confirmationSnapshot(plan));
            statement.setString(14, simulation.plannedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void insertRecoveryReward(Connection connection, PlannedReward reward) throws SQLException {
        String sql = "INSERT INTO mp_prestige_recovery_rewards (operation_id, action_id, player_uuid, reward_id, "
                + "provider_id, reward_type, value_type, value_text, metadata_text, display_name, failure_policy, "
                + "repeatability, config_revision_id, provider_generation, idempotent, reversible, reconcilable, "
                + "external_uncertainty, redacted_preview) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reward.operationId().toString());
            statement.setString(2, reward.actionId());
            statement.setString(3, reward.playerId().toString());
            statement.setString(4, reward.definition().id().value());
            statement.setString(5, reward.definition().providerId().value());
            statement.setString(6, reward.definition().type());
            statement.setString(7, reward.definition().value().type().name());
            statement.setString(8, reward.definition().value().canonical());
            statement.setString(9, encodeMetadata(reward.definition().metadata()));
            statement.setString(10, reward.definition().displayName());
            statement.setString(11, reward.definition().failurePolicy().name());
            statement.setString(12, reward.definition().repeatability().name());
            statement.setString(13, reward.configRevision().value());
            statement.setLong(14, reward.providerGeneration());
            statement.setInt(15, reward.characteristics().idempotent() ? 1 : 0);
            statement.setInt(16, reward.characteristics().reversible() ? 1 : 0);
            statement.setInt(17, reward.characteristics().reconcilable() ? 1 : 0);
            statement.setInt(18, reward.characteristics().externalUncertaintyPossible() ? 1 : 0);
            statement.setString(19, reward.redactedPreview());
            statement.executeUpdate();
        }
    }

    private static void insertRecoveryCost(Connection connection, PlannedCost cost) throws SQLException {
        String sql = "INSERT INTO mp_prestige_recovery_costs (operation_id, action_id, player_uuid, cost_id, "
                + "provider_id, cost_type, value_type, value_text, metadata_text, display_name, config_revision_id, "
                + "provider_generation, idempotent, reversible, reconcilable, external_uncertainty, "
                + "redacted_preview) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cost.operationId().toString());
            statement.setString(2, cost.actionId());
            statement.setString(3, cost.playerId().toString());
            statement.setString(4, cost.definition().id().value());
            statement.setString(5, cost.definition().providerId().value());
            statement.setString(6, cost.definition().type());
            statement.setString(7, cost.definition().amount().type().name());
            statement.setString(8, cost.definition().amount().canonical());
            statement.setString(9, encodeMetadata(cost.definition().metadata()));
            statement.setString(10, cost.definition().displayName());
            statement.setString(11, cost.configRevision().value());
            statement.setLong(12, cost.providerGeneration());
            statement.setInt(13, cost.characteristics().idempotent() ? 1 : 0);
            statement.setInt(14, cost.characteristics().reversible() ? 1 : 0);
            statement.setInt(15, cost.characteristics().reconcilable() ? 1 : 0);
            statement.setInt(16, cost.characteristics().externalUncertaintyPossible() ? 1 : 0);
            statement.setString(17, cost.redactedPreview());
            statement.executeUpdate();
        }
    }

    private static String encodeMetadata(java.util.Map<String, String> metadata) {
        java.util.Base64.Encoder encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        return metadata.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> encoder.encodeToString(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + ":" + encoder.encodeToString(
                                entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private static java.util.Map<String, String> decodeMetadata(String encoded) {
        if (encoded.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (String item : encoded.split(",", -1)) {
            String[] pair = item.split(":", -1);
            if (pair.length != 2) {
                throw new PersistenceException("Stored reward recovery metadata is malformed");
            }
            String key = new String(decoder.decode(pair[0]), java.nio.charset.StandardCharsets.UTF_8);
            String value = new String(decoder.decode(pair[1]), java.nio.charset.StandardCharsets.UTF_8);
            if (result.putIfAbsent(key, value) != null) {
                throw new PersistenceException("Stored reward recovery metadata contains a duplicate key");
            }
        }
        return java.util.Map.copyOf(result);
    }

    private static void requireExecuting(Connection connection, OperationId operationId) throws SQLException {
        String sql = "SELECT state FROM mp_operations WHERE operation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !OperationState.EXECUTING.name().equals(row.getString(1))) {
                    throw new PersistenceException("Prestige operation is not in EXECUTING state");
                }
            }
        }
    }

    private static void updateStage(Connection connection, PrestigePlan plan, Instant now) throws SQLException {
        String sql = "UPDATE mp_player_stage_state SET stage_id = ?, state_revision = ?, config_revision_id = ?, "
                + "stage_entered_at = ?, updated_at = ?, last_reconciled_at = ?, last_provider_generation = ? "
                + "WHERE player_uuid = ? AND stage_id = ? AND state_revision = ? AND config_revision_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.simulation().resetStage().value());
            statement.setLong(2, Math.addExact(plan.expectedStageRevision(), 1));
            statement.setString(3, plan.configRevision().value());
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.setString(6, now.toString());
            if (plan.rankProjectionRequest().isPresent()) {
                statement.setLong(7, plan.rankProjectionRequest().orElseThrow().providerGeneration());
            } else {
                statement.setNull(7, Types.BIGINT);
            }
            statement.setString(8, plan.playerId().toString());
            statement.setString(9, plan.simulation().sourceStage().value());
            statement.setLong(10, plan.expectedStageRevision());
            statement.setString(11, plan.simulation().playerStageProvenance().value());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Authoritative stage CAS failed during Prestige");
            }
        }
    }

    private static void updatePrestige(Connection connection, PrestigePlan plan, Instant now) throws SQLException {
        String sql = "UPDATE mp_player_prestige_state SET current_prestige = ?, lifetime_prestige = ?, "
                + "state_revision = ?, config_revision_id = ?, prestige_scope_id = ?, last_prestiged_at = ?, "
                + "updated_at = ? WHERE player_uuid = ? AND current_prestige = ? AND lifetime_prestige = ? "
                + "AND state_revision = ? AND config_revision_id = ? AND prestige_scope_id = ?";
        var simulation = plan.simulation();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, simulation.currentPrestigeAfter());
            statement.setLong(2, simulation.lifetimePrestigeAfter());
            statement.setLong(3, Math.addExact(plan.expectedPrestigeRevision(), 1));
            statement.setString(4, plan.configRevision().value());
            statement.setString(5, simulation.prestigeScopeAfter().value());
            statement.setString(6, now.toString());
            statement.setString(7, now.toString());
            statement.setString(8, plan.playerId().toString());
            statement.setLong(9, simulation.currentPrestigeBefore());
            statement.setLong(10, simulation.lifetimePrestigeBefore());
            statement.setLong(11, plan.expectedPrestigeRevision());
            statement.setString(12, simulation.playerPrestigeProvenance().value());
            statement.setString(13, simulation.prestigeScopeBefore().value());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Authoritative Prestige CAS failed");
            }
        }
    }

    private static void resetCurrency(
            Connection connection,
            PrestigePlan plan,
            CurrencyConsequence currency,
            Instant now) throws SQLException {
        String find = "SELECT balance_text FROM mp_currency_accounts WHERE player_uuid = ? AND currency_id = ?";
        String current = "0";
        try (PreparedStatement statement = connection.prepareStatement(find)) {
            statement.setString(1, plan.playerId().toString());
            statement.setString(2, currency.currencyId().value());
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) {
                    current = row.getString(1);
                }
            }
        }
        if (!net.maddkraft.maddprestige.api.value.ExactDecimal.parse(current).equals(currency.before())) {
            throw new PersistenceException("Prestige-scoped currency balance changed after confirmation");
        }
        String ledger = "INSERT INTO mp_currency_ledger (operation_id, action_id, player_uuid, currency_id, "
                + "delta_text, balance_after_text, mutation_kind, actor_type, actor_uuid, actor_name, source, reason, "
                + "config_revision_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(ledger)) {
            statement.setString(1, plan.operationId().toString());
            statement.setString(2, "currency-reset-" + currency.currencyId().value());
            statement.setString(3, plan.playerId().toString());
            statement.setString(4, currency.currencyId().value());
            statement.setString(5, currency.delta().toString());
            statement.setString(6, currency.after().toString());
            statement.setString(7, CurrencyMutationKind.PRESTIGE_RESET.name());
            statement.setString(8, plan.operationPlan().actor().type());
            if (plan.operationPlan().actor().uuid().isPresent()) {
                statement.setString(9, plan.operationPlan().actor().uuid().orElseThrow().toString());
            } else {
                statement.setNull(9, Types.VARCHAR);
            }
            statement.setString(10, plan.operationPlan().actor().displayName());
            statement.setString(11, "prestige");
            statement.setString(12, currency.reason());
            statement.setString(13, plan.configRevision().value());
            statement.setString(14, now.toString());
            statement.executeUpdate();
        }
        String account = "INSERT INTO mp_currency_accounts (player_uuid, currency_id, balance_text, updated_at) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, currency_id) DO UPDATE SET "
                + "balance_text = excluded.balance_text, updated_at = excluded.updated_at";
        try (PreparedStatement statement = connection.prepareStatement(account)) {
            statement.setString(1, plan.playerId().toString());
            statement.setString(2, currency.currencyId().value());
            statement.setString(3, currency.after().toString());
            statement.setString(4, now.toString());
            statement.executeUpdate();
        }
    }

    private static void insertPrestigeBaselines(
            Connection connection,
            PrestigePlan plan,
            Instant now) throws SQLException {
        String sql = "INSERT INTO mp_requirement_baselines (player_uuid, requirement_id, measurement_scope, "
                + "scope_instance, semantic_fingerprint, value_type, value_text, provider_generation, created_at) "
                + "VALUES (?, ?, 'SINCE_PRESTIGE_START', ?, ?, ?, ?, ?, ?)";
        for (var baseline : plan.simulation().baselineChanges()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, plan.playerId().toString());
                statement.setString(2, baseline.requirementId().value());
                statement.setString(3, plan.simulation().prestigeScopeAfter().value());
                statement.setString(4, baseline.semanticFingerprint());
                statement.setString(5, baseline.value().type().name());
                statement.setString(6, baseline.value().canonical());
                statement.setLong(7, baseline.providerGeneration());
                statement.setString(8, now.toString());
                statement.executeUpdate();
            }
        }
    }

    private static void insertMilestone(
            Connection connection,
            PrestigePlan plan,
            MilestoneConsequence milestone,
            Instant now) throws SQLException {
        String sql = "INSERT INTO mp_milestone_awards (player_uuid, milestone_id, repeatability_key, operation_id, "
                + "config_revision_id, reward_snapshot, awarded_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plan.playerId().toString());
            statement.setString(2, milestone.milestoneId().value());
            statement.setString(3, milestone.repeatabilityKey());
            statement.setString(4, plan.operationId().toString());
            statement.setString(5, plan.configRevision().value());
            statement.setString(6, milestone.rewardIds().stream().map(value -> value.value())
                    .sorted().reduce((left, right) -> left + "," + right).orElse(""));
            statement.setString(7, now.toString());
            statement.executeUpdate();
        }
    }

    private static void insertStageHistory(Connection connection, PrestigePlan plan, Instant now) throws SQLException {
        String sql = "INSERT INTO mp_stage_history (history_id, player_uuid, stage_id, entered_at, operation_id, "
                + "actor_type, actor_uuid, actor_name, reason, config_revision_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, plan.playerId().toString());
            statement.setString(3, plan.simulation().resetStage().value());
            statement.setString(4, now.toString());
            statement.setString(5, plan.operationId().toString());
            statement.setString(6, plan.operationPlan().actor().type());
            if (plan.operationPlan().actor().uuid().isPresent()) {
                statement.setString(7, plan.operationPlan().actor().uuid().orElseThrow().toString());
            } else {
                statement.setNull(7, Types.VARCHAR);
            }
            statement.setString(8, plan.operationPlan().actor().displayName());
            statement.setString(9, "Prestige reset");
            statement.setString(10, plan.configRevision().value());
            statement.executeUpdate();
        }
    }

    private static void insertPrestigeHistory(
            Connection connection,
            PrestigePlan plan,
            Instant now) throws SQLException {
        String sql = "INSERT INTO mp_prestige_history (history_id, player_uuid, operation_id, event_type, "
                + "source_stage_id, reset_stage_id, current_before, current_after, lifetime_before, lifetime_after, "
                + "result, costs_snapshot, rewards_snapshot, config_revision_id, occurred_at) "
                + "VALUES (?, ?, ?, 'STATE_COMMITTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        var simulation = plan.simulation();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, plan.playerId().toString());
            statement.setString(3, plan.operationId().toString());
            statement.setString(4, simulation.sourceStage().value());
            statement.setString(5, simulation.resetStage().value());
            statement.setLong(6, simulation.currentPrestigeBefore());
            statement.setLong(7, simulation.currentPrestigeAfter());
            statement.setLong(8, simulation.lifetimePrestigeBefore());
            statement.setLong(9, simulation.lifetimePrestigeAfter());
            statement.setString(10, "STATE_COMMITTED");
            statement.setString(11, costsSnapshot(plan));
            statement.setString(12, rewardsSnapshot(plan));
            statement.setString(13, plan.configRevision().value());
            statement.setString(14, now.toString());
            statement.executeUpdate();
        }
    }

    private static String confirmationSnapshot(PrestigePlan plan) {
        var value = plan.simulation();
        return "source=" + value.sourceStage().value() + ";reset=" + value.resetStage().value()
                + ";current=" + value.currentPrestigeBefore() + "->" + value.currentPrestigeAfter()
                + ";lifetime=" + value.lifetimePrestigeBefore() + "->" + value.lifetimePrestigeAfter()
                + ";scope=" + value.prestigeScopeBefore().value() + "->" + value.prestigeScopeAfter().value();
    }

    private static String costsSnapshot(PrestigePlan plan) {
        return plan.costs().stream().map(cost -> cost.definition().id().value() + "="
                + cost.definition().amount().canonical()).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String rewardsSnapshot(PrestigePlan plan) {
        return plan.rewards().stream().map(reward -> reward.definition().id().value() + "="
                + reward.definition().value().canonical()).reduce((left, right) -> left + "," + right).orElse("");
    }
}
