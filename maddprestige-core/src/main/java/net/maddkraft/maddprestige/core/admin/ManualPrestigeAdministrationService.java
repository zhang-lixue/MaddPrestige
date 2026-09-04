package net.maddkraft.maddprestige.core.admin;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;

public final class ManualPrestigeAdministrationService {
    private final PrestigeAdministrationStore store;
    private final Supplier<ConfigRevisionId> activeRevision;
    private final Executor worker;
    private final Function<UUID, Optional<String>> playerInitializer;
    private final Supplier<ManualPrestigeAdjustmentPolicy> policy;

    public ManualPrestigeAdministrationService(
            PrestigeAdministrationStore store,
            Supplier<ConfigRevisionId> activeRevision,
            Executor worker) {
        this(store, activeRevision, worker, ignored -> Optional.empty(),
                ManualPrestigeAdjustmentPolicy::unlimited);
    }

    public ManualPrestigeAdministrationService(
            PrestigeAdministrationStore store,
            Supplier<ConfigRevisionId> activeRevision,
            Executor worker,
            Function<UUID, Optional<String>> playerInitializer) {
        this(store, activeRevision, worker, playerInitializer, ManualPrestigeAdjustmentPolicy::unlimited);
    }

    public ManualPrestigeAdministrationService(
            PrestigeAdministrationStore store,
            Supplier<ConfigRevisionId> activeRevision,
            Executor worker,
            Function<UUID, Optional<String>> playerInitializer,
            Supplier<ManualPrestigeAdjustmentPolicy> policy) {
        this.store = Objects.requireNonNull(store, "store");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.worker = Objects.requireNonNull(worker, "worker executor");
        this.playerInitializer = Objects.requireNonNull(playerInitializer, "player initializer");
        this.policy = Objects.requireNonNull(policy, "adjustment policy");
    }

    public CompletionStage<PlayerPrestigeState> set(
            PermissionSubject subject,
            UUID playerId,
            long expectedStateRevision,
            long prestige,
            String sourceSurface,
            String reason) {
        return set(subject, playerId, expectedStateRevision, prestige, prestige, sourceSurface, reason);
    }

    /** Compatibility overload: current and lifetime values must be identical. */
    public CompletionStage<PlayerPrestigeState> set(
            PermissionSubject subject,
            UUID playerId,
            long expectedStateRevision,
            long currentPrestige,
            long lifetimePrestige,
            String sourceSurface,
            String reason) {
        subject.require(PhaseSixPermissions.PLAYER_PRESTIGE_EDIT);
        validateSetTarget(currentPrestige);
        ManualPrestigeAdjustment adjustment = new ManualPrestigeAdjustment(UUID.randomUUID(),
                ManualPrestigeAdjustmentKind.SET, playerId, expectedStateRevision,
                currentPrestige, lifetimePrestige, activeRevision.get(), subject.actor(), sourceSurface, reason);
        return CompletableFuture.supplyAsync(() -> {
            initialize(playerId);
            return store.adjust(adjustment);
        }, worker);
    }

    public CompletionStage<PlayerPrestigeState> inspect(PermissionSubject subject, UUID playerId) {
        subject.require(PhaseSixPermissions.PLAYER_VIEW);
        return CompletableFuture.supplyAsync(() -> state(playerId), worker);
    }

    public CompletionStage<ManualPrestigeAdjustmentReview> reviewSet(
            PermissionSubject subject,
            UUID playerId,
            long targetPrestige) {
        subject.require(PhaseSixPermissions.PLAYER_PRESTIGE_SET);
        validateSetTarget(targetPrestige);
        return review(subject, playerId, targetPrestige, ManualPrestigeAdjustmentKind.SET);
    }

