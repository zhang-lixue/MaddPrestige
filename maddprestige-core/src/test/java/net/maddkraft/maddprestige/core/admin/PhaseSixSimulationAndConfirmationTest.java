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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationService;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
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

class PhaseSixSimulationAndConfirmationTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("simulation_revision");

    @Test
    @DisplayName("[A26][Phase6] Command/GUI preview service reuses authorization and performs zero mutation")
    void simulationUsesCanonicalPlanWithoutExecution() {
        Fixture fixture = new Fixture();
        PlayerStageState before = fixture.state;

        var preview = fixture.previews.simulateRankUp(fixture.self, fixture.playerId)
                .toCompletableFuture().join();

        assertTrue(preview.executable());
        assertEquals("first → second", preview.stateChange());
        assertEquals(REVISION, preview.configRevision());
        assertEquals(before, fixture.state);
        assertEquals(1, fixture.authorizationReads.get());
        assertEquals(0, fixture.executions.get());
    }

    @Test
    @DisplayName("[A27][A56] Simulation permission does not imply execution permission")
    void separatesSimulationAndExecutionAuthority() {
        Fixture fixture = new Fixture();
        PermissionSubject simulator = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()),
                "Simulator"), Set.of(PhaseSixPermissions.SIMULATE));

        assertTrue(fixture.previews.simulateRankUp(simulator, fixture.playerId)
                .toCompletableFuture().join().executable());
        assertThrows(AdministrationException.class, () -> fixture.confirmations
                .prepareRankUp(simulator, fixture.playerId));
        assertEquals(0, fixture.executions.get());
    }

    @Test
    @DisplayName("[A27] Confirmation is actor-bound, single-use, revision-bound, and executes once")
    void confirmationRevalidatesAuthorityAndStaleness() {
        Fixture fixture = new Fixture();
        var stale = fixture.confirmations.prepareRankUp(fixture.self, fixture.playerId)
                .toCompletableFuture().join();
        fixture.activeRevision.set(Optional.of(new ConfigRevisionId("changed_revision")));

        AdministrationException staleFailure = assertThrows(AdministrationException.class, () ->
                fixture.confirmations.confirm(fixture.self, stale.confirmationId()));
        assertEquals("confirmation.config_stale", staleFailure.code());
        assertEquals(0, fixture.executions.get());

        fixture.activeRevision.set(Optional.of(REVISION));
        var current = fixture.confirmations.prepareRankUp(fixture.self, fixture.playerId)
                .toCompletableFuture().join();
        var result = fixture.confirmations.confirm(fixture.self, current.confirmationId())
                .toCompletableFuture().join();
        assertEquals("COMPLETED", result.status());
        assertEquals(1, fixture.executions.get());
        assertThrows(AdministrationException.class, () -> fixture.confirmations
                .confirm(fixture.self, current.confirmationId()));
        assertEquals(1, fixture.executions.get());
    }

    @Test
    @DisplayName("[A27][Phase6-security] Wrong actor cannot destroy another actor's confirmation")
    void wrongActorDoesNotConsumeConfirmation() {
        Fixture fixture = new Fixture();
        var prepared = fixture.confirmations.prepareRankUp(fixture.self, fixture.playerId)
                .toCompletableFuture().join();
        PermissionSubject intruder = new PermissionSubject(new Actor("staff", Optional.of(UUID.randomUUID()),
                "Intruder"), Set.of(PhaseSixPermissions.EXECUTE));

        AdministrationException mismatch = assertThrows(AdministrationException.class, () ->
                fixture.confirmations.confirm(intruder, prepared.confirmationId()));
        assertEquals("confirmation.actor_mismatch", mismatch.code());
        assertEquals("COMPLETED", fixture.confirmations.confirm(fixture.self, prepared.confirmationId())
                .toCompletableFuture().join().status());
        assertEquals(1, fixture.executions.get());
    }

    @Test
    @DisplayName("[A27][Phase6-security] Concurrent confirmation has exactly one consuming winner")
    void concurrentConfirmationExecutesExactlyOnce() {
        Fixture fixture = new Fixture();
        var prepared = fixture.confirmations.prepareRankUp(fixture.self, fixture.playerId)
                .toCompletableFuture().join();
        CountDownLatch gate = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> confirmAfter(gate, fixture, prepared.confirmationId()));
        var second = CompletableFuture.supplyAsync(() -> confirmAfter(gate, fixture, prepared.confirmationId()));

        gate.countDown();

        assertEquals(1, java.util.stream.Stream.of(first.join(), second.join()).filter(Boolean::booleanValue).count());
        assertEquals(1, fixture.executions.get());
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
            assertTrue(Set.of("confirmation.already_used", "confirmation.unknown").contains(exception.code()));
            return false;
        }
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
                new Actor("player", Optional.of(playerId), "Player"), Set.of(PhaseSixPermissions.RANK_UP));
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
                    new PhaseThreeConfigurationSnapshot(REVISION, PhaseThreeConfiguration.empty(), Map.of()));
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
}
