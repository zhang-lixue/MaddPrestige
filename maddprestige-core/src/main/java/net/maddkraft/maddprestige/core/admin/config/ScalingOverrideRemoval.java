package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;

/** One schema-owned, lossless structural removal of an explicit per-Prestige scaling override. */
public record ScalingOverrideRemoval(
        String scalingPath,
        int segmentIndex,
        long prestigeLevel) {
    public ScalingOverrideRemoval {
        scalingPath = Objects.requireNonNull(scalingPath, "scaling path");
        if (scalingPath.isBlank() || segmentIndex < 0 || prestigeLevel < 1) {
            throw new IllegalArgumentException("Scaling override removal requires a path, segment, and positive level");
        }
    }
}
