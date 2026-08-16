package net.maddkraft.maddprestige.core.plan;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;

/** Trusted progression/lifecycle inputs resolved by engine composition, never by a rank-up caller. */
public record RankUpProgressContext(
        UUID playerId,
        ConfigRevisionId activeConfigRevision,
        long scalingIndex,
        ExactDecimal catchUpPosition,
        ScopeContext scopes) {
    public RankUpProgressContext {
        playerId = Objects.requireNonNull(playerId, "player ID");
        activeConfigRevision = Objects.requireNonNull(activeConfigRevision, "active configuration revision");
        if (scalingIndex < 0) {
            throw new IllegalArgumentException("Scaling index cannot be negative");
        }
        catchUpPosition = Objects.requireNonNull(catchUpPosition, "catch-up position");
        if (catchUpPosition.asBigDecimal().signum() < 0) {
            throw new IllegalArgumentException("Catch-up position cannot be negative");
        }
        scopes = Objects.requireNonNull(scopes, "scopes");
    }
}
