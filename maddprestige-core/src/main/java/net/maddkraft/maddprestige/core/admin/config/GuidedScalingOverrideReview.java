package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Sealed, revision-bound review for one explicit per-Prestige override addition, edit, or removal. */
public record GuidedScalingOverrideReview(
        UUID reviewId,
        ConfigRevisionId baseRevision,
        long prestigeLevel,
        Optional<String> currentOverride,
        Optional<String> newOverride,
        Optional<String> currentEffectiveValue,
        Optional<String> newEffectiveValue,
        Instant expiresAt) {
    public GuidedScalingOverrideReview {
        reviewId = Objects.requireNonNull(reviewId, "review ID");
        baseRevision = Objects.requireNonNull(baseRevision, "base revision");
        currentOverride = Objects.requireNonNull(currentOverride, "current override");
        newOverride = Objects.requireNonNull(newOverride, "new override");
        currentEffectiveValue = Objects.requireNonNull(currentEffectiveValue, "current effective value");
        newEffectiveValue = Objects.requireNonNull(newEffectiveValue, "new effective value");
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        if (prestigeLevel < 1 || currentOverride.stream().anyMatch(String::isEmpty)
                || newOverride.stream().anyMatch(String::isEmpty)
                || currentOverride.isEmpty() && newOverride.isEmpty()
                || currentEffectiveValue.stream().anyMatch(String::isEmpty)
                || newEffectiveValue.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("Override review requires a positive level and complete values");
        }
    }

    public boolean removal() {
        return currentOverride.isPresent() && newOverride.isEmpty();
    }

    public boolean addition() {
        return currentOverride.isEmpty() && newOverride.isPresent();
    }
}
