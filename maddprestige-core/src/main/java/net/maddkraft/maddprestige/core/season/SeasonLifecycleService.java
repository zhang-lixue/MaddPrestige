package net.maddkraft.maddprestige.core.season;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.core.requirement.BaselineInitializationService;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;

public final class SeasonLifecycleService {
    private final SeasonStore store;
    private final BaselineInitializationService baselines;
    private final Clock clock;

    public SeasonLifecycleService(SeasonStore store, BaselineInitializationService baselines, Clock clock) {
        this.store = Objects.requireNonNull(store, "season store");
        this.baselines = Objects.requireNonNull(baselines, "baseline service");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SeasonRecord start(SeasonDefinition definition, ConfigRevisionId revision) {
        ScopeId scope = new ScopeId("season-" + UUID.randomUUID());
        return store.start(definition, scope, revision, clock.instant());
    }

    public SeasonRecord endAndArchive(net.maddkraft.maddprestige.api.id.SeasonId seasonId) {
        return store.endAndArchive(seasonId, clock.instant());
    }

    public List<RequirementBaseline> enterPlayer(
            UUID playerId,
            SeasonRecord season,
            List<RequirementDefinition> requirements,
            Map<RequirementId, MetricSample> samples,
            Map<ProviderId, Long> providerGenerations) {
        if (season.state() != SeasonLifecycleState.ACTIVE) {
            throw new IllegalStateException("Cannot enter an inactive season");
        }
        List<RequirementBaseline> prepared = baselines.prepareScope(playerId,
                MeasurementScope.SINCE_SEASON_START, season.scopeId(), requirements, samples, providerGenerations);
        store.enterPlayer(playerId, season.id(), prepared, clock.instant());
        return prepared;
    }
}
