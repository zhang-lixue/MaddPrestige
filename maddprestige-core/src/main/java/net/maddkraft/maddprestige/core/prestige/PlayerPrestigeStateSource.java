package net.maddkraft.maddprestige.core.prestige;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface PlayerPrestigeStateSource {
    Optional<PlayerPrestigeState> find(UUID playerId);
}
