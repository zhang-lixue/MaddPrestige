package net.maddkraft.maddprestige.core.config;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public final class ConfigurationService {
    private final AtomicReference<ActiveConfiguration> active = new AtomicReference<>();

    public Optional<ActiveConfiguration> active() {
        return Optional.ofNullable(active.get());
    }

    public ActiveConfiguration apply(
            ConfigRevisionId revisionId,
            CompiledConfiguration candidate,
            ValidationReport validation,
            Set<String> acknowledgements,
            BackupMetadata backup) {
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(acknowledgements, "acknowledgements");
        Objects.requireNonNull(backup, "backup");
        if (!validation.canApply(acknowledgements)) {
            throw new IllegalStateException("Invalid or unacknowledged configuration cannot become active");
        }
        if (!backup.verified()) {
            throw new IllegalStateException("Configuration apply requires a verified backup");
        }
        ActiveConfiguration replacement = new ActiveConfiguration(revisionId, candidate);
        active.set(replacement);
        return replacement;
    }

    public ActiveConfiguration rollback(
            ConfigRevisionId newRevisionId,
            CompiledConfiguration priorContent,
            ValidationReport validation,
            BackupMetadata backup) {
        return apply(newRevisionId, priorContent, validation, Set.of(), backup);
    }
}
