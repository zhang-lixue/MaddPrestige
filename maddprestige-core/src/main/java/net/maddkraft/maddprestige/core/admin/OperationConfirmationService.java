package net.maddkraft.maddprestige.core.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

public final class OperationConfirmationService {
    private final OperationPreviewService previews;
    private final RankUpPlanExecutor rankUpExecutor;
    private final PrestigePlanExecutor prestigeExecutor;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final Duration lifetime;
    private final Clock clock;
    private final Map<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();

    public OperationConfirmationService(
            OperationPreviewService previews,
            RankUpPlanExecutor rankUpExecutor,
            PrestigePlanExecutor prestigeExecutor,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Duration lifetime,
            Clock clock) {
        this.previews = Objects.requireNonNull(previews, "preview service");
        this.rankUpExecutor = Objects.requireNonNull(rankUpExecutor, "rank-up executor");
        this.prestigeExecutor = Objects.requireNonNull(prestigeExecutor, "Prestige executor");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.lifetime = Objects.requireNonNull(lifetime, "confirmation lifetime");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Confirmation lifetime must be between zero and ten minutes");
        }
    }

    public CompletionStage<PreparedConfirmation> prepareRankUp(PermissionSubject subject, UUID playerId) {
        return previews.authorizeRankUp(subject, playerId).thenApply(plan -> store(subject, plan));
    }

    public CompletionStage<PreparedConfirmation> preparePrestige(PermissionSubject subject, UUID playerId) {
        return previews.authorizePrestige(subject, playerId).thenApply(plan -> store(subject, plan));
    }

    public CompletionStage<OperationExecutionResult> confirm(PermissionSubject subject, UUID confirmationId) {
        Objects.requireNonNull(subject, "subject");
        UUID id = Objects.requireNonNull(confirmationId, "confirmation ID");
        Confirmation confirmation = confirmations.get(id);
        if (confirmation == null) {
            throw new AdministrationException("confirmation.unknown", "Unknown, expired, or already-used confirmation.",
                    "Generate a fresh preview; confirmation tokens are single-use.");
        }
        if (!confirmation.actor().equals(subject.actor())) {
            throw new AdministrationException("confirmation.actor_mismatch",
                    "A confirmation cannot be transferred to another actor.",
                    "The intended actor must generate a fresh preview.");
        }
        if (!Instant.now(clock).isBefore(confirmation.expiresAt())) {
            confirmations.remove(id, confirmation);
            throw new AdministrationException("confirmation.expired", "The operation preview expired.",
                    "Generate a fresh preview so current state and providers are revalidated.");
        }
        ConfigRevisionId revision = confirmation.revision();
        if (!activeRevision.get().filter(revision::equals).isPresent()) {
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
        PrestigePlan prestigePlan = (PrestigePlan) confirmation.plan();
        requireExecution(subject, prestigePlan.playerId(), PhaseSixPermissions.PRESTIGE);
        consume(id, confirmation);
        return prestigeExecutor.execute(prestigePlan).thenApply(result -> new OperationExecutionResult(
                result.operationId(), result.status().name(), result.detail()));
    }

    private PreparedConfirmation store(PermissionSubject subject, RankUpPlan plan) {
        pruneExpired();
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(lifetime);
        confirmations.put(id, new Confirmation(subject.actor(), plan, plan.configRevision(), expiresAt));
        return new PreparedConfirmation(id, OperationPreviewService.rankUpPreview(plan), expiresAt);
    }

    private PreparedConfirmation store(PermissionSubject subject, PrestigePlan plan) {
        pruneExpired();
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(lifetime);
        confirmations.put(id, new Confirmation(subject.actor(), plan, plan.configRevision(), expiresAt));
        return new PreparedConfirmation(id, OperationPreviewService.prestigePreview(plan), expiresAt);
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

    private void pruneExpired() {
        Instant now = Instant.now(clock);
        confirmations.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record Confirmation(
            net.maddkraft.maddprestige.api.operation.Actor actor,
            Object plan,
            ConfigRevisionId revision,
            Instant expiresAt) {
    }
}
