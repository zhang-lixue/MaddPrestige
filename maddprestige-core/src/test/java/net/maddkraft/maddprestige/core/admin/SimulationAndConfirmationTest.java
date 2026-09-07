package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationSnapshot;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationService;
import net.maddkraft.maddprestige.core.plan.RankUpIntent;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.plan.RankUpProgressContext;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.LatchKey;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementLatch;
import net.maddkraft.maddprestige.core.requirement.RequirementStateReader;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationAndConfirmationTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("simulation_revision");

    @Test
    @DisplayName("[A26]Rank preview is blocked before authorization or mutation")
    void simulationUsesCanonicalPlanWithoutExecution() {
        Fixture fixture = new Fixture();
        PlayerStageState before = fixture.state;

        CompletionException wrapped = assertThrows(CompletionException.class,
                () -> fixture.previews.simulateRankUp(fixture.self, fixture.playerId).toCompletableFuture().join());

        assertEquals("rankup.compatibility_only", ((AdministrationException) wrapped.getCause()).code());
        assertEquals(before, fixture.state);
        assertEquals(0, fixture.authorizationReads.get());
        assertEquals(0, fixture.executions.get());
    }

    @Test
    @DisplayName("[A47][A68][OR8D-10] Real no-plan simulation retains exact authorization blockers")
    void realNoPlanSimulationRetainsStructuredAuthorizationBlockers() {
        UUID player = UUID.randomUUID();
        PermissionSubject self = new PermissionSubject(new Actor("player", Optional.of(player), "Player"),
                Set.of(AdministrationPermissions.RANK_UP));
        RankUpAuthorizationService unavailable = new RankUpAuthorizationService(
                () -> Optional.empty(),
                ignored -> Optional.empty(),
                (ignored, state, active) -> {
                    throw new AssertionError("inactive snapshot must reject before context load");
                }, Fixture.emptyRequirementState(), new ProviderRegistry(), CLOCK);
        OperationPreviewService previews = new OperationPreviewService(unavailable::authorize,
                ignored -> CompletableFuture.completedFuture(PrestigeAuthorizationResult.rejected(
                        net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker.of(
                                net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                        .PRESTIGE_DISABLED,
                                "diagnostic"))));

        CompletionException wrapped = assertThrows(CompletionException.class,
                () -> previews.simulateRankUp(self, player).toCompletableFuture().join());
        AdministrationException rejected = (AdministrationException) wrapped.getCause();

        assertEquals("rankup.compatibility_only", rejected.code());
        assertTrue(rejected.authorizationBlockers().isEmpty());
    }

    @Test
    @DisplayName("[A47][OR8D-08] Real RankUp boundary exposes exact inactive, stale-target, and cost identities")
    void realRankUpBoundaryExposesExactStructuredRejections() {
        UUID player = UUID.randomUUID();
        PlayerStageState state = new PlayerStageState(player, new StageId("first"), 0, REVISION,
                NOW.minusSeconds(60), NOW.minusSeconds(60), NOW.minusSeconds(60), Optional.empty(),
                Optional.empty(), Optional.empty());

        var inactive = authorize(rankUpConfiguration(false, false, false), state, Optional.empty());
        var staleTarget = authorize(rankUpConfiguration(true, true, false), state,
                Optional.of(new StageId("third")));
        var missingCost = authorize(rankUpConfiguration(true, false, true), state, Optional.empty());

        assertEquals(net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.STAGE_LADDER_INACTIVE,
                inactive.authorizationBlockers().getFirst().kind());
        assertEquals(net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.ILLEGAL_OR_STALE_TARGET,
                staleTarget.authorizationBlockers().getFirst().kind());
        assertEquals("third", staleTarget.authorizationBlockers().getFirst().facts().get("intended_target"));
        assertEquals(net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.UNKNOWN_CONFIGURED_COST,
                missingCost.authorizationBlockers().getFirst().kind());
        assertEquals("missing_cost",
                missingCost.authorizationBlockers().getFirst().facts().get("configured_cost"));
    }

    @Test
    @DisplayName("[A47][A68][OR8D-08] Real cost preflight retains provider detail as structured facts")
    void realCostPreflightRetainsStructuredDetail() {
        UUID player = UUID.randomUUID();
        StageId firstId = new StageId("first");
        StageId secondId = new StageId("second");
        CostId costId = new CostId("coins");
        net.maddkraft.maddprestige.api.id.ProviderId providerId =
                new net.maddkraft.maddprestige.api.id.ProviderId("vault");
        BlockingCostProvider provider = new BlockingCostProvider(providerId);
        ProviderRegistry registry = new ProviderRegistry();
        var registration = registry.register("test", provider);
        registry.activate(registration);
        var cost = new net.maddkraft.maddprestige.api.cost.CostDefinition(costId, providerId, "debit",
                net.maddkraft.maddprestige.api.metric.MetricValue.decimal("25"), Map.of(), "Twenty-five coins");
        StageDefinition first = new StageDefinition(firstId, true, "First", Map.of(), StageProjection.none());
        StageDefinition second = new StageDefinition(secondId, true, "Second", Map.of(), StageProjection.none(),
                Optional.empty(), List.of(costId), List.of());
        StageConfiguration stages = new StageConfiguration(3, true, Map.of(firstId, first, secondId, second),
                List.of(firstId, secondId), Optional.of(firstId), ReconciliationPolicy.WARN_ONLY);
        ProgressionConfiguration progression = new ProgressionConfiguration(3, 16, Map.of(), Map.of(),
                Map.of(costId, cost), Map.of(),
                net.maddkraft.maddprestige.core.command.CommandActionPolicy.safeDefaults());
        ActiveStageConfiguration active = new ActiveStageConfiguration(
                new StageConfigurationSnapshot(REVISION, stages),
                new ProgressionConfigurationSnapshot(REVISION, progression,
                        Map.of(providerId, registration.generation())));
        PlayerStageState state = new PlayerStageState(player, firstId, 0, REVISION, NOW.minusSeconds(60),
                NOW.minusSeconds(60), NOW.minusSeconds(60), Optional.empty(), Optional.empty(), Optional.empty());
        RankUpAuthorizationService service = new RankUpAuthorizationService(() -> Optional.of(active),
                ignored -> Optional.of(state), (ignored, authoritative, configuration) -> new RankUpProgressContext(
                        player, REVISION, 0, ExactDecimal.ZERO, new ScopeContext(Map.of())),
                Fixture.emptyRequirementState(), registry, CLOCK);

        var plan = service.authorize(new RankUpIntent(new Actor("player", Optional.of(player), "Player"), player,
                Optional.empty(), "cost-preflight-test")).toCompletableFuture().join().plan().orElseThrow();
        var blocker = plan.authorizationBlockers().getFirst();

        assertEquals(net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.COST_PREFLIGHT_BLOCKED,
                blocker.kind());
        assertEquals(Map.of("id", "coins", "provider", "vault", "amount", "25", "type", "debit",
                "status", "BLOCKED", "detail", "Insufficient exact balance"), blocker.facts());
    }

    @Test
    @DisplayName("[A27][A56] Simulation permission does not imply execution permission")
    void separatesSimulationAndExecutionAuthority() {
        Fixture fixture = new Fixture();
        PermissionSubject simulator = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()),
                "Simulator"), Set.of(AdministrationPermissions.SIMULATE));

        assertThrows(CompletionException.class, () -> fixture.previews.simulateRankUp(simulator, fixture.playerId)
                .toCompletableFuture().join());
        assertThrows(AdministrationException.class, () -> fixture.confirmations
                .prepareRankUp(simulator, fixture.playerId));
        assertEquals(0, fixture.executions.get());
    }

    @Test
    @DisplayName("Player-view permission enters canonical Prestige inspection without simulate authority")
    void staffInspectionUsesLeastPrivilegeCanonicalPipeline() {
        UUID actorId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        AtomicInteger authorizationReads = new AtomicInteger();
        OperationPreviewService previews = new OperationPreviewService(
                ignored -> CompletableFuture.completedFuture(
                        net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult.rejected("unused")),
                ignored -> {
                    authorizationReads.incrementAndGet();
                    return CompletableFuture.completedFuture(PrestigeAuthorizationResult.rejected("blocked"));
                });
        PermissionSubject viewer = new PermissionSubject(new Actor("player", Optional.of(actorId), "Viewer"),
                Set.of(AdministrationPermissions.PLAYER_VIEW));
        PermissionSubject unprivileged = new PermissionSubject(
                new Actor("player", Optional.of(UUID.randomUUID()), "Player"), Set.of());

        assertThrows(CompletionException.class,
                () -> previews.inspectPrestige(viewer, playerId).toCompletableFuture().join());
        assertEquals(1, authorizationReads.get());
        AdministrationException denied = assertThrows(AdministrationException.class,
                () -> previews.inspectPrestige(unprivileged, playerId));
        assertEquals("permission.denied", denied.code());
        assertEquals(1, authorizationReads.get());
    }

    @Test
    @DisplayName("[A27]Retained rank confirmation is actor/single-use/revision-safe and never executes")
    void confirmationRevalidatesAuthorityAndStaleness() {
        Fixture fixture = new Fixture();
        var stale = retainedRankConfirmation(fixture);
        fixture.activeRevision.set(Optional.of(new ConfigRevisionId("changed_revision")));

        AdministrationException staleFailure = assertThrows(AdministrationException.class, () ->
                fixture.confirmations.confirm(fixture.self, stale.confirmationId()));
        assertEquals("confirmation.config_stale", staleFailure.code());
        assertEquals(0, fixture.executions.get());

        fixture.activeRevision.set(Optional.of(REVISION));
        var current = retainedRankConfirmation(fixture);
        AdministrationException blocked = assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.self, current.confirmationId()));
        assertEquals("rankup.compatibility_only", blocked.code());
        assertEquals(0, fixture.executions.get());
        assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.self, current.confirmationId()));
        assertEquals(0, fixture.executions.get());
    }

    @Test
    @DisplayName("[A27]Wrong actor cannot destroy another actor's confirmation")
    void wrongActorDoesNotConsumeConfirmation() {
        Fixture fixture = new Fixture();
        var prepared = retainedRankConfirmation(fixture);
        PermissionSubject intruder = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()),
                "Intruder"), Set.of(AdministrationPermissions.EXECUTE));

        AdministrationException mismatch = assertThrows(AdministrationException.class, () ->
                fixture.confirmations.confirm(intruder, prepared.confirmationId()));
        assertEquals("confirmation.actor_mismatch", mismatch.code());
        AdministrationException blocked = assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.self, prepared.confirmationId()));
        assertEquals("rankup.compatibility_only", blocked.code());
        assertEquals(0, fixture.executions.get());
    }

    @Test
    @DisplayName("[A27]Concurrent confirmation has exactly one consuming winner")
    void concurrentConfirmationExecutesExactlyOnce() {
        Fixture fixture = new Fixture();
        var prepared = retainedRankConfirmation(fixture);
        CountDownLatch gate = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> confirmAfter(gate, fixture, prepared.confirmationId()));
        var second = CompletableFuture.supplyAsync(() -> confirmAfter(gate, fixture, prepared.confirmationId()));

        gate.countDown();

        assertEquals(1, java.util.stream.Stream.of(first.join(), second.join()).filter(Boolean::booleanValue).count());
        assertEquals(0, fixture.executions.get());
    }

    private static boolean confirmAfter(CountDownLatch gate, Fixture fixture, UUID confirmationId) {
        try {
            gate.await();
            fixture.confirmations.confirm(fixture.self, confirmationId).toCompletableFuture().join();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (AdministrationException exception) {
            if (exception.code().equals("rankup.compatibility_only")) {
                return true;
            }
            assertTrue(Set.of("confirmation.already_used", "confirmation.unknown").contains(exception.code()));
            return false;
        }
    }

    private static PreparedConfirmation retainedRankConfirmation(Fixture fixture) {
        RankUpPlan plan = fixture.authorization.authorize(new RankUpIntent(fixture.self.actor(), fixture.playerId,
                Optional.empty(), "retained-confirmation-test")).toCompletableFuture().join().plan().orElseThrow();
        try {
            var store = OperationConfirmationService.class.getDeclaredMethod(
                    "store", PermissionSubject.class, RankUpPlan.class);
            store.setAccessible(true);
            return (PreparedConfirmation) store.invoke(fixture.confirmations, fixture.self, plan);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not construct retained compatibility confirmation", exception);
        }
    }

    private static net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult authorize(
            ActiveStageConfiguration active,
            PlayerStageState state,
            Optional<StageId> intendedTarget) {
        RankUpAuthorizationService service = new RankUpAuthorizationService(() -> Optional.of(active),
                ignored -> Optional.of(state), (ignored, authoritative, configuration) -> new RankUpProgressContext(
                        state.playerId(), REVISION, 0, ExactDecimal.ZERO, new ScopeContext(Map.of())),
                Fixture.emptyRequirementState(), new ProviderRegistry(), CLOCK);
        return service.authorize(new RankUpIntent(new Actor("player", Optional.of(state.playerId()), "Player"),
                state.playerId(), intendedTarget, "structured-blocker-test")).toCompletableFuture().join();
    }

    private static ActiveStageConfiguration rankUpConfiguration(
            boolean enabled,
            boolean third,
            boolean missingCost) {
        StageId firstId = new StageId("first");
        StageId secondId = new StageId("second");
        StageId thirdId = new StageId("third");
        StageDefinition first = new StageDefinition(firstId, true, "First", Map.of(), StageProjection.none());
        StageDefinition second = new StageDefinition(secondId, true, "Second", Map.of(), StageProjection.none(),
                Optional.empty(), missingCost ? List.of(new CostId("missing_cost")) : List.of(), List.of());
        java.util.LinkedHashMap<StageId, StageDefinition> definitions = new java.util.LinkedHashMap<>();
        definitions.put(firstId, first);
        definitions.put(secondId, second);
        if (third) {
            definitions.put(thirdId,
                    new StageDefinition(thirdId, true, "Third", Map.of(), StageProjection.none()));
        }
        List<StageId> order = third ? List.of(firstId, secondId, thirdId) : List.of(firstId, secondId);
        StageConfiguration stages = new StageConfiguration(3, enabled, definitions, order, Optional.of(firstId),
                ReconciliationPolicy.WARN_ONLY);
        return new ActiveStageConfiguration(new StageConfigurationSnapshot(REVISION, stages),
                new ProgressionConfigurationSnapshot(REVISION, ProgressionConfiguration.empty(), Map.of()));
    }

    private static final class Fixture {
        private final UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private final PlayerStageState state = new PlayerStageState(playerId, new StageId("first"), 4, REVISION,
                NOW.minusSeconds(60), NOW.minusSeconds(600), NOW.minusSeconds(60), Optional.empty(),
                Optional.empty(), Optional.empty());
        private final AtomicInteger authorizationReads = new AtomicInteger();
        private final AtomicInteger executions = new AtomicInteger();
        private final AtomicReference<Optional<ConfigRevisionId>> activeRevision =
                new AtomicReference<>(Optional.of(REVISION));
        private final PermissionSubject self = new PermissionSubject(
                new Actor("player", Optional.of(playerId), "Player"), Set.of(AdministrationPermissions.RANK_UP));
        private final RankUpAuthorizationService authorization = new RankUpAuthorizationService(
                () -> Optional.of(active()), ignored -> {
                    authorizationReads.incrementAndGet();
                    return Optional.of(state);
                }, (ignored, authoritative, active) -> new RankUpProgressContext(playerId, REVISION, 0,
                        ExactDecimal.ZERO, new ScopeContext(Map.of())), emptyRequirementState(),
                new ProviderRegistry(), CLOCK);
        private final OperationPreviewService previews = new OperationPreviewService(authorization::authorize,
                ignored -> CompletableFuture.completedFuture(PrestigeAuthorizationResult.rejected("disabled")));
        private final OperationConfirmationService confirmations = new OperationConfirmationService(previews,
                plan -> {
                    executions.incrementAndGet();
                    return CompletableFuture.completedFuture(new RankUpExecutionResult(plan.operationId(),
                            RankUpExecutionStatus.COMPLETED, "committed"));
                }, plan -> CompletableFuture.completedFuture(new PrestigeExecutionResult(OperationId.random(),
                        PrestigeExecutionStatus.FAILED, "unused")), activeRevision::get,
                Duration.ofMinutes(2), CLOCK);

        private static ActiveStageConfiguration active() {
            StageId firstId = new StageId("first");
            StageId secondId = new StageId("second");
            StageConfiguration stages = new StageConfiguration(3, true,
                    Map.of(firstId, new StageDefinition(firstId, true, "First", Map.of(), StageProjection.none()),
                            secondId, new StageDefinition(secondId, true, "Second", Map.of(), StageProjection.none())),
                    List.of(firstId, secondId), Optional.of(firstId), ReconciliationPolicy.WARN_ONLY);
            return new ActiveStageConfiguration(new StageConfigurationSnapshot(REVISION, stages),
                    new ProgressionConfigurationSnapshot(REVISION, ProgressionConfiguration.empty(), Map.of()));
        }

        private static RequirementStateReader emptyRequirementState() {
            return new RequirementStateReader() {
                @Override
                public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                    return Optional.empty();
                }

                @Override
                public Optional<RequirementLatch> findLatch(LatchKey key) {
                    return Optional.empty();
                }
            };
        }
    }

    private static final class BlockingCostProvider implements net.maddkraft.maddprestige.api.cost.CostProvider {
        private final net.maddkraft.maddprestige.api.provider.ProviderDescriptor descriptor;

        private BlockingCostProvider(net.maddkraft.maddprestige.api.id.ProviderId id) {
            descriptor = new net.maddkraft.maddprestige.api.provider.ProviderDescriptor(id, "test", "1", "1",
                    List.of(), List.of());
        }

        @Override
        public net.maddkraft.maddprestige.api.provider.ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public net.maddkraft.maddprestige.api.provider.ProviderHealth health() {
            return new net.maddkraft.maddprestige.api.provider.ProviderHealth(
                    net.maddkraft.maddprestige.api.provider.ProviderHealthState.AVAILABLE,
                    "available", "ready", NOW);
        }

        @Override
        public net.maddkraft.maddprestige.api.action.ActionCharacteristics characteristics(
                net.maddkraft.maddprestige.api.cost.CostDefinition definition) {
            return new net.maddkraft.maddprestige.api.action.ActionCharacteristics(true, true, true, false);
        }

        @Override
        public net.maddkraft.maddprestige.api.validation.ValidationReport validate(
                net.maddkraft.maddprestige.api.cost.CostDefinition definition) {
            return net.maddkraft.maddprestige.api.validation.ValidationReport.VALID;
        }

        @Override
        public java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.cost.CostPreflight> preflight(
                net.maddkraft.maddprestige.api.cost.PlannedCost proposed) {
            return CompletableFuture.completedFuture(
                    net.maddkraft.maddprestige.api.cost.CostPreflight.blocked("Insufficient exact balance"));
        }

        @Override
        public java.util.concurrent.CompletionStage<net.maddkraft.maddprestige.api.action.ActionExecutionResult> execute(
                net.maddkraft.maddprestige.api.cost.PlannedCost plannedCost) {
            throw new AssertionError("blocked cost cannot execute");
        }
    }
}
