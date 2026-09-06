package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record GuidedTotalSkillLevelReview(
        UUID reviewId,
        ConfigRevisionId baseRevision,
        long prestigeLevel,
        String currentTarget,
        String newTarget,
        Instant expiresAt) {
    public GuidedTotalSkillLevelReview {
        reviewId = Objects.requireNonNull(reviewId, "review ID");
        baseRevision = Objects.requireNonNull(baseRevision, "base revision");
        currentTarget = Objects.requireNonNull(currentTarget, "current target");
        newTarget = Objects.requireNonNull(newTarget, "new target");
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        if (prestigeLevel < 1) {
            throw new IllegalArgumentException("Prestige level must be positive");
        }
    }
}
