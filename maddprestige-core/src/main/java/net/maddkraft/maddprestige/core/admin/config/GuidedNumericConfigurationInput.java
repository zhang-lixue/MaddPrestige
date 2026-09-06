package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Revision-bound initial state for one reusable guided numeric input. */
public record GuidedNumericConfigurationInput(
        ConfigRevisionId revision,
        long prestigeLevel,
        String currentValue) {
    public GuidedNumericConfigurationInput {
        revision = Objects.requireNonNull(revision, "revision");
        currentValue = Objects.requireNonNull(currentValue, "current value");
        if (prestigeLevel < 1 || currentValue.isEmpty()) {
            throw new IllegalArgumentException("Numeric input requires a positive Prestige level and current value");
        }
    }
}
