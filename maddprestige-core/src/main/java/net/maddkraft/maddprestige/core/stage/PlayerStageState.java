package net.maddkraft.maddprestige.core.stage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;

/**
 * Authoritative player progression state. {@code configRevision} is historical provenance: the active
 * configuration revision that last wrote this stage row, not a requirement that every later safe apply rewrite it.
 */
public record PlayerStageState(
        UUID playerId,
        StageId stageId,
        long stateRevision,
        ConfigRevisionId configRevision,
        Instant stageEnteredAt,
        Instant createdAt,
        Instant updatedAt,
        Optional<Instant> lastReconciledAt,
        Optional<Long> lastProviderGeneration,
        Optional<Instant> importedAt) {
    public PlayerStageState {
        playerId = Objects.requireNonNull(playerId, "player ID");
        stageId = Objects.requireNonNull(stageId, "stage ID");
        if (stateRevision < 0) {
            throw new IllegalArgumentException("Player state revision cannot be negative");
        }
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        stageEnteredAt = Objects.requireNonNull(stageEnteredAt, "stage entered at");
        createdAt = Objects.requireNonNull(createdAt, "created at");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
        lastReconciledAt = Objects.requireNonNull(lastReconciledAt, "last reconciled at");
        lastProviderGeneration = Objects.requireNonNull(lastProviderGeneration, "last provider generation");
        importedAt = Objects.requireNonNull(importedAt, "imported at");
        lastProviderGeneration.ifPresent(generation -> {
            if (generation < 1) {
                throw new IllegalArgumentException("Last provider generation must be positive");
            }
        });
    }

    public PlayerStageState advanceTo(
            StageId targetStage,
            ConfigRevisionId targetConfigRevision,
            long providerGeneration,
            Instant now) {
        return advanceTo(targetStage, targetConfigRevision, Optional.of(providerGeneration), now);
    }

    public PlayerStageState advanceTo(
            StageId targetStage,
            ConfigRevisionId targetConfigRevision,
            Optional<Long> providerGeneration,
            Instant now) {
        return new PlayerStageState(playerId, targetStage, Math.addExact(stateRevision, 1), targetConfigRevision,
                now, createdAt, now, Optional.of(now), providerGeneration, importedAt);
    }
}
