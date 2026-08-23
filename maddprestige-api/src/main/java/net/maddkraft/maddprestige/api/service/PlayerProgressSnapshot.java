package net.maddkraft.maddprestige.api.service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;

/**
 * Immutable materialized player-progress read model; construction performs no I/O.
 *
 * @param playerId queried player
 * @param stage materialized canonical stage, or empty for a player with no committed progression state
 * @param currentPrestige non-negative Prestige count in the current reset scope
 * @param lifetimePrestige non-negative lifetime count, never less than {@code currentPrestige}
 * @param configRevision revision that materialized this read model, or empty before first committed state
 * @param attributes immutable implementation-neutral machine attributes, at most 32 bounded entries
 * @param observedAt non-null observation time; the snapshot is not live after construction
 */
public record PlayerProgressSnapshot(
        UUID playerId,
        Optional<StageId> stage,
        long currentPrestige,
        long lifetimePrestige,
        Optional<ConfigRevisionId> configRevision,
        Map<String, String> attributes,
        Instant observedAt) {
    public PlayerProgressSnapshot {
        playerId = Objects.requireNonNull(playerId, "player ID");
        stage = Objects.requireNonNull(stage, "stage");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        observedAt = Objects.requireNonNull(observedAt, "observation time");
        if (currentPrestige < 0 || lifetimePrestige < currentPrestige || attributes.size() > 32
                || attributes.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                        || entry.getKey().length() > 64 || entry.getValue().length() > 256)) {
            throw new IllegalArgumentException("Player progress snapshot is outside stable bounds");
        }
    }
}
