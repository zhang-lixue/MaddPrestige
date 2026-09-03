package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Objects;
import java.util.UUID;

/** Bounded platform-supplied identity for one known, currently inspectable player. */
public record StaffPlayerIdentity(UUID playerId, String name, boolean online) {
    public StaffPlayerIdentity {
        playerId = Objects.requireNonNull(playerId, "player ID");
        name = Objects.requireNonNull(name, "player name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
    }

    public StaffPlayerIdentity(UUID playerId, String name) {
        this(playerId, name, true);
    }
}
