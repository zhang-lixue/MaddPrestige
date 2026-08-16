package net.maddkraft.maddprestige.core.prestige;

import java.util.UUID;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;

@FunctionalInterface
public interface PrestigeProgressContextSource {
    PrestigeProgressContext load(
            UUID playerId,
            PlayerStageState stageState,
            PlayerPrestigeState prestigeState,
            ActivePhaseFourConfiguration configuration);
}
