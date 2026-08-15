package net.maddkraft.maddprestige.persistence.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteFoundation implements ConnectionProvider {
    private final SQLiteDataSource dataSource;

    public SqliteFoundation(Path databaseFile) {
        Path normalized = databaseFile.toAbsolutePath().normalize();
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(5000);
        dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + normalized);
    }

    @Override
    public Connection open() throws SQLException {
        Connection connection = dataSource.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }
}
