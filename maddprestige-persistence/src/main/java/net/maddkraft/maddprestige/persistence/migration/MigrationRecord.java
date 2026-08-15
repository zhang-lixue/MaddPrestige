package net.maddkraft.maddprestige.persistence.migration;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.core.config.ContentHash;

public record MigrationRecord(
        UUID attemptId,
        long version,
        ContentHash checksum,
        String description,
        Instant appliedAt,
        MigrationResult result,
        String detail) {
    public MigrationRecord {
        attemptId = Objects.requireNonNull(attemptId, "attempt ID");
        checksum = Objects.requireNonNull(checksum, "checksum");
        description = Objects.requireNonNull(description, "description");
        appliedAt = Objects.requireNonNull(appliedAt, "applied at");
        result = Objects.requireNonNull(result, "result");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
