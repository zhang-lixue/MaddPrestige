package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.persistence.CurrencyAccountRepository;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteCurrencyAccountRepository implements CurrencyAccountRepository {
    private final ConnectionProvider connections;

    public SqliteCurrencyAccountRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void set(UUID playerId, CurrencyId currencyId, ExactDecimal value) {
        String sql = "INSERT INTO mp_currency_accounts (player_uuid, currency_id, balance_text, updated_at) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(player_uuid, currency_id) DO UPDATE SET "
                + "balance_text = excluded.balance_text, updated_at = excluded.updated_at";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currencyId.value());
            statement.setString(3, value.toString());
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not store exact decimal currency balance", exception);
        }
    }

    @Override
    public Optional<ExactDecimal> find(UUID playerId, CurrencyId currencyId) {
        String sql = "SELECT balance_text FROM mp_currency_accounts WHERE player_uuid = ? AND currency_id = ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currencyId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(ExactDecimal.parse(row.getString(1))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load exact decimal currency balance", exception);
        }
    }
}
