package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.plan.RankUpProgressContext;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.prestige.PrestigeProgressContext;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.season.SeasonStore;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;

/** Resolves trusted requirement inputs exclusively from current durable lifecycle state. */
final class AuthoritativeProgressContextFactory {
    private final Function<UUID, Optional<PlayerPrestigeState>> prestiges;
    private final SeasonStore seasons;

    AuthoritativeProgressContextFactory(
            Function<UUID, Optional<PlayerPrestigeState>> prestiges,
            SeasonStore seasons) {
        this.prestiges = Objects.requireNonNull(prestiges, "Prestige state source");
        this.seasons = Objects.requireNonNull(seasons, "season store");
    }

    RankUpProgressContext rankUp(
            UUID playerId,
            PlayerStageState stage,
            ActiveStageConfiguration active) {
        PlayerPrestigeState prestige = prestiges.apply(playerId).orElseGet(() -> initialPrestige(
                playerId, active.stages().revisionId(), stage.createdAt()));
        SeasonPosition season = seasonPosition(playerId);
        return new RankUpProgressContext(playerId, active.stages().revisionId(), prestige.currentPrestige(),
                season.catchUpPosition(), scopes(playerId, stage, prestige, season.context()));
    }

    PrestigeProgressContext prestige(
            UUID playerId,
            PlayerStageState stage,
            PlayerPrestigeState prestige,
            ActivePhaseFourConfiguration active) {
        SeasonPosition season = seasonPosition(playerId);
        return new PrestigeProgressContext(playerId, active.phaseFour().revisionId(), prestige.currentPrestige(),
                season.catchUpPosition(), scopes(playerId, stage, prestige, season.context()));
    }

    static ScopeId initialPrestigeScope(UUID playerId) {
        return new ScopeId("prestige_" + compact(playerId) + "_0");
    }

    static ScopeId stageScope(PlayerStageState stage) {
        return new ScopeId("stage_" + compact(stage.playerId()) + "_" + stage.stateRevision());
    }

    private SeasonPosition seasonPosition(UUID playerId) {
        ActiveSeasonContext context = seasons.active();
        ExactDecimal position = context.seasonId()
                .map(seasonId -> seasons.playerProgress(playerId, seasonId))
                .orElse(ExactDecimal.ZERO);
        return new SeasonPosition(context, position);
    }

    private static ScopeContext scopes(
            UUID playerId,
            PlayerStageState stage,
            PlayerPrestigeState prestige,
            ActiveSeasonContext season) {
        LinkedHashMap<MeasurementScope, ScopeId> scopes = new LinkedHashMap<>();
        scopes.put(MeasurementScope.ABSOLUTE, new ScopeId("absolute_" + compact(playerId)));
        scopes.put(MeasurementScope.LIFETIME, new ScopeId("lifetime_" + compact(playerId)));
        scopes.put(MeasurementScope.SINCE_STAGE_START, stageScope(stage));
        scopes.put(MeasurementScope.SINCE_PRESTIGE_START, prestige.prestigeScope());
        season.scopeId().ifPresent(scope -> scopes.put(MeasurementScope.SINCE_SEASON_START, scope));
        return new ScopeContext(Map.copyOf(scopes));
    }

    private static PlayerPrestigeState initialPrestige(
            UUID playerId,
            net.maddkraft.maddprestige.api.id.ConfigRevisionId revision,
            java.time.Instant createdAt) {
        return new PlayerPrestigeState(playerId, 0, 0, 0, revision, initialPrestigeScope(playerId),
                Optional.empty(), createdAt, createdAt);
    }

    private static String compact(UUID value) {
        return value.toString().replace("-", "");
    }

    private record SeasonPosition(ActiveSeasonContext context, ExactDecimal catchUpPosition) {
    }
}