    public CompletionStage<ManualPrestigeAdjustmentReview> reviewSetInput(
            PermissionSubject subject,
            UUID playerId,
            String input) {
        Objects.requireNonNull(input, "Prestige input");
        final long target;
        try {
            target = Long.parseLong(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Prestige must be a whole number", exception);
        }
        return reviewSet(subject, playerId, target);
    }

    public CompletionStage<ManualPrestigeAdjustmentReview> reviewReset(
            PermissionSubject subject,
            UUID playerId) {
        subject.require(PhaseSixPermissions.PLAYER_PRESTIGE_RESET);
        return review(subject, playerId, currentPolicy().resetTarget(), ManualPrestigeAdjustmentKind.RESET);
    }

    public CompletionStage<ManualPrestigeTargetPage> targets(
            PermissionSubject subject,
            UUID playerId,
            int page,
            int pageSize) {
        subject.require(PhaseSixPermissions.PLAYER_PRESTIGE_SET);
        if (page < 0 || pageSize < 1 || pageSize > 7) {
            throw new IllegalArgumentException("Prestige target page is outside supported bounds");
        }
        return CompletableFuture.supplyAsync(() -> {
            PlayerPrestigeState current = state(playerId);
            ManualPrestigeAdjustmentPolicy currentPolicy = currentPolicy();
            long first;
            try {
                first = Math.addExact(Math.multiplyExact((long) page, pageSize), 1L);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Prestige target page is outside supported bounds", exception);
            }
            if (!currentPolicy.allows(first)) {
                throw new IllegalArgumentException("Prestige target page is outside the active supported range");
            }
            ArrayList<ManualPrestigeAdjustmentReview> targets = new ArrayList<>();
            long last = first;
            for (int index = 0; index < pageSize; index++) {
                long target;
                try {
                    target = Math.addExact(first, index);
                } catch (ArithmeticException exception) {
                    break;
                }
                if (!currentPolicy.allows(target)) {
                    break;
                }
                last = target;
                if (target != current.currentPrestige()) {
                    targets.add(new ManualPrestigeAdjustmentReview(UUID.randomUUID(),
                            ManualPrestigeAdjustmentKind.SET, playerId, current.currentPrestige(), target,
                            current.stateRevision(), activeRevision.get()));
                }
            }
            boolean hasNext = last < Long.MAX_VALUE && currentPolicy.allows(last + 1);
            return new ManualPrestigeTargetPage(current.currentPrestige(), page, targets, page > 0, hasNext);
        }, worker);
    }

    public CompletionStage<PlayerPrestigeState> confirm(
            PermissionSubject subject,
            ManualPrestigeAdjustmentReview review,
            String sourceSurface,
            String reason) {
        ManualPrestigeAdjustmentReview authority = Objects.requireNonNull(review, "adjustment review");
        subject.require(authority.kind().permission());
        ConfigRevisionId revision = activeRevision.get();
        if (!revision.equals(authority.configRevision())) {
            throw new IllegalStateException("Active configuration changed after the adjustment review");
        }
        if (authority.kind() == ManualPrestigeAdjustmentKind.SET) {
            validateSetTarget(authority.targetPrestige());
        } else {
            validateTarget(authority.targetPrestige());
        }
        ManualPrestigeAdjustment adjustment = new ManualPrestigeAdjustment(authority.reviewId(), authority.kind(),
                authority.playerId(), authority.expectedStateRevision(), authority.targetPrestige(),
                authority.targetPrestige(), revision, subject.actor(), sourceSurface, reason);
        return CompletableFuture.supplyAsync(() -> store.adjust(adjustment), worker);
    }

    private CompletionStage<ManualPrestigeAdjustmentReview> review(
            PermissionSubject subject,
            UUID playerId,
            long targetPrestige,
            ManualPrestigeAdjustmentKind kind) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(playerId, "player ID");
        return CompletableFuture.supplyAsync(() -> {
            PlayerPrestigeState current = state(playerId);
            if (current.currentPrestige() == targetPrestige) {
                throw new IllegalArgumentException("Administrative Prestige target already equals current Prestige");
            }
            return new ManualPrestigeAdjustmentReview(UUID.randomUUID(), kind, playerId,
                    current.currentPrestige(), targetPrestige, current.stateRevision(), activeRevision.get());
        }, worker);
    }

    private PlayerPrestigeState state(UUID playerId) {
        initialize(playerId);
        return store.find(playerId).orElseThrow(() ->
                new IllegalStateException("Player has no authoritative Prestige state"));
    }

    private void initialize(UUID playerId) {
        Optional<String> initializationFailure = Objects.requireNonNull(
                playerInitializer.apply(playerId), "player initialization result");
        initializationFailure.ifPresent(failure -> {
            throw new IllegalStateException("Player lifecycle establishment failed before Prestige edit: "
                    + failure);
        });
    }

    private void validateTarget(long targetPrestige) {
        if (!currentPolicy().allows(targetPrestige)) {
            throw new IllegalArgumentException("Prestige target is outside the active supported range");
        }
    }

    private void validateSetTarget(long targetPrestige) {
        validateTarget(targetPrestige);
        if (targetPrestige < 1) {
            throw new IllegalArgumentException("Set Prestige target must be positive; use Reset Prestige for zero");
        }
    }

    private ManualPrestigeAdjustmentPolicy currentPolicy() {
        return Objects.requireNonNull(policy.get(), "adjustment policy");
    }
}
