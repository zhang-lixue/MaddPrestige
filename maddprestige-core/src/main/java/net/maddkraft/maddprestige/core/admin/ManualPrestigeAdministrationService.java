package net.maddkraft.maddprestige.core.admin;

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

    public ManualPrestigeAdministrationService(
            PrestigeAdministrationStore store,
            Supplier<ConfigRevisionId> activeRevision,
            Executor worker) {
        this(store, activeRevision, worker, ignored -> Optional.empty());
    }

    public ManualPrestigeAdministrationService(
            PrestigeAdministrationStore store,
            Supplier<ConfigRevisionId> activeRevision,
            Executor worker,
            Function<UUID, Optional<String>> playerInitializer) {
        this.store = Objects.requireNonNull(store, "store");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.worker = Objects.requireNonNull(worker, "worker executor");
        this.playerInitializer = Objects.requireNonNull(playerInitializer, "player initializer");
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
        ManualPrestigeAdjustment adjustment = new ManualPrestigeAdjustment(playerId, expectedStateRevision,
                currentPrestige, lifetimePrestige, activeRevision.get(), subject.actor(), sourceSurface, reason);
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> initializationFailure = Objects.requireNonNull(
                    playerInitializer.apply(playerId), "player initialization result");
            initializationFailure.ifPresent(failure -> {
                throw new IllegalStateException("Player lifecycle establishment failed before Prestige edit: "
                        + failure);
            });
            return store.adjust(adjustment);
        }, worker);
    }
}
