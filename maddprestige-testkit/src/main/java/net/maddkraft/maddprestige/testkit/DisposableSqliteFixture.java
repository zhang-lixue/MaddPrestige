package net.maddkraft.maddprestige.testkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;

public final class DisposableSqliteFixture implements AutoCloseable {
    private final Path directory;
    private final Path database;
    private final SqliteFoundation foundation;

    private DisposableSqliteFixture(Path directory) {
        this.directory = directory;
        this.database = directory.resolve("persistence-test.db");
        this.foundation = new SqliteFoundation(database);
        new MigrationRunner(foundation, new FileBackupService(database, directory.resolve("backups"), Clock.systemUTC()),
                Clock.systemUTC()).migrate(SqliteMigrations.current());
    }

    public static DisposableSqliteFixture create() throws IOException {
        Path systemTemp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory(systemTemp, "maddprestige-v2-").toAbsolutePath().normalize();
        if (!directory.startsWith(systemTemp) || !directory.getFileName().toString().startsWith("maddprestige-v2-")) {
            throw new IOException("Refusing unsafe disposable fixture directory: " + directory);
        }
        return new DisposableSqliteFixture(directory);
    }

    public Path database() {
        return database;
    }

    public SqliteFoundation foundation() {
        return foundation;
    }

    public void prepareConfigurationTransition(
            ConfigRevisionId revision,
            Optional<ConfigRevisionId> parent,
            ContentHash contentHash,
            Instant occurredAt) {
        String sql = "INSERT OR IGNORE INTO mp_configuration_revisions_v2 (revision_id, parent_revision_id, "
                + "canonical_content_hash, actor_type, actor_name, source_surface, reason, validation_summary, "
                + "diff_summary, application_status, created_at) VALUES (?, ?, ?, 'console', 'Owner', "
                + "'testkit', 'configuration transition test', 'valid', 'test', 'ATTEMPTED', ?)";
        try (var connection = foundation.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, revision.value());
            if (parent.isPresent()) {
                statement.setString(2, parent.orElseThrow().value());
            } else {
                statement.setNull(2, java.sql.Types.VARCHAR);
            }
            statement.setString(3, contentHash.value());
            statement.setString(4, occurredAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not prepare test configuration transition owner", exception);
        }
    }

    @Override
    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
