package net.maddkraft.maddprestige.persistence.sqlite;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import net.maddkraft.maddprestige.persistence.jdbc.ConnectionProvider;

public final class SqliteFoundation implements ConnectionProvider {
    private final Path databaseFile;
    private final SQLiteDataSource dataSource;
    private final ReentrantReadWriteLock coordination = new ReentrantReadWriteLock(true);

    public SqliteFoundation(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "database file").toAbsolutePath().normalize();
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(5000);
        dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + this.databaseFile);
    }

    public Path databaseFile() {
        return databaseFile;
    }

    @Override
    public Connection open() throws SQLException {
        coordination.readLock().lock();
        try {
            return coordinated(openUncoordinated());
        } catch (SQLException | RuntimeException exception) {
            coordination.readLock().unlock();
            throw exception;
        }
    }

    private Connection openUncoordinated() throws SQLException {
        Connection connection = dataSource.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    /**
     * Drains every connection opened through this foundation, then fences new connections while a SQLite-native
     * snapshot is sealed. The fair lock also allows already queued application work to complete before backup begins.
     * Shared-database/multi-process access is outside the supported 2.0 deployment model.
     */
    public <T> T withExclusiveSnapshotAccess(SqliteWork<T> work) throws SQLException {
        Objects.requireNonNull(work, "work");
        coordination.writeLock().lock();
        try (Connection connection = openUncoordinated()) {
            return work.execute(connection);
        } finally {
            coordination.writeLock().unlock();
        }
    }

    private Connection coordinated(Connection delegate) {
        AtomicBoolean released = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                SqliteFoundation.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) {
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        } finally {
                            if (released.compareAndSet(false, true)) {
                                coordination.readLock().unlock();
                            }
                        }
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    @FunctionalInterface
    public interface SqliteWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
