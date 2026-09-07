package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;
import net.maddkraft.maddprestige.core.prestige.PrestigeSimulation;
import net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OperationConfirmationSessionTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("session-confirmation-revision");

    @Test
    @DisplayName("Confirmation remains valid in the login session beyond the former short timer")
    void sessionIsAuthoritativeUntilTheSecondaryCap() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T12:00:00Z"));
        Fixture fixture = new Fixture(clock);
        PrestigePlan approved = plan(PLAYER, 0, 1, 0);
        PrestigePlan fresh = plan(PLAYER, 0, 1, 0);
        fixture.authorize(PLAYER, approved, fresh);
        fixture.confirmations.beginPlayerSession(PLAYER);

        PreparedConfirmation prepared = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();
        clock.advance(Duration.ofMinutes(30));

        assertEquals(List.of(prepared.confirmationId()), fixture.confirmations.validConfirmationIds(fixture.player));
        assertEquals("COMPLETED", fixture.confirmations.confirmOnly(fixture.player)
                .toCompletableFuture().join().status());
    }

    @Test
    @DisplayName("Logout and restart invalidate in-memory confirmation authority")
    void logoutAndRestartInvalidateConfirmations() {
        Fixture fixture = new Fixture(Clock.systemUTC());
        fixture.authorize(PLAYER, plan(PLAYER, 0, 1, 0));
        fixture.confirmations.beginPlayerSession(PLAYER);
        PreparedConfirmation logout = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();

        fixture.confirmations.endPlayerSession(PLAYER);
        assertEquals("confirmation.unknown", assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.player, logout.confirmationId())).code());

        fixture.authorize(PLAYER, plan(PLAYER, 0, 1, 0));
        fixture.confirmations.beginPlayerSession(PLAYER);
        PreparedConfirmation restart = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();
        fixture.confirmations.close();
        assertEquals("confirmation.unknown", assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.player, restart.confirmationId())).code());
    }

    @Test
    @DisplayName("Replacement supersedes the old preview and shorthand selects the replacement")
    void replacementSupersedesPriorConfirmation() {
        Fixture fixture = new Fixture(Clock.systemUTC());
        PrestigePlan firstPlan = plan(PLAYER, 0, 1, 0);
        PrestigePlan secondPlan = plan(PLAYER, 0, 1, 0);
        PrestigePlan fresh = plan(PLAYER, 0, 1, 0);
        fixture.authorize(PLAYER, firstPlan, secondPlan, fresh);
        fixture.confirmations.beginPlayerSession(PLAYER);

        PreparedConfirmation first = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();
        PreparedConfirmation second = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();

        assertEquals("confirmation.unknown", assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.player, first.confirmationId())).code());
        assertEquals(List.of(second.confirmationId()), fixture.confirmations.validConfirmationIds(fixture.player));
        assertEquals("COMPLETED", fixture.confirmations.confirmOnly(fixture.player)
                .toCompletableFuture().join().status());
    }

    @Test
    @DisplayName("Confirmation performs fresh authorization and rejects a changed Prestige target")
    void finalStateIsRevalidatedBeforeExecution() {
        Fixture fixture = new Fixture(Clock.systemUTC());
        fixture.authorize(PLAYER, plan(PLAYER, 0, 1, 4), plan(PLAYER, 1, 2, 5));
        fixture.confirmations.beginPlayerSession(PLAYER);
        PreparedConfirmation prepared = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();

        CompletionException failure = assertThrows(CompletionException.class, () -> fixture.confirmations
                .confirm(fixture.player, prepared.confirmationId()).toCompletableFuture().join());

        assertEquals("confirmation.revalidation_failed", ((AdministrationException) failure.getCause()).code());
        verify(fixture.executor, never()).execute(any());
    }

    @Test
    @DisplayName("Successful confirmations reject replay")
    void successfulConfirmationIsSingleUse() {
        Fixture fixture = new Fixture(Clock.systemUTC());
        fixture.authorize(PLAYER, plan(PLAYER, 0, 1, 0), plan(PLAYER, 0, 1, 0));
        fixture.confirmations.beginPlayerSession(PLAYER);
        PreparedConfirmation prepared = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();

        fixture.confirmations.confirm(fixture.player, prepared.confirmationId()).toCompletableFuture().join();

        assertEquals("confirmation.unknown", assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(fixture.player, prepared.confirmationId())).code());
        verify(fixture.executor).execute(any());
    }

    @Test
    @DisplayName("Valid-ID discovery and consumption remain isolated by player")
    void confirmationIdsArePlayerIsolated() {
        Fixture fixture = new Fixture(Clock.systemUTC());
        PermissionSubject other = subject(OTHER);
        fixture.authorize(PLAYER, plan(PLAYER, 0, 1, 0));
        fixture.authorize(OTHER, plan(OTHER, 0, 1, 0));
        fixture.confirmations.beginPlayerSession(PLAYER);
        fixture.confirmations.beginPlayerSession(OTHER);
        PreparedConfirmation playerConfirmation = fixture.confirmations.preparePrestige(fixture.player, PLAYER)
                .toCompletableFuture().join();
        PreparedConfirmation otherConfirmation = fixture.confirmations.preparePrestige(other, OTHER)
                .toCompletableFuture().join();

        assertEquals(List.of(playerConfirmation.confirmationId()),
                fixture.confirmations.validConfirmationIds(fixture.player));
        assertEquals(List.of(otherConfirmation.confirmationId()), fixture.confirmations.validConfirmationIds(other));
        assertEquals("confirmation.actor_mismatch", assertThrows(AdministrationException.class,
                () -> fixture.confirmations.confirm(other, playerConfirmation.confirmationId())).code());
        assertEquals(List.of(playerConfirmation.confirmationId()),
                fixture.confirmations.validConfirmationIds(fixture.player));
    }

    private static PrestigePlan plan(UUID player, long current, long target, long revision) {
        PrestigeSimulation simulation = mock(PrestigeSimulation.class);
        BoundRequirementEvaluation requirements = mock(BoundRequirementEvaluation.class);
        RequirementEvaluationResult result = mock(RequirementEvaluationResult.class);
        ExplanationNode explanation = new ExplanationNode("root", ExplanationStatus.SATISFIED, "ready",
                Map.of("mode", "ALL"), List.of());
        when(requirements.result()).thenReturn(result);
        when(result.explanation()).thenReturn(explanation);
        when(simulation.currentPrestigeBefore()).thenReturn(current);
        when(simulation.currentPrestigeAfter()).thenReturn(target);
        when(simulation.lifetimePrestigeBefore()).thenReturn(current);
        when(simulation.lifetimePrestigeAfter()).thenReturn(target);
        when(simulation.requirements()).thenReturn(requirements);
        when(simulation.componentConsequences()).thenReturn(List.of());
        when(simulation.currencyChanges()).thenReturn(List.of());
        when(simulation.milestoneConsequences()).thenReturn(List.of());
        when(simulation.providerActions()).thenReturn(List.of());
        when(simulation.uncertainExternalEffects()).thenReturn(List.of());

        PrestigePlan plan = mock(PrestigePlan.class);
        when(plan.playerId()).thenReturn(player);
        when(plan.configRevision()).thenReturn(REVISION);
        when(plan.expectedStageRevision()).thenReturn(revision);
        when(plan.expectedPrestigeRevision()).thenReturn(revision);
        when(plan.simulation()).thenReturn(simulation);
        when(plan.costs()).thenReturn(List.of());
        when(plan.rewards()).thenReturn(List.of());
        when(plan.rankProjectionRequest()).thenReturn(Optional.empty());
        when(plan.providerGenerations()).thenReturn(Map.of());
        when(plan.blockers()).thenReturn(List.of());
        when(plan.authorizationBlockers()).thenReturn(List.of());
        when(plan.executionAllowed()).thenReturn(true);
        return plan;
    }

    private static PermissionSubject subject(UUID playerId) {
        return new PermissionSubject(new Actor("player", Optional.of(playerId), playerId.toString()),
                Set.of(AdministrationPermissions.PRESTIGE));
    }

    private static final class Fixture {
        private final OperationPreviewService previews = mock(OperationPreviewService.class);
        private final PrestigePlanExecutor executor = mock(PrestigePlanExecutor.class);
        private final PermissionSubject player = subject(PLAYER);
        private final OperationConfirmationService confirmations;

        private Fixture(Clock clock) {
            when(executor.execute(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                    new PrestigeExecutionResult(OperationId.random(), PrestigeExecutionStatus.COMPLETED, "done")));
            confirmations = new OperationConfirmationService(previews,
                    ignored -> CompletableFuture.failedFuture(new AssertionError("rank-up is inactive")), executor,
                    () -> Optional.of(REVISION), Duration.ofHours(12), clock);
        }

        private void authorize(UUID playerId, PrestigePlan... plans) {
            @SuppressWarnings("unchecked")
            CompletableFuture<PrestigePlan>[] stages = java.util.Arrays.stream(plans)
                    .map(CompletableFuture::completedFuture).toArray(CompletableFuture[]::new);
            when(previews.authorizePrestige(any(PermissionSubject.class), eq(playerId)))
                    .thenReturn(stages[0], java.util.Arrays.copyOfRange(stages, 1, stages.length));
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
