package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;

/** One narrowly selected scalar inside a canonical scaling segment. */
public record ScalingParameterEdit(
        String scalingPath,
        int segmentIndex,
        GuidedScalingParameter parameter,
        String value) {
    public ScalingParameterEdit {
        scalingPath = Objects.requireNonNull(scalingPath, "scaling path");
        parameter = Objects.requireNonNull(parameter, "parameter");
        value = Objects.requireNonNull(value, "value");
        if (scalingPath.isBlank() || segmentIndex < 0 || value.isEmpty()) {
            throw new IllegalArgumentException("Scaling parameter edit requires a path, segment, and value");
        }
    }
}
