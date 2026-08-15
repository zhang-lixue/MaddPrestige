package net.maddkraft.maddprestige.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;

public interface PlayerStageRepository {
    Optional<PlayerStageState> find(UUID playerId);

    void insert(PlayerStageState state);

    boolean importOnce(PlayerStageState state);

    void update(PlayerStageState replacement, long expectedRevision);

    Map<StageId, Long> countByStage();
}
