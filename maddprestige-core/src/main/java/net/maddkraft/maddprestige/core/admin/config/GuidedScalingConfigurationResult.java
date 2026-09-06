package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Applied result of exactly one guided scaling parameter edit. */
public record GuidedScalingConfigurationResult(
        ConfigRevisionId previousRevision,
        ConfigRevisionId newRevision,
        long prestigeLevel,
        GuidedScalingParameter parameter,
        String previousValue,
        String newValue) {
    public GuidedScalingConfigurationResult {
        previousRevision = Objects.requireNonNull(previousRevision, "previous revision");
        newRevision = Objects.requireNonNull(newRevision, "new revision");
        parameter = Objects.requireNonNull(parameter, "parameter");
        previousValue = Objects.requireNonNull(previousValue, "previous value");
        newValue = Objects.requireNonNull(newValue, "new value");
        if (prestigeLevel < 1 || previousRevision.equals(newRevision)) {
            throw new IllegalArgumentException("A scaling result requires a positive level and new revision");
        }
    }
}
