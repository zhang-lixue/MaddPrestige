package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageProjectionChange(
        StageId stageId,
        Optional<StageProjection> oldProjection,
        Optional<StageProjection> newProjection) {
    public StageProjectionChange {
        stageId = Objects.requireNonNull(stageId, "stage ID");
        oldProjection = Objects.requireNonNull(oldProjection, "old projection");
        newProjection = Objects.requireNonNull(newProjection, "new projection");
    }
}
