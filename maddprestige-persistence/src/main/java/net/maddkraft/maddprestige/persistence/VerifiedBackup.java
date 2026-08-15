package net.maddkraft.maddprestige.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.config.ContentHash;

public record VerifiedBackup(
        String backupId,
        Optional<Path> location,
        Optional<ContentHash> checksum,
        Instant createdAt,
        boolean verified,
        String detail) {
    public VerifiedBackup {
        backupId = Objects.requireNonNull(backupId, "backup ID");
        location = Objects.requireNonNull(location, "location");
        checksum = Objects.requireNonNull(checksum, "checksum");
        createdAt = Objects.requireNonNull(createdAt, "created at");
        detail = Objects.requireNonNull(detail, "detail");
        if (verified && (location.isEmpty() || checksum.isEmpty())) {
            throw new IllegalArgumentException("A verified backup requires a location and checksum");
        }
    }

    public static VerifiedBackup failure(String backupId, String detail) {
        return new VerifiedBackup(backupId, Optional.empty(), Optional.empty(), Instant.now(), false, detail);
    }
}
