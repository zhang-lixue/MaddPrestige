package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageRemapEntry(
        UUID playerId,
        StageId sourceStage,
        StageId targetStage,
        long expectedStateRevision,
        ConfigRevisionId sourceConfigRevision) {
    public StageRemapEntry {
        playerId = Objects.requireNonNull(playerId, "player ID");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        sourceConfigRevision = Objects.requireNonNull(sourceConfigRevision, "source configuration revision");
        if (sourceStage.equals(targetStage)) {
            throw new IllegalArgumentException("Stage remap source and target must differ");
        }
        if (expectedStateRevision < 0) {
            throw new IllegalArgumentException("Expected player state revision cannot be negative");
        }
    }
}
