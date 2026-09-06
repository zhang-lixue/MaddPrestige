package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record GuidedRewardConfigurationResult(
        ConfigRevisionId previousRevision,
        ConfigRevisionId newRevision,
        long prestigeLevel,
        String previousAmount,
        String newAmount) {
    public GuidedRewardConfigurationResult {
        previousRevision = Objects.requireNonNull(previousRevision, "previous revision");
        newRevision = Objects.requireNonNull(newRevision, "new revision");
        previousAmount = Objects.requireNonNull(previousAmount, "previous amount");
        newAmount = Objects.requireNonNull(newAmount, "new amount");
        if (prestigeLevel < 1) {
            throw new IllegalArgumentException("Prestige level must be positive");
        }
    }
}
