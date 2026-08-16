package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.currency.CurrencyDefinition;
import net.maddkraft.maddprestige.core.currency.CurrencyHistoryEntry;
import net.maddkraft.maddprestige.core.currency.CurrencyLedgerStore;
import net.maddkraft.maddprestige.core.currency.CurrencyMutation;
import net.maddkraft.maddprestige.core.currency.CurrencyMutationKind;
import net.maddkraft.maddprestige.core.currency.CurrencyMutationResult;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteCurrencyLedgerStore implements CurrencyLedgerStore {
    private static final int MAX_TRANSACTION_ATTEMPTS = 8;
    private static final String FIND_BALANCE = "SELECT balance_text FROM mp_currency_accounts "
            + "WHERE player_uuid = ? AND currency_id = ?";
    private static final String FIND_MUTATION = "SELECT player_uuid, currency_id, delta_text, balance_after_text, "
            + "mutation_kind, config_revision_id, actor_type, actor_uuid, actor_name, source, reason "
            + "FROM mp_currency_ledger WHERE operation_id = ? AND action_id = ?";
    private static final String UPSERT_BALANCE = "INSERT INTO mp_currency_accounts "
            + "(player_uuid, currency_id, balance_text, updated_at) VALUES (?, ?, ?, ?) "
            + "ON CONFLICT(player_uuid, currency_id) DO UPDATE SET balance_text = excluded.balance_text, "
            + "updated_at = excluded.updated_at";
    private static final String INSERT_LEDGER = "INSERT INTO mp_currency_ledger (operation_id, action_id, "
            + "player_uuid, currency_id, delta_text, balance_after_text, mutation_kind, actor_type, actor_uuid, "
            + "actor_name, source, reason, config_revision_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?)";
    private static final String HISTORY = "SELECT sequence_id, operation_id, action_id, delta_text, "
            + "balance_after_text, mutation_kind, actor_type, actor_name, source, reason, config_revision_id, "
            + "occurred_at FROM mp_currency_ledger WHERE player_uuid = ? AND currency_id = ? "
            + "ORDER BY sequence_id DESC LIMIT ?";
    private final ConnectionProvider connections;

    public SqliteCurrencyLedgerStore(ConnectionProvider connections) {
        this.connections = java.util.Objects.requireNonNull(connections, "connection provider");
    }

    @Override
    public ExactDecimal balance(UUID playerId, CurrencyId currencyId) {
        try (Connection connection = connections.open()) {
            return balance(connection, playerId, currencyId);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load currency balance", exception);
        }
    }

    @Override
    public CurrencyMutationResult apply(CurrencyDefinition definition, CurrencyMutation mutation) {
        if (!definition.id().equals(mutation.currencyId())) {
            throw new IllegalArgumentException("Currency mutation definition ID differs from mutation ID");
        }
        ExactDecimal delta;
        try {
            delta = mutation.delta().withScale(definition.scale(), definition.roundingMode());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Currency delta violates configured scale", exception);
        }
        if (!delta.equals(mutation.delta()) || delta.precision() > definition.maximumPrecision()) {
            throw new IllegalArgumentException("Currency delta violates configured precision/scale");
        }
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try (Connection connection = connections.open()) {
                connection.setAutoCommit(false);
                try {
                    Optional<CurrencyMutationResult> replay = existing(connection, mutation, delta);
                    if (replay.isPresent()) {
                        connection.rollback();
                        return replay.orElseThrow();
                    }
                    ExactDecimal before = balance(connection, mutation.playerId(), mutation.currencyId());
                    ExactDecimal after = definition.normalize(before.add(delta));
                    insertLedger(connection, mutation, delta, after);
                    upsertBalance(connection, mutation, after);
                    connection.commit();
                    return new CurrencyMutationResult(before, after, false);
                } catch (SQLException exception) {
                    rollbackQuietly(connection);
                    if (attempt < MAX_TRANSACTION_ATTEMPTS && isRetryableContention(exception)) {
                        Thread.onSpinWait();
                        continue;
                    }
                    throw new PersistenceException("Could not apply idempotent currency mutation after "
                            + attempt + " database transaction attempt(s)", exception);
                } catch (RuntimeException exception) {
                    rollbackQuietly(connection);
                    throw exception;
                }
            } catch (SQLException exception) {
                if (attempt < MAX_TRANSACTION_ATTEMPTS && isRetryableContention(exception)) {
                    Thread.onSpinWait();
                    continue;
                }
                throw new PersistenceException("Could not open/complete currency transaction", exception);
            }
        }
        throw new PersistenceException("Currency mutation exhausted its bounded SQLite contention retry");
    }

    @Override
    public List<CurrencyHistoryEntry> history(UUID playerId, CurrencyId currencyId, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Currency history limit must be 1-1000");
        }
        ArrayList<CurrencyHistoryEntry> result = new ArrayList<>();
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(HISTORY)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currencyId.value());
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new CurrencyHistoryEntry(rows.getLong(1),
                            new OperationId(UUID.fromString(rows.getString(2))), rows.getString(3), playerId,
                            currencyId, ExactDecimal.parse(rows.getString(4)), ExactDecimal.parse(rows.getString(5)),
                            CurrencyMutationKind.valueOf(rows.getString(6)), rows.getString(7), rows.getString(8),
                            rows.getString(9), rows.getString(10), new ConfigRevisionId(rows.getString(11)),
                            java.time.Instant.parse(rows.getString(12))));
                }
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load currency history", exception);
        }
    }

    private static ExactDecimal balance(Connection connection, UUID playerId, CurrencyId currencyId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_BALANCE)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currencyId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? ExactDecimal.parse(row.getString(1)) : ExactDecimal.ZERO;
            }
        }
    }

    private static Optional<CurrencyMutationResult> existing(
            Connection connection,
            CurrencyMutation mutation,
            ExactDecimal delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_MUTATION)) {
            statement.setString(1, mutation.operationId().toString());
            statement.setString(2, mutation.actionId());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                ExactDecimal storedDelta = ExactDecimal.parse(row.getString(3));
                if (!row.getString(1).equals(mutation.playerId().toString())
                        || !row.getString(2).equals(mutation.currencyId().value()) || !storedDelta.equals(delta)
                        || !row.getString(5).equals(mutation.kind().name())
                        || !row.getString(6).equals(mutation.configRevision().value())
                        || !row.getString(7).equals(mutation.actor().type())
                        || !java.util.Objects.equals(row.getString(8),
                                mutation.actor().uuid().map(UUID::toString).orElse(null))
                        || !row.getString(9).equals(mutation.actor().displayName())
                        || !row.getString(10).equals(mutation.source())
                        || !row.getString(11).equals(mutation.reason())) {
                    throw new SecurityException(
                            "Currency idempotency key collides with different semantic mutation data");
                }
                ExactDecimal after = ExactDecimal.parse(row.getString(4));
                return Optional.of(new CurrencyMutationResult(after.subtract(storedDelta), after, true));
            }
        }
    }

    private static void insertLedger(
            Connection connection,
            CurrencyMutation mutation,
            ExactDecimal delta,
            ExactDecimal after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
            statement.setString(1, mutation.operationId().toString());
            statement.setString(2, mutation.actionId());
            statement.setString(3, mutation.playerId().toString());
            statement.setString(4, mutation.currencyId().value());
            statement.setString(5, delta.toString());
            statement.setString(6, after.toString());
            statement.setString(7, mutation.kind().name());
            statement.setString(8, mutation.actor().type());
            if (mutation.actor().uuid().isPresent()) {
                statement.setString(9, mutation.actor().uuid().orElseThrow().toString());
            } else {
                statement.setNull(9, Types.VARCHAR);
            }
            statement.setString(10, mutation.actor().displayName());
            statement.setString(11, mutation.source());
            statement.setString(12, mutation.reason());
            statement.setString(13, mutation.configRevision().value());
            statement.setString(14, mutation.occurredAt().toString());
            statement.executeUpdate();
        }
    }

    private static void upsertBalance(
            Connection connection,
            CurrencyMutation mutation,
            ExactDecimal after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_BALANCE)) {
            statement.setString(1, mutation.playerId().toString());
            statement.setString(2, mutation.currencyId().value());
            statement.setString(3, after.toString());
            statement.setString(4, mutation.occurredAt().toString());
            statement.executeUpdate();
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original transaction error is authoritative.
        }
    }

    private static boolean isRetryableContention(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            int code = current.getErrorCode();
            String message = Optional.ofNullable(current.getMessage()).orElse("").toLowerCase(java.util.Locale.ROOT);
            if (code == 5 || code == 6 || code == 517 || message.contains("sqlite_busy")
                    || message.contains("database is locked") || message.contains("database table is locked")) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }
}
