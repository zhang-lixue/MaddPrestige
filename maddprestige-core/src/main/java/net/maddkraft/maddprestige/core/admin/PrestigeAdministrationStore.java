package net.maddkraft.maddprestige.core.admin;

import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;

@FunctionalInterface
public interface PrestigeAdministrationStore {
    PlayerPrestigeState adjust(ManualPrestigeAdjustment adjustment);

    default Optional<PlayerPrestigeState> find(UUID playerId) {
        return Optional.empty();
    }
}
