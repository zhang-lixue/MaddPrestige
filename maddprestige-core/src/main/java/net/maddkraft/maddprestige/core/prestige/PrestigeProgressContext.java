package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;

public record PrestigeProgressContext(
        UUID playerId,
        ConfigRevisionId activeConfigRevision,
        long scalingIndex,
        ExactDecimal catchUpPosition,
        ScopeContext scopes) {
    public PrestigeProgressContext {
        playerId = Objects.requireNonNull(playerId, "player ID");
        activeConfigRevision = Objects.requireNonNull(activeConfigRevision, "active configuration revision");
        if (scalingIndex < 0) {
            throw new IllegalArgumentException("Scaling index cannot be negative");
        }
        catchUpPosition = Objects.requireNonNull(catchUpPosition, "catch-up position");
        scopes = Objects.requireNonNull(scopes, "scopes");
    }
}
