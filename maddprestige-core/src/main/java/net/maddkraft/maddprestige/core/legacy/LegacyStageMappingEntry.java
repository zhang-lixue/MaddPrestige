package net.maddkraft.maddprestige.core.legacy;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;

public record LegacyStageMappingEntry(String legacyValue, StageId targetStage) {
    public LegacyStageMappingEntry {
        legacyValue = Objects.requireNonNull(legacyValue, "legacy value");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        if (legacyValue.isBlank()) {
            throw new IllegalArgumentException("Legacy mapping source cannot be blank");
        }
    }
}
