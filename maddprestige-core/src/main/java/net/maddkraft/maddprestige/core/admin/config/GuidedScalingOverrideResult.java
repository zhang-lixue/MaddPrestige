package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Applied result of exactly one explicit per-Prestige override addition, edit, or structural removal. */
public record GuidedScalingOverrideResult(
        ConfigRevisionId previousRevision,
        ConfigRevisionId newRevision,
        long prestigeLevel,
        Optional<String> previousOverride,
        Optional<String> newOverride) {
    public GuidedScalingOverrideResult {
        previousRevision = Objects.requireNonNull(previousRevision, "previous revision");
        newRevision = Objects.requireNonNull(newRevision, "new revision");
        previousOverride = Objects.requireNonNull(previousOverride, "previous override");
        newOverride = Objects.requireNonNull(newOverride, "new override");
        if (prestigeLevel < 1 || previousOverride.stream().anyMatch(String::isEmpty)
                || newOverride.stream().anyMatch(String::isEmpty)
                || previousOverride.isEmpty() && newOverride.isEmpty()
                || previousRevision.equals(newRevision)) {
            throw new IllegalArgumentException("An override result requires a positive level and new revision");
        }
    }

    public boolean removal() {
        return previousOverride.isPresent() && newOverride.isEmpty();
    }

    public boolean addition() {
        return previousOverride.isEmpty() && newOverride.isPresent();
    }
}
