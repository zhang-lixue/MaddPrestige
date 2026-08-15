package net.maddkraft.maddprestige.core.config;

import java.time.Instant;
import java.util.Objects;

public record BackupMetadata(String backupId, ContentHash checksum, Instant createdAt, boolean verified) {
    public BackupMetadata {
        backupId = Objects.requireNonNull(backupId, "backup ID");
        checksum = Objects.requireNonNull(checksum, "checksum");
        createdAt = Objects.requireNonNull(createdAt, "created at");
    }
}
