package net.maddkraft.maddprestige.testkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
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
        this.database = directory.resolve("phase1-test.db");
        this.foundation = new SqliteFoundation(database);
        new MigrationRunner(foundation, new FileBackupService(database, directory.resolve("backups"), Clock.systemUTC()),
                Clock.systemUTC()).migrate(SqliteMigrations.phaseFour());
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

    @Override
    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
