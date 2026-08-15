package net.maddkraft.maddprestige.core.config;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record ConfigRevision(
        ConfigRevisionId id,
        Optional<ConfigRevisionId> parent,
        ContentHash contentHash,
        Map<String, ContentHash> fileHashes,
        Instant createdAt,
        Optional<Instant> appliedAt,
        Actor actor,
        String sourceSurface,
        ValidationReport validation,
        SemanticDiff diff,
        Optional<BackupMetadata> backup) {
    public ConfigRevision {
        id = Objects.requireNonNull(id, "revision ID");
        parent = Objects.requireNonNull(parent, "parent");
        contentHash = Objects.requireNonNull(contentHash, "content hash");
        fileHashes = Map.copyOf(Objects.requireNonNull(fileHashes, "file hashes"));
        createdAt = Objects.requireNonNull(createdAt, "created at");
        appliedAt = Objects.requireNonNull(appliedAt, "applied at");
        actor = Objects.requireNonNull(actor, "actor");
        sourceSurface = Objects.requireNonNull(sourceSurface, "source surface");
        validation = Objects.requireNonNull(validation, "validation");
        diff = Objects.requireNonNull(diff, "diff");
        backup = Objects.requireNonNull(backup, "backup");
    }
}
