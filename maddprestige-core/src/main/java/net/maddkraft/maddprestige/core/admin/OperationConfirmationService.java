package net.maddkraft.maddprestige.core.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

/** In-memory, login-session-bound authority for consequential player operations. */
public final class OperationConfirmationService implements AutoCloseable {
    public static final Duration DEFAULT_MAXIMUM_LIFETIME = Duration.ofHours(12);
    private static final Duration MAXIMUM_LIFETIME = Duration.ofDays(7);

    private final OperationPreviewService previews;
    private final RankUpPlanExecutor rankUpExecutor;
    private final PrestigePlanExecutor prestigeExecutor;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final Supplier<Duration> maximumLifetime;
    private final Clock clock;
    private final Map<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerSessions = new ConcurrentHashMap<>();

    public OperationConfirmationService(
            OperationPreviewService previews,
            RankUpPlanExecutor rankUpExecutor,
            PrestigePlanExecutor prestigeExecutor,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Duration maximumLifetime,
            Clock clock) {
        this(previews, rankUpExecutor, prestigeExecutor, activeRevision,
                () -> Objects.requireNonNull(maximumLifetime, "maximum confirmation lifetime"), clock);
    }

    public OperationConfirmationService(
            OperationPreviewService previews,
            RankUpPlanExecutor rankUpExecutor,
            PrestigePlanExecutor prestigeExecutor,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Supplier<Duration> maximumLifetime,
            Clock clock) {
        this.previews = Objects.requireNonNull(previews, "preview service");
        this.rankUpExecutor = Objects.requireNonNull(rankUpExecutor, "rank-up executor");
        this.prestigeExecutor = Objects.requireNonNull(prestigeExecutor, "Prestige executor");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.maximumLifetime = Objects.requireNonNull(maximumLifetime, "maximum confirmation lifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        requireLifetime(this.maximumLifetime.get());
    }

    /** Starts a new authoritative login session and invalidates any confirmation from an earlier session. */
    public UUID beginPlayerSession(UUID playerId) {
        UUID player = Objects.requireNonNull(playerId, "player ID");
        invalidatePlayer(player);
        UUID sessionId = UUID.randomUUID();
        playerSessions.put(player, sessionId);
        return sessionId;
    }

    /** Ends the current login session and immediately removes its interactive confirmation state. */
    public void endPlayerSession(UUID playerId) {
        UUID player = Objects.requireNonNull(playerId, "player ID");
        playerSessions.remove(player);
        invalidatePlayer(player);
    }

    public CompletionStage<PreparedConfirmation> prepareRankUp(PermissionSubject subject, UUID playerId) {
        return previews.authorizeRankUp(subject, playerId).thenApply(plan -> store(subject, plan));
    }

    public CompletionStage<PreparedConfirmation> preparePrestige(PermissionSubject subject, UUID playerId) {
        return previews.authorizePrestige(subject, playerId).thenApply(plan -> store(subject, plan));
    }

    /** Returns only current, actor-owned confirmation IDs; no other player's token can be discovered. */
    public List<UUID> validConfirmationIds(PermissionSubject subject) {
        Objects.requireNonNull(subject, "subject");
        pruneInvalid();
        return confirmations.entrySet().stream()
                .filter(entry -> sameActor(entry.getValue().actor(), subject.actor()))
                .filter(entry -> sessionMatches(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    public CompletionStage<OperationExecutionResult> confirmOnly(PermissionSubject subject) {
        List<UUID> ids = validConfirmationIds(subject);
        if (ids.isEmpty()) {
            throw new AdministrationException("confirmation.none_pending",
                    "There is no valid confirmation in this login session.",
                    "Request a fresh Prestige preview, then use its Confirm action.");
        }
        if (ids.size() != 1) {
            throw new AdministrationException("confirmation.ambiguous",
                    "More than one confirmation is available for this actor.",
                    "Choose the intended confirmation ID using tab completion.", "count", ids.size());
        }
        return confirm(subject, ids.getFirst());
    }

    public CompletionStage<OperationExecutionResult> confirm(PermissionSubject subject, UUID confirmationId) {
        Objects.requireNonNull(subject, "subject");
        UUID id = Objects.requireNonNull(confirmationId, "confirmation ID");
        Confirmation confirmation = confirmations.get(id);
        if (confirmation == null) {
            throw unknown();
        }
        if (!sameActor(confirmation.actor(), subject.actor())) {
            throw new AdministrationException("confirmation.actor_mismatch",
                    "A confirmation cannot be transferred to another actor.",
                    "The intended actor must generate a fresh preview.");
        }
        requireCurrentSession(id, confirmation);
        if (!Instant.now(clock).isBefore(confirmation.expiresAt())) {
            confirmations.remove(id, confirmation);
            throw new AdministrationException("confirmation.expired",
                    "The operation preview exceeded its configured maximum safety lifetime.",
                    "Generate a fresh preview so current state and providers are revalidated.");
        }
        ConfigRevisionId revision = confirmation.revision();
        if (activeRevision.get().filter(revision::equals).isEmpty()) {
            confirmations.remove(id, confirmation);
            throw new AdministrationException("confirmation.config_stale",
                    "The active configuration changed after this preview was generated.",
                    "Generate a fresh preview under the current revision.");
        }
        if (confirmation.plan() instanceof RankUpPlan rankPlan) {
            requireExecution(subject, rankPlan.playerId(), PhaseSixPermissions.RANK_UP);
            consume(id, confirmation);
            throw new AdministrationException("rankup.compatibility_only",
                    "Rank-up confirmations are compatibility-only and cannot execute.",
                    "Use the numeric Prestige operation; active progression is Prestige N to N + 1.");
        }
        PrestigePlan approved = (PrestigePlan) confirmation.plan();
        requireExecution(subject, approved.playerId(), PhaseSixPermissions.PRESTIGE);
        consume(id, confirmation);
        return previews.authorizePrestige(subject, approved.playerId())
                .handle((fresh, failure) -> requireEquivalentFreshPlan(confirmation, approved, fresh, failure))
                .thenCompose(prestigeExecutor::execute)
                .thenApply(result -> {
                    if (result.status() == PrestigeExecutionStatus.UNAUTHORIZED
                            || result.status() == PrestigeExecutionStatus.STALE_CONFIGURATION
                            || result.status() == PrestigeExecutionStatus.STALE_GENERATION) {
                        throw revalidationFailed();
                    }
                    return new OperationExecutionResult(result.operationId(), result.status().name(), result.detail());
                });
    }

    public Duration maximumLifetime() {
        return requireLifetime(maximumLifetime.get());
    }

    private synchronized PreparedConfirmation store(PermissionSubject subject, RankUpPlan plan) {
        pruneInvalid();
        supersede(subject.actor());
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(maximumLifetime());
        confirmations.put(id, new Confirmation(subject.actor(), plan, plan.configRevision(), expiresAt,
                sessionFor(subject.actor())));
        return new PreparedConfirmation(id, OperationPreviewService.rankUpPreview(plan), expiresAt);
    }

    private synchronized PreparedConfirmation store(PermissionSubject subject, PrestigePlan plan) {
        pruneInvalid();
        supersede(subject.actor());
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(maximumLifetime());
        confirmations.put(id, new Confirmation(subject.actor(), plan, plan.configRevision(), expiresAt,
                sessionFor(subject.actor())));
        return new PreparedConfirmation(id, OperationPreviewService.prestigePreview(plan), expiresAt);
    }

    private PrestigePlan requireEquivalentFreshPlan(
            Confirmation confirmation,
            PrestigePlan approved,
            PrestigePlan fresh,
            Throwable failure) {
        requireCurrentSession(null, confirmation);
        if (failure != null) {
            throw revalidationFailed(root(failure));
        }
        if (!equivalentOperation(approved, Objects.requireNonNull(fresh, "fresh Prestige plan"))) {
            throw revalidationFailed();
        }
        return fresh;
    }

    private static boolean equivalentOperation(PrestigePlan approved, PrestigePlan fresh) {
        var before = approved.simulation();
        var after = fresh.simulation();
        return approved.playerId().equals(fresh.playerId())
                && approved.configRevision().equals(fresh.configRevision())
                && approved.expectedStageRevision() == fresh.expectedStageRevision()
                && approved.expectedPrestigeRevision() == fresh.expectedPrestigeRevision()
                && before.currentPrestigeBefore() == after.currentPrestigeBefore()
                && before.currentPrestigeAfter() == after.currentPrestigeAfter()
                && before.lifetimePrestigeBefore() == after.lifetimePrestigeBefore()
                && before.lifetimePrestigeAfter() == after.lifetimePrestigeAfter()
                && approved.costs().stream().map(value -> value.definition()).toList()
                        .equals(fresh.costs().stream().map(value -> value.definition()).toList())
                && approved.rewards().stream().map(value -> value.definition()).toList()
                        .equals(fresh.rewards().stream().map(value -> value.definition()).toList())
                && before.componentConsequences().equals(after.componentConsequences())
                && before.currencyChanges().equals(after.currencyChanges())
                && before.milestoneConsequences().equals(after.milestoneConsequences())
                && approved.rankProjectionRequest().equals(fresh.rankProjectionRequest());
    }

    private static void requireExecution(PermissionSubject subject, UUID playerId, String selfPermission) {
        if (subject.actor().uuid().filter(playerId::equals).isPresent()) {
            subject.require(selfPermission);
        } else {
            subject.require(PhaseSixPermissions.EXECUTE);
        }
    }

    private void consume(UUID id, Confirmation confirmation) {
        if (!confirmations.remove(id, confirmation)) {
            throw new AdministrationException("confirmation.already_used",
                    "This confirmation was already consumed by another request.",
                    "Generate a fresh preview; confirmation tokens are single-use.");
        }
    }

    private void supersede(Actor actor) {
        confirmations.entrySet().removeIf(entry -> sameActor(entry.getValue().actor(), actor));
    }

    private void invalidatePlayer(UUID playerId) {
        confirmations.entrySet().removeIf(entry ->
                entry.getValue().actor().uuid().filter(playerId::equals).isPresent());
    }

    private Optional<UUID> sessionFor(Actor actor) {
        return actor.uuid().map(playerId -> playerSessions.computeIfAbsent(playerId, ignored -> UUID.randomUUID()));
    }

    private boolean sessionMatches(Confirmation confirmation) {
        return confirmation.sessionId().map(session -> confirmation.actor().uuid()
                .map(player -> session.equals(playerSessions.get(player))).orElse(false)).orElse(true);
    }

    private void requireCurrentSession(UUID id, Confirmation confirmation) {
        if (!sessionMatches(confirmation)) {
            if (id != null) {
                confirmations.remove(id, confirmation);
            }
            throw new AdministrationException("confirmation.session_ended",
                    "This confirmation belongs to a login session that has ended.",
                    "Request a fresh Prestige preview in the current login session.");
        }
    }

    private void pruneInvalid() {
        Instant now = Instant.now(clock);
        Optional<ConfigRevisionId> revision = activeRevision.get();
        confirmations.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt())
                || revision.filter(entry.getValue().revision()::equals).isEmpty()
                || !sessionMatches(entry.getValue()));
    }

    private static boolean sameActor(Actor left, Actor right) {
        if (left.uuid().isPresent() && right.uuid().isPresent()) {
            return left.uuid().orElseThrow().equals(right.uuid().orElseThrow());
        }
        return left.equals(right);
    }

    private static Duration requireLifetime(Duration value) {
        Duration lifetime = Objects.requireNonNull(value, "maximum confirmation lifetime");
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException("Maximum confirmation lifetime must be positive and at most seven days");
        }
        return lifetime;
    }

    private static AdministrationException unknown() {
        return new AdministrationException("confirmation.unknown",
                "Unknown, expired, replaced, logged-out, or already-used confirmation.",
                "Generate a fresh preview in the current login session.");
    }

    private static AdministrationException revalidationFailed() {
        return revalidationFailed(null);
    }

    private static AdministrationException revalidationFailed(Throwable ignored) {
        return new AdministrationException("confirmation.revalidation_failed",
                "The approved operation is no longer safely executable after final revalidation.",
                "Request and review a fresh Prestige preview before confirming again.");
    }

    private static Throwable root(Throwable failure) {
        Throwable current = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public void close() {
        confirmations.clear();
        playerSessions.clear();
    }

    private record Confirmation(
            Actor actor,
            Object plan,
            ConfigRevisionId revision,
            Instant expiresAt,
            Optional<UUID> sessionId) {
    }
}
