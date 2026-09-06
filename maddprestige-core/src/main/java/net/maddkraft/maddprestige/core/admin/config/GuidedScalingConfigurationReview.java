package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Sealed, revision-bound review for one guided scaling parameter edit. */
public record GuidedScalingConfigurationReview(
        UUID reviewId,
        ConfigRevisionId baseRevision,
        long prestigeLevel,
        GuidedScalingParameter parameter,
        String currentValue,
        String newValue,
        Instant expiresAt) {
    public GuidedScalingConfigurationReview {
        reviewId = Objects.requireNonNull(reviewId, "review ID");
        baseRevision = Objects.requireNonNull(baseRevision, "base revision");
        parameter = Objects.requireNonNull(parameter, "parameter");
        currentValue = Objects.requireNonNull(currentValue, "current value");
        newValue = Objects.requireNonNull(newValue, "new value");
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        if (prestigeLevel < 1 || currentValue.isEmpty() || newValue.isEmpty()) {
            throw new IllegalArgumentException("Scaling review requires a positive level and complete values");
        }
    }
}
