package net.maddkraft.maddprestige.api.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/**
 * Immutable payload emitted after one canonical configuration revision is durably applied.
 *
 * @param revision exact newly authoritative revision
 * @param priorRevision exact prior revision, or empty on the first canonical apply
 * @param changedDocuments immutable ordered document identities, at most 32 bounded entries
 * @param occurredAt non-null completion time
 */
public record ConfigAppliedSnapshot(
        ConfigRevisionId revision,
        Optional<ConfigRevisionId> priorRevision,
        List<String> changedDocuments,
        Instant occurredAt) {
    public ConfigAppliedSnapshot {
        revision = Objects.requireNonNull(revision, "revision");
        priorRevision = Objects.requireNonNull(priorRevision, "prior revision");
        changedDocuments = List.copyOf(Objects.requireNonNull(changedDocuments, "changed documents"));
        occurredAt = Objects.requireNonNull(occurredAt, "occurrence time");
        if (changedDocuments.size() > 32 || changedDocuments.stream().anyMatch(value -> value == null
                || value.isBlank() || value.length() > 128)) {
            throw new IllegalArgumentException("Changed-document payload is outside stable bounds");
        }
    }
}
