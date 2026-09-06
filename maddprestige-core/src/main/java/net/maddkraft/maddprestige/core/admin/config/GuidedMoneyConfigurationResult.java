package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record GuidedMoneyConfigurationResult(
        ConfigRevisionId previousRevision,
        ConfigRevisionId newRevision,
        long prestigeLevel,
        String previousAmount,
        String newAmount) {
    public GuidedMoneyConfigurationResult {
        previousRevision = Objects.requireNonNull(previousRevision, "previous revision");
        newRevision = Objects.requireNonNull(newRevision, "new revision");
        previousAmount = Objects.requireNonNull(previousAmount, "previous amount");
        newAmount = Objects.requireNonNull(newAmount, "new amount");
        if (prestigeLevel < 1 || previousRevision.equals(newRevision)) {
            throw new IllegalArgumentException("A guided Money result requires a positive level and new revision");
        }
    }
}
