package net.maddkraft.maddprestige.persistence.sqlite;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.persistence.BackupService;
import net.maddkraft.maddprestige.persistence.PersistenceException;
import net.maddkraft.maddprestige.persistence.VerifiedBackup;
import net.maddkraft.maddprestige.persistence.migration.Migration;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import org.sqlite.SQLiteConnection;

/** SQLite-native, coordinated backup authority used by production migration startup. */
public final class SqliteBackupService implements BackupService {
    private final SqliteFoundation source;
    private final Path backupDirectory;
    private final List<Migration> migrations;
    private final Clock clock;

    public SqliteBackupService(
            SqliteFoundation source, Path backupDirectory, List<Migration> migrations, Clock clock) {
        this.source = Objects.requireNonNull(source, "source");
        this.backupDirectory = Objects.requireNonNull(backupDirectory, "backup directory")
                .toAbsolutePath().normalize();
        this.migrations = List.copyOf(Objects.requireNonNull(migrations, "migrations"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public VerifiedBackup createVerifiedBackup(String reason) {
        String backupId = UUID.randomUUID().toString();
        Instant createdAt = clock.instant();
        Path partialDatabase = backupDirectory.resolve(backupId + ".sqlite.partial");
        Path finalDatabase = backupDirectory.resolve(backupId + ".sqlite");
        Path partialManifest = manifestPath(partialDatabase);
        Path finalManifest = manifestPath(finalDatabase);
        boolean finalDatabaseOwned = false;
        boolean finalManifestOwned = false;
        try {
            validateReason(reason);
            prepareBackupDirectory();
            requireNewControlledPath(partialDatabase);
            requireNewControlledPath(finalDatabase);
            requireNewControlledPath(partialManifest);
            requireNewControlledPath(finalManifest);

            source.withExclusiveSnapshotAccess(connection -> {
                SQLiteConnection sqlite = connection.unwrap(SQLiteConnection.class);
                int result = sqlite.getDatabase().backup("main", partialDatabase.toString(), null);
                if (result != 0) {
                    throw new SQLException("SQLite native backup API returned result code " + result);
                }
                return null;
            });
            force(partialDatabase);

            SqliteValidationResult validation = SqliteDatabaseValidator.validate(partialDatabase, migrations);
            rehearseRestore(partialDatabase);
            deleteSidecars(partialDatabase);
            ContentHash checksum = sha256(partialDatabase);
            SqliteBackupManifest manifest = SqliteBackupManifest.accepted(
                    backupId, createdAt, reason, source.databaseFile().toString(), validation,
                    finalDatabase.getFileName().toString(), checksum);
            manifest.writeNew(partialManifest);
            promote(partialDatabase, finalDatabase);
            finalDatabaseOwned = true;
            try {
                promote(partialManifest, finalManifest);
                finalManifestOwned = true;
            } catch (IOException exception) {
                Files.deleteIfExists(finalDatabase);
                finalDatabaseOwned = false;
                throw exception;
            }
            validateAcceptedBackup(finalDatabase, migrations);
            deleteSidecars(finalDatabase);
            return new VerifiedBackup(backupId, Optional.of(finalDatabase), Optional.of(checksum), createdAt, true,
                    "SQLite-native backup passed integrity validation and disposable restore rehearsal; manifest="
                            + finalManifest.getFileName());
        } catch (IOException | SQLException | RuntimeException exception) {
            cleanup(partialDatabase);
            cleanup(partialManifest);
            cleanupSidecars(partialDatabase);
            if (finalManifestOwned) {
                cleanup(finalManifest);
            }
            if (finalDatabaseOwned) {
                cleanup(finalDatabase);
                cleanupSidecars(finalDatabase);
            }
            return new VerifiedBackup(backupId, Optional.empty(), Optional.empty(), createdAt, false,
                    "SQLite backup rejected: " + actionableMessage(exception));
        }
    }

    public static SqliteBackupManifest validateAcceptedBackup(Path database, List<Migration> migrations) {
        Path normalized = database.toAbsolutePath().normalize();
        Path manifestPath = manifestPath(normalized);
        if (!Files.isRegularFile(manifestPath)) {
            throw new PersistenceException("Accepted SQLite backup manifest is missing: " + manifestPath);
        }
        SqliteBackupManifest manifest = SqliteBackupManifest.read(manifestPath);
        String expectedArtifact = manifest.backupId() + ".sqlite";
        if (!expectedArtifact.equals(manifest.databaseArtifact())) {
            throw new PersistenceException("Backup manifest ID is not bound to its canonical database artifact");
        }
        if (!normalized.getFileName().toString().equals(manifest.databaseArtifact())) {
            throw new PersistenceException("Backup manifest artifact identity does not match the database filename");
        }
        ContentHash actualHash;
        try {
            actualHash = sha256(normalized);
        } catch (IOException exception) {
            throw new PersistenceException("Could not hash accepted SQLite backup", exception);
        }
        if (!actualHash.equals(manifest.sha256())) {
            throw new PersistenceException("Backup SHA-256 does not match its manifest");
        }
        if (!"PASS".equals(manifest.validationResult()) || !"PASS".equals(manifest.restoreRehearsalResult())) {
            throw new PersistenceException("Backup manifest does not contain accepted validation and rehearsal results");
        }
        SqliteValidationResult validation = SqliteDatabaseValidator.validate(normalized, migrations);
        if (validation.schemaVersion() != manifest.sourceSchemaVersion()) {
            throw new PersistenceException("Backup manifest source schema does not match the database");
        }
        if (!validation.activeConfigurationRevision().equals(manifest.activeConfigurationRevision())) {
            throw new PersistenceException("Backup manifest active configuration does not match the database");
        }
        if (!validation.journalMode().equals(manifest.journalMode())) {
            throw new PersistenceException("Backup manifest journal mode does not match the database");
        }
        return manifest;
    }

    public static Path manifestPath(Path database) {
        return database.resolveSibling(database.getFileName() + ".manifest");
    }

    public static ContentHash sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return new ContentHash(HexFormat.of().formatHex(digest.digest()));
    }

    private void rehearseRestore(Path backup) throws IOException {
        Path rehearsalDirectory = Files.createTempDirectory(backupDirectory, ".restore-rehearsal-");
        if (!rehearsalDirectory.normalize().startsWith(backupDirectory)) {
            throw new IOException("Restore rehearsal directory escaped the controlled backup directory");
        }
        Path rehearsalDatabase = rehearsalDirectory.resolve("rehearsal.sqlite");
        try {
            Files.copy(backup, rehearsalDatabase);
            SqliteFoundation rehearsal = new SqliteFoundation(rehearsalDatabase);
            BackupService alreadyProtected = ignored -> {
                try {
                    return new VerifiedBackup("rehearsal-source", Optional.of(rehearsalDatabase),
                            Optional.of(sha256(rehearsalDatabase)), clock.instant(), true,
                            "Disposable rehearsal uses the independently validated source snapshot");
                } catch (IOException exception) {
                    throw new PersistenceException("Could not hash disposable rehearsal source", exception);
                }
            };
            new MigrationRunner(rehearsal, alreadyProtected, clock).migrate(migrations);
            SqliteValidationResult result = SqliteDatabaseValidator.validate(rehearsalDatabase, migrations);
            if (result.schemaVersion() != migrations.getLast().version()) {
                throw new PersistenceException("Restore rehearsal did not reach the current schema version");
            }
        } catch (RuntimeException exception) {
            throw new PersistenceException("Disposable restore rehearsal failed: " + actionableMessage(exception),
                    exception);
        } finally {
            deleteControlledTree(rehearsalDirectory);
        }
    }

    private void prepareBackupDirectory() throws IOException {
        if (!Files.isRegularFile(source.databaseFile()) || Files.isSymbolicLink(source.databaseFile())) {
            throw new IOException("SQLite backup source must be an existing non-symlink regular file");
        }
        if (Files.exists(backupDirectory) && Files.isSymbolicLink(backupDirectory)) {
            throw new IOException("Backup directory must not be a symbolic link");
        }
        Files.createDirectories(backupDirectory);
        if (!Files.isDirectory(backupDirectory) || Files.isSymbolicLink(backupDirectory)) {
            throw new IOException("Backup path is not a controlled real directory");
        }
        if (!backupDirectory.toRealPath().equals(backupDirectory)) {
            throw new IOException("Backup directory contains a symbolic-link or noncanonical path component");
        }
    }

    private void requireNewControlledPath(Path path) throws IOException {
        if (!path.normalize().startsWith(backupDirectory) || Files.exists(path)) {
            throw new IOException("Backup destination is not a new controlled path: " + path.getFileName());
        }
    }

    private static void validateReason(String reason) {
        Objects.requireNonNull(reason, "backup reason");
        if (reason.isBlank() || reason.length() > 512 || reason.chars().anyMatch(character -> character < 32)) {
            throw new IllegalArgumentException("Backup reason must be 1-512 printable characters");
        }
    }

    private static void promote(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(partial, target);
        }
    }

    private static void force(Path file) throws IOException {
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void deleteControlledTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String actionableMessage(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? " without detail" : ": " + message);
    }

    private static void cleanup(Path... paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // The unpromoted artifact remains invalid because it lacks a complete accepted artifact+manifest pair.
            }
        }
    }

    private static void deleteSidecars(Path database) throws IOException {
        Files.deleteIfExists(Path.of(database + "-wal"));
        Files.deleteIfExists(Path.of(database + "-shm"));
        Files.deleteIfExists(Path.of(database + "-journal"));
    }

    private static void cleanupSidecars(Path database) {
        cleanup(Path.of(database + "-wal"), Path.of(database + "-shm"), Path.of(database + "-journal"));
    }
}
