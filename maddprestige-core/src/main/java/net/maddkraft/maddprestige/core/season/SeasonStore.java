package net.maddkraft.maddprestige.core.season;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;

public interface SeasonStore extends ActiveSeasonSource {
    SeasonRecord start(SeasonDefinition definition, ScopeId scopeId, ConfigRevisionId revision, Instant now);

    SeasonRecord endAndArchive(SeasonId seasonId, Instant now);

    Optional<SeasonRecord> find(SeasonId seasonId);

    List<SeasonRecord> history(int limit);

    void enterPlayer(UUID playerId, SeasonId seasonId, List<RequirementBaseline> baselines, Instant now);

    void setPlayerProgress(UUID playerId, SeasonId seasonId, ExactDecimal progress, Instant now);

    ExactDecimal playerProgress(UUID playerId, SeasonId seasonId);
}
