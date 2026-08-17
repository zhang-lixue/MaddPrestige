package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;

public final class ManualPrestigeAdministrationService {
    private final PrestigeAdministrationStore store;
    private final Supplier<ConfigRevisionId> activeRevision;
    private final Executor worker;

    public ManualPrestigeAdministrationService(
            PrestigeAdministrationStore store,
            Supplier<ConfigRevisionId> activeRevision,
            Executor worker) {
        this.store = Objects.requireNonNull(store, "store");
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.worker = Objects.requireNonNull(worker, "worker executor");
    }

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
        return CompletableFuture.supplyAsync(() -> store.adjust(adjustment), worker);
    }
}
