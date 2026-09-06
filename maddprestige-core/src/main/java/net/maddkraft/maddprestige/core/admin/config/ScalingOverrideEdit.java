package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;

/** One schema-owned, lossless per-Prestige scaling override edit. */
public record ScalingOverrideEdit(
        String scalingPath,
        int segmentIndex,
        long prestigeLevel,
        String multiplier) {
    public ScalingOverrideEdit {
        scalingPath = Objects.requireNonNull(scalingPath, "scaling path");
        multiplier = Objects.requireNonNull(multiplier, "multiplier");
        if (scalingPath.isBlank() || segmentIndex < 0 || prestigeLevel < 1) {
            throw new IllegalArgumentException("Scaling override requires a path, segment, and positive level");
        }
    }
}
