package net.maddkraft.maddprestige.core.legacy;

import java.util.Objects;
import java.util.UUID;

public record LegacyPlayerStageRecord(UUID playerId, String legacyValue) {
    public LegacyPlayerStageRecord {
        playerId = Objects.requireNonNull(playerId, "player ID");
        legacyValue = Objects.requireNonNull(legacyValue, "legacy value");
        if (legacyValue.isBlank()) {
            throw new IllegalArgumentException("Legacy player stage value cannot be blank");
        }
    }
}
