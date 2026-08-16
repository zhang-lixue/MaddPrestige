package net.maddkraft.maddprestige.core.plan;

import java.util.UUID;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;

/** Engine-owned boundary for scaling, catch-up, and lifecycle scope identity. */
@FunctionalInterface
public interface RankUpProgressContextSource {
    RankUpProgressContext load(
            UUID playerId,
            PlayerStageState authoritativeState,
            ActiveStageConfiguration activeConfiguration);
}
