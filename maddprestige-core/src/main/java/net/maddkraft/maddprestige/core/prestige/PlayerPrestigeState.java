package net.maddkraft.maddprestige.core.prestige;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;

public record PlayerPrestigeState(
        UUID playerId,
        long currentPrestige,
        long lifetimePrestige,
        long stateRevision,
        ConfigRevisionId configRevision,
        ScopeId prestigeScope,
        Optional<Instant> lastPrestigedAt,
        Instant createdAt,
        Instant updatedAt) {
    public PlayerPrestigeState {
        playerId = Objects.requireNonNull(playerId, "player ID");
        if (currentPrestige < 0 || lifetimePrestige < 0 || stateRevision < 0
                || lifetimePrestige < currentPrestige) {
            throw new IllegalArgumentException("Prestige counters/revision are inconsistent");
        }
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        prestigeScope = Objects.requireNonNull(prestigeScope, "Prestige scope");
        lastPrestigedAt = Objects.requireNonNull(lastPrestigedAt, "last Prestige time");
        createdAt = Objects.requireNonNull(createdAt, "created time");
        updatedAt = Objects.requireNonNull(updatedAt, "updated time");
    }

    public PlayerPrestigeState advance(
            long currentIncrement,
            long lifetimeIncrement,
            ConfigRevisionId activeRevision,
            ScopeId newScope,
            Instant now) {
        return new PlayerPrestigeState(playerId, Math.addExact(currentPrestige, currentIncrement),
                Math.addExact(lifetimePrestige, lifetimeIncrement), Math.addExact(stateRevision, 1),
                activeRevision, newScope, Optional.of(now), createdAt, now);
    }
}
