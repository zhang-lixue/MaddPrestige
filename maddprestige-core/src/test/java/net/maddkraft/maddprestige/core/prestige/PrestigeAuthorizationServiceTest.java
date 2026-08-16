package net.maddkraft.maddprestige.core.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.phase4.PrestigeConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PrestigeLimit;
import net.maddkraft.maddprestige.core.config.phase4.ResetComponent;
import net.maddkraft.maddprestige.core.config.phase4.ResetPreservePolicy;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.LatchKey;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementLatch;
import net.maddkraft.maddprestige.core.requirement.RequirementStateReader;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.Test;

class PrestigeAuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase4-test");
    private static final StageId ORIGIN = new StageId("origin_stage");
    private static final StageId SUMMIT = new StageId("summit_stage");

    @Test
    void destructiveSimulationIsExactAndZeroMutationForArbitraryStages() {
        Fixture fixture = fixture(PrestigeLimit.finite(3), 1, SUMMIT, ORIGIN);
        PlayerStageState beforeStage = fixture.stageState;
        PlayerPrestigeState beforePrestige = fixture.prestigeState;

        PrestigePlan plan = fixture.authorize().plan().orElseThrow();

        assertTrue(plan.executionAllowed());
        assertEquals(SUMMIT, plan.simulation().sourceStage());
        assertEquals(ORIGIN, plan.simulation().resetStage());
        assertEquals(1, plan.simulation().currentPrestigeBefore());
        assertEquals(2, plan.simulation().currentPrestigeAfter());
        assertEquals(4, plan.simulation().lifetimePrestigeBefore());
        assertEquals(5, plan.simulation().lifetimePrestigeAfter());
        assertEquals("RESET", plan.simulation().componentConsequences().stream()
                .filter(value -> value.component() == ResetComponent.PROGRESSION_STAGE)
                .findFirst().orElseThrow().disposition().name());
        assertFalse(plan.simulation().prestigeScopeBefore().equals(plan.simulation().prestigeScopeAfter()));
        assertEquals(beforeStage, fixture.stageState);
        assertEquals(beforePrestige, fixture.prestigeState);
    }

    @Test
    void finiteCapBoundaryAndUnlimitedModeWork() {
        assertTrue(fixture(PrestigeLimit.finite(2), 1, SUMMIT, ORIGIN)
                .authorize().plan().orElseThrow().executionAllowed());
        assertTrue(fixture(PrestigeLimit.finite(1), 1, SUMMIT, ORIGIN)
                .authorize().rejection().orElseThrow().contains("maximum"));
        assertTrue(fixture(PrestigeLimit.unlimited(), 5000, SUMMIT, ORIGIN)
                .authorize().plan().orElseThrow().executionAllowed());
        assertTrue(fixture(PrestigeLimit.unlimited(), Long.MAX_VALUE, SUMMIT, ORIGIN)
                .authorize().rejection().orElseThrow().contains("maximum"));
    }

    @Test
    void counterAndRevisionOverflowBlockBeforeExecutableAuthority() {
        assertTrue(fixture(PrestigeLimit.unlimited(), 0, Long.MAX_VALUE, 7, 3, SUMMIT, ORIGIN)
                .authorize().rejection().orElseThrow().contains("Lifetime"));
        assertTrue(fixture(PrestigeLimit.unlimited(), 0, 4, Long.MAX_VALUE, 3, SUMMIT, ORIGIN)
                .authorize().rejection().orElseThrow().contains("stage state revision"));
        assertTrue(fixture(PrestigeLimit.unlimited(), 0, 4, 7, Long.MAX_VALUE, SUMMIT, ORIGIN)
                .authorize().rejection().orElseThrow().contains("Prestige state revision"));
    }

    @Test
    void unknownOrIneligibleConfiguredStagesFailClosed() {
        assertTrue(fixture(PrestigeLimit.unlimited(), 0, ORIGIN, ORIGIN)
                .authorize().rejection().orElseThrow().contains("ineligible"));
        assertTrue(fixture(PrestigeLimit.unlimited(), 0, SUMMIT, new StageId("missing_stage"))
                .authorize().rejection().orElseThrow().contains("unknown"));
    }

    @Test
    void reconstructedPlanCannotReuseAuthority() {
        PrestigePlan original = fixture(PrestigeLimit.unlimited(), 0, SUMMIT, ORIGIN)
                .authorize().plan().orElseThrow();
        OperationPlan operation = original.operationPlan();
        OperationPlan changed = new OperationPlan(operation.id(), operation.operationType(), operation.actor(),
                operation.target(), operation.expectedStateRevision(), operation.configRevision(),
                operation.providerGenerations(), operation.idempotencyKey(), operation.actions(), "changed preview");
        PrestigePlan reconstructed = new PrestigePlan(original.operationId(), original.playerId(),
                original.expectedStageRevision(), original.expectedPrestigeRevision(), original.configRevision(),
                original.providerGenerations(), original.simulation(), original.costs(), original.rewards(),
                original.rankProviderId(), original.rankProjectionRequest(), original.unavailableProviders(),
                original.blockers(), original.executionAllowed(), changed, original.authorization());

        assertFalse(reconstructed.authorization().matches(reconstructed));
        assertTrue(original.authorization().matches(original));
    }

    private static Fixture fixture(
            PrestigeLimit limit,
            long currentPrestige,
            StageId requiredStage,
            StageId resetStage) {
        return fixture(limit, currentPrestige, Math.max(4, currentPrestige), 7, 3, requiredStage, resetStage);
    }

    private static Fixture fixture(
            PrestigeLimit limit,
            long currentPrestige,
            long lifetimePrestige,
            long stageRevision,
            long prestigeRevision,
            StageId requiredStage,
            StageId resetStage) {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PlayerStageState stageState = new PlayerStageState(playerId, SUMMIT, stageRevision, REVISION,
                NOW.minusSeconds(60),
                NOW.minusSeconds(600), NOW.minusSeconds(60), Optional.empty(), Optional.empty(), Optional.empty());
        PlayerPrestigeState prestigeState = new PlayerPrestigeState(playerId, currentPrestige,
                lifetimePrestige, prestigeRevision, REVISION, new ScopeId("prestige-current"), Optional.empty(),
                NOW.minusSeconds(600), NOW.minusSeconds(60));
        StageDefinition origin = new StageDefinition(ORIGIN, true, "Origin", Map.of(), StageProjection.none());
        StageDefinition summit = new StageDefinition(SUMMIT, true, "Summit", Map.of(), StageProjection.none());
        StageConfiguration stages = new StageConfiguration(2, true, Map.of(ORIGIN, origin, SUMMIT, summit),
                List.of(ORIGIN, SUMMIT), Optional.of(ORIGIN), ReconciliationPolicy.WARN_ONLY);
        PhaseThreeConfiguration phaseThree = new PhaseThreeConfiguration(3, 16, Map.of(), Map.of(), Map.of(),
                Map.of(), CommandActionPolicy.safeDefaults());
        PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(requiredStage), resetStage, 1, 1,
                limit, Duration.ZERO, Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                ResetPreservePolicy.safeDefaults(), false);
        PhaseFourConfiguration phaseFour = new PhaseFourConfiguration(4, prestige, Map.of(), Map.of(), Map.of(),
                Map.of(), CompetitionConfiguration.disabled());
        ActiveStageConfiguration prior = new ActiveStageConfiguration(new StageConfigurationSnapshot(REVISION, stages),
                new PhaseThreeConfigurationSnapshot(REVISION, phaseThree, Map.of()));
        ActivePhaseFourConfiguration active = new ActivePhaseFourConfiguration(prior,
                new PhaseFourConfigurationSnapshot(REVISION, phaseFour, Map.of()));
        ProviderRegistry providers = new ProviderRegistry();
        RequirementStateReader requirementStates = new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                return Optional.empty();
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                return Optional.empty();
            }
        };
        PrestigeAuthorizationService service = new PrestigeAuthorizationService(() -> Optional.of(active),
                ignored -> Optional.of(stageState), ignored -> Optional.of(prestigeState),
                (ignored, stage, state, configuration) -> new PrestigeProgressContext(playerId, REVISION,
                        currentPrestige, ExactDecimal.ZERO, new ScopeContext(Map.of(
                                MeasurementScope.SINCE_PRESTIGE_START, state.prestigeScope()))),
                requirementStates, providers, (ignored, currency) -> ExactDecimal.ZERO,
                (ignored, milestone, key) -> false, ActiveSeasonContext::none,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(playerId, stageState, prestigeState, service);
    }

    private record Fixture(
            UUID playerId,
            PlayerStageState stageState,
            PlayerPrestigeState prestigeState,
            PrestigeAuthorizationService service) {
        private PrestigeAuthorizationResult authorize() {
            return service.authorize(new PrestigeIntent(new Actor("player", Optional.of(playerId), "player"),
                    playerId, "prestige-click-1")).toCompletableFuture().join();
        }
    }
}
