package net.maddkraft.maddprestige.core.admin.setup;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

public record SetupPrestige(
        boolean enabled,
        Optional<StageId> requiredStage,
        Optional<StageId> resetStage) {
    public SetupPrestige {
        requiredStage = Objects.requireNonNull(requiredStage, "required stage");
        resetStage = Objects.requireNonNull(resetStage, "reset stage");
    }

    public static SetupPrestige numericEnabled() {
        return new SetupPrestige(true, Optional.empty(), Optional.empty());
    }

    public static SetupPrestige disabled() {
        return new SetupPrestige(false, Optional.empty(), Optional.empty());
    }
}
