package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record GuidedRewardConfigurationReview(
        UUID reviewId,
        ConfigRevisionId baseRevision,
        long prestigeLevel,
        String currentAmount,
        String newAmount,
        Instant expiresAt) {
    public GuidedRewardConfigurationReview {
        reviewId = Objects.requireNonNull(reviewId, "review ID");
        baseRevision = Objects.requireNonNull(baseRevision, "base revision");
        currentAmount = Objects.requireNonNull(currentAmount, "current amount");
        newAmount = Objects.requireNonNull(newAmount, "new amount");
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        if (prestigeLevel < 1) {
            throw new IllegalArgumentException("Prestige level must be positive");
        }
    }
}
