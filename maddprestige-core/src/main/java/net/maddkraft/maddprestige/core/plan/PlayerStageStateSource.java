package net.maddkraft.maddprestige.core.plan;

import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;

@FunctionalInterface
public interface PlayerStageStateSource {
    Optional<PlayerStageState> find(UUID playerId);
}
