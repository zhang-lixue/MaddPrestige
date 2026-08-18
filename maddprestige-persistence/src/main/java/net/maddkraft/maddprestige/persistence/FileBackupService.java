package net.maddkraft.maddprestige.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.config.ContentHash;

/**
 * Byte-copy backup retained for already-quiesced test fixtures. It is not safe for a live SQLite database and is not
 * used by production composition. Production uses {@code SqliteBackupService}, which coordinates through SQLite.
 */
public final class FileBackupService implements BackupService {
    private final Path source;
    private final Path backupDirectory;
    private final Clock clock;

    public FileBackupService(Path source, Path backupDirectory, Clock clock) {
        this.source = source.toAbsolutePath().normalize();
        this.backupDirectory = backupDirectory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public VerifiedBackup createVerifiedBackup(String reason) {
        String backupId = UUID.randomUUID().toString();
        Instant createdAt = clock.instant();
        try {
            if (!Files.isRegularFile(source)) {
                return VerifiedBackup.failure(backupId, "Backup source does not exist as a regular file");
            }
            Files.createDirectories(backupDirectory);
            Path destination = backupDirectory.resolve(backupId + ".bak").normalize();
            if (!destination.startsWith(backupDirectory)) {
                return VerifiedBackup.failure(backupId, "Resolved backup path escaped the backup directory");
            }
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
            ContentHash sourceHash = hash(source);
            ContentHash backupHash = hash(destination);
            if (!sourceHash.equals(backupHash)) {
                Files.deleteIfExists(destination);
                return VerifiedBackup.failure(backupId, "Backup checksum verification failed");
            }
            return new VerifiedBackup(backupId, Optional.of(destination), Optional.of(backupHash), createdAt, true,
                    "Verified byte-for-byte backup for: " + reason);
        } catch (IOException exception) {
            return VerifiedBackup.failure(backupId, "Backup failed: " + exception.getMessage());
        }
    }

    private static ContentHash hash(Path file) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }
}
