package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;

public record ManualPrestigeAdjustment(
        UUID playerId,
        long expectedStateRevision,
        long currentPrestige,
        long lifetimePrestige,
        ConfigRevisionId configRevision,
        Actor actor,
        String sourceSurface,
        String reason) {
    public ManualPrestigeAdjustment {
        playerId = Objects.requireNonNull(playerId, "player ID");
        if (expectedStateRevision < 0 || currentPrestige < 0 || lifetimePrestige < currentPrestige) {
            throw new IllegalArgumentException("Manual Prestige counters/revision are inconsistent");
        }
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        actor = Objects.requireNonNull(actor, "actor");
        sourceSurface = Objects.requireNonNull(sourceSurface, "source surface");
        reason = Objects.requireNonNull(reason, "reason");
        if (reason.isBlank() || reason.length() > 512 || sourceSurface.isBlank()) {
            throw new IllegalArgumentException("Manual adjustment requires source and a 1-512 character reason");
        }
    }
}
