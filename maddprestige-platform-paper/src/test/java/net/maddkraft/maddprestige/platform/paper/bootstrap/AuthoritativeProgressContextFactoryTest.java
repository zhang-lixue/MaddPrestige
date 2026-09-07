package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.lifecycle.ActiveLifecycleConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfigurationSnapshot;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.season.SeasonStore;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthoritativeProgressContextFactoryTest {
    private static final Instant NOW = Instant.parse("2026-08-17T20:00:00Z");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("authoritative_revision");

    @Test
    @DisplayName("[OR8B-01] Live stage, Prestige, season, scaling, catch-up, and scopes compose rank authorization")
    void rankUpUsesOnlyDurableLifecycleInputs() {
        UUID playerId = UUID.fromString("45ba5aac-001e-4ab5-8d1f-e6fe03a69476");
        PlayerStageState stage = stage(playerId);
        PlayerPrestigeState prestige = prestige(playerId);
        SeasonStore seasons = activeSeason(playerId);
        AuthoritativeProgressContextFactory factory = new AuthoritativeProgressContextFactory(
                ignored -> Optional.of(prestige), seasons);

        var context = factory.rankUp(playerId, stage, activeStages());

        assertEquals(7, context.scalingIndex());
        assertEquals(ExactDecimal.parse("19.25"), context.catchUpPosition());
        assertEquals(REVISION, context.activeConfigRevision());
        assertEquals(AuthoritativeProgressContextFactory.stageScope(stage),
                context.scopes().instance(MeasurementScope.SINCE_STAGE_START).orElseThrow());
        assertEquals(prestige.prestigeScope(),
                context.scopes().instance(MeasurementScope.SINCE_PRESTIGE_START).orElseThrow());
        assertEquals(new ScopeId("season_scope"),
                context.scopes().instance(MeasurementScope.SINCE_SEASON_START).orElseThrow());
        assertTrue(context.scopes().instance(MeasurementScope.ABSOLUTE).isPresent());
        assertTrue(context.scopes().instance(MeasurementScope.LIFETIME).isPresent());
    }

    @Test
    @DisplayName("[OR8B-01] Prestige authorization uses live Prestige scaling and has no synthetic season scope")
    void prestigeUsesDurableScalingAndInactiveSeason() {
        UUID playerId = UUID.fromString("4ae32039-6e52-4b00-ac23-afbd3832994e");
        PlayerPrestigeState prestige = prestige(playerId);
        SeasonStore seasons = mock(SeasonStore.class);
        when(seasons.active()).thenReturn(ActiveSeasonContext.none());
        AuthoritativeProgressContextFactory factory = new AuthoritativeProgressContextFactory(
                ignored -> Optional.of(prestige), seasons);

        var context = factory.prestige(playerId, prestige, activeLifecycle());

        assertEquals(7, context.scalingIndex());
        assertEquals(ExactDecimal.ZERO, context.catchUpPosition());
        assertTrue(context.scopes().instance(MeasurementScope.SINCE_STAGE_START).isEmpty());
        assertTrue(context.scopes().instance(MeasurementScope.SINCE_SEASON_START).isEmpty());
    }

    private static PlayerStageState stage(UUID playerId) {
        return new PlayerStageState(playerId, new StageId("veteran"), 12, REVISION, NOW, NOW.minusSeconds(1000),
                NOW, Optional.of(NOW), Optional.of(4L), Optional.empty());
    }

    private static PlayerPrestigeState prestige(UUID playerId) {
        return new PlayerPrestigeState(playerId, 7, 11, 3, REVISION, new ScopeId("prestige_scope"),
                Optional.of(NOW.minusSeconds(500)), NOW.minusSeconds(2000), NOW);
    }

    private static SeasonStore activeSeason(UUID playerId) {
        SeasonStore seasons = mock(SeasonStore.class);
        SeasonId seasonId = new SeasonId("summer_2026");
        when(seasons.active()).thenReturn(new ActiveSeasonContext(Optional.of(seasonId),
                Optional.of(new ScopeId("season_scope"))));
        when(seasons.playerProgress(playerId, seasonId)).thenReturn(ExactDecimal.parse("19.25"));
        return seasons;
    }

    private static ActiveStageConfiguration activeStages() {
        StageConfigurationSnapshot snapshot = mock(StageConfigurationSnapshot.class);
        when(snapshot.revisionId()).thenReturn(REVISION);
        ActiveStageConfiguration active = mock(ActiveStageConfiguration.class);
        when(active.stages()).thenReturn(snapshot);
        return active;
    }

    private static ActiveLifecycleConfiguration activeLifecycle() {
        LifecycleConfigurationSnapshot snapshot = mock(LifecycleConfigurationSnapshot.class);
        when(snapshot.revisionId()).thenReturn(REVISION);
        ActiveLifecycleConfiguration active = mock(ActiveLifecycleConfiguration.class);
        when(active.lifecycle()).thenReturn(snapshot);
        return active;
    }
}
