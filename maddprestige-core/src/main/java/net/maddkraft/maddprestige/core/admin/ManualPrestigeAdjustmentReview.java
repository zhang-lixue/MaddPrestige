package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Server-owned immutable review authority for one administrative Prestige adjustment. */
public record ManualPrestigeAdjustmentReview(
        UUID reviewId,
        ManualPrestigeAdjustmentKind kind,
        UUID playerId,
        long currentPrestige,
        long targetPrestige,
        long expectedStateRevision,
        ConfigRevisionId configRevision) {
    public ManualPrestigeAdjustmentReview {
        reviewId = Objects.requireNonNull(reviewId, "review ID");
        kind = Objects.requireNonNull(kind, "adjustment kind");
        playerId = Objects.requireNonNull(playerId, "player ID");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        if (currentPrestige < 0 || targetPrestige < 0 || expectedStateRevision < 0
                || currentPrestige == targetPrestige) {
            throw new IllegalArgumentException("Administrative Prestige review is inconsistent");
        }
    }
}
