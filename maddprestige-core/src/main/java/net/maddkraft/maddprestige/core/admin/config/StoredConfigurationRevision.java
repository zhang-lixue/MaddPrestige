package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;

public record StoredConfigurationRevision(
        ConfigRevisionId id,
        Optional<ConfigRevisionId> parent,
        Optional<ConfigRevisionId> rollbackSource,
        CompiledConfiguration compiled,
        Actor actor,
        String sourceSurface,
        String reason,
        ValidationReport validation,
        String diffSummary,
        ConfigurationApplicationStatus status,
        Instant createdAt,
        Optional<Instant> appliedAt,
        Optional<String> failure) {
    public StoredConfigurationRevision {
        id = Objects.requireNonNull(id, "revision ID");
        parent = Objects.requireNonNull(parent, "parent");
        rollbackSource = Objects.requireNonNull(rollbackSource, "rollback source");
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
        actor = Objects.requireNonNull(actor, "actor");
        sourceSurface = Objects.requireNonNull(sourceSurface, "source surface");
        reason = Objects.requireNonNull(reason, "reason");
        validation = Objects.requireNonNull(validation, "validation");
        diffSummary = Objects.requireNonNull(diffSummary, "diff summary");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "created at");
        appliedAt = Objects.requireNonNull(appliedAt, "applied at");
        failure = Objects.requireNonNull(failure, "failure");
        if (sourceSurface.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException("Configuration revision source and reason are required");
        }
        if (status == ConfigurationApplicationStatus.APPLIED && appliedAt.isEmpty()) {
            throw new IllegalArgumentException("Applied configuration revision requires an applied time");
        }
    }

    public StoredConfigurationRevision withOutcome(
            ConfigurationApplicationStatus replacement,
            Optional<Instant> appliedTime,
            Optional<String> failureDetail) {
        return new StoredConfigurationRevision(id, parent, rollbackSource, compiled, actor, sourceSurface, reason,
                validation, diffSummary, replacement, createdAt, appliedTime, failureDetail);
    }
}
