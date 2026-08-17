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
        if (enabled && (requiredStage.isEmpty() || resetStage.isEmpty())) {
            throw new IllegalArgumentException("Enabled setup Prestige requires eligibility and reset stages");
        }
    }

    public static SetupPrestige disabled() {
        return new SetupPrestige(false, Optional.empty(), Optional.empty());
    }
}
