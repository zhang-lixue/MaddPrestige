package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record GuidedTotalSkillLevelResult(
        ConfigRevisionId previousRevision,
        ConfigRevisionId newRevision,
        long prestigeLevel,
        String previousTarget,
        String newTarget) {
    public GuidedTotalSkillLevelResult {
        previousRevision = Objects.requireNonNull(previousRevision, "previous revision");
        newRevision = Objects.requireNonNull(newRevision, "new revision");
        previousTarget = Objects.requireNonNull(previousTarget, "previous target");
        newTarget = Objects.requireNonNull(newTarget, "new target");
        if (prestigeLevel < 1) {
            throw new IllegalArgumentException("Prestige level must be positive");
        }
    }
}
