package net.maddkraft.maddprestige.persistence.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteConfigRevisionRepository {
    private final ConnectionProvider connections;

    public SqliteConfigRevisionRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    public void insert(ConfigRevisionId id, ContentHash hash) {
        String sql = "INSERT INTO mp_config_revisions "
                + "(revision_id, content_hash, created_at, actor, source_surface, validation_summary, diff_summary) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setString(2, hash.value());
            statement.setString(3, Instant.now().toString());
            statement.setString(4, "testkit");
            statement.setString(5, "configuration-initialization");
            statement.setString(6, "valid");
            statement.setString(7, "initial");
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not persist configuration revision", exception);
        }
    }
}
