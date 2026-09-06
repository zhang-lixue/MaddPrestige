package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import org.junit.jupiter.api.Test;

class ManualPrestigeAdministrationServiceTest {
    private static final UUID PLAYER = UUID.fromString("d7551bf9-6358-3218-89c4-06c9c57dc879");
    private static final UUID STAFF = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase9f-c1");
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void setUsesMirroredCountersCasAndSingleAuditAuthority() {
        MemoryStore store = new MemoryStore(state(6, 9));
        ManualPrestigeAdministrationService service = service(store, new AtomicReference<>(REVISION),
                new AtomicInteger());
        PermissionSubject staff = staff(PhaseSixPermissions.PLAYER_VIEW,
                PhaseSixPermissions.PLAYER_PRESTIGE_SET);

        ManualPrestigeAdjustmentReview review = service.reviewSetInput(staff, PLAYER, "8")
                .toCompletableFuture().join();
        PlayerPrestigeState updated = service.confirm(staff, review, "staff-gui", "Owner-approved set")
                .toCompletableFuture().join();

        assertEquals(8, updated.currentPrestige());
        assertEquals(8, updated.lifetimePrestige());
        assertEquals(10, updated.stateRevision());
        assertEquals(1, store.adjustments.size());
        assertEquals(ManualPrestigeAdjustmentKind.SET, store.adjustments.getFirst().kind());
        assertThrows(CompletionException.class, () -> service.confirm(staff, review,
                "staff-gui", "Replay").toCompletableFuture().join());
        assertEquals(1, store.adjustments.size());
    }

    @Test
    void resetUsesCanonicalBaselineAndSeparatePermission() {
        MemoryStore store = new MemoryStore(state(6, 2));
        ManualPrestigeAdministrationService service = service(store, new AtomicReference<>(REVISION),
                new AtomicInteger());
        PermissionSubject resetter = staff(PhaseSixPermissions.PLAYER_VIEW,
                PhaseSixPermissions.PLAYER_PRESTIGE_RESET);

        ManualPrestigeAdjustmentReview review = service.reviewReset(resetter, PLAYER)
                .toCompletableFuture().join();
        assertEquals(0, review.targetPrestige());
        PlayerPrestigeState updated = service.confirm(resetter, review, "staff-gui", "Owner-approved reset")
                .toCompletableFuture().join();

        assertEquals(0, updated.currentPrestige());
        assertEquals(0, updated.lifetimePrestige());
        assertEquals(ManualPrestigeAdjustmentKind.RESET, store.adjustments.getFirst().kind());
        assertThrows(AdministrationException.class, () -> service.reviewSet(resetter, PLAYER, 3));
    }

    @Test
    void rejectsInvalidNumericRangeStaleRevisionAndStalePlayerState() {
        MemoryStore store = new MemoryStore(state(6, 4));
        AtomicReference<ConfigRevisionId> revision = new AtomicReference<>(REVISION);
        ManualPrestigeAdministrationService service = service(store, revision, new AtomicInteger());
        PermissionSubject setter = staff(PhaseSixPermissions.PLAYER_VIEW,
                PhaseSixPermissions.PLAYER_PRESTIGE_SET);
        PermissionSubject editor = staff(PhaseSixPermissions.PLAYER_PRESTIGE_EDIT);

        assertThrows(IllegalArgumentException.class, () -> service.set(editor, PLAYER, 4, 0,
                "command", "Zero belongs to Reset Prestige"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, "six"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, "-1"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, "0"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, "+1"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, "1.0"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, "1e1"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER, " 1"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSetInput(setter, PLAYER,
                "9223372036854775808"));
        assertThrows(IllegalArgumentException.class, () -> service.reviewSet(setter, PLAYER, 11));
        assertEquals(10, service.validateSetInput(setter, "10", REVISION));
        assertThrows(IllegalArgumentException.class, () -> service.validateSetInput(setter, "11", REVISION));

        ConfigRevisionId staleInputRevision = REVISION;
        revision.set(new ConfigRevisionId("phase9f-c1-input-replaced"));
        assertThrows(IllegalStateException.class,
                () -> service.validateSetInput(setter, "8", staleInputRevision));
        revision.set(REVISION);

        ManualPrestigeAdjustmentReview forgedZero = new ManualPrestigeAdjustmentReview(UUID.randomUUID(),
                ManualPrestigeAdjustmentKind.SET, PLAYER, 6, 0, 4, REVISION);
        assertThrows(IllegalArgumentException.class, () -> service.confirm(setter, forgedZero,
                "staff-gui", "Forged zero target"));

        ManualPrestigeAdjustmentReview staleRevision = service.reviewSet(setter, PLAYER, 8)
                .toCompletableFuture().join();
        revision.set(new ConfigRevisionId("phase9f-c1-new"));
        assertThrows(IllegalStateException.class, () -> service.confirm(setter, staleRevision,
                "staff-gui", "Stale config"));

        revision.set(REVISION);
        ManualPrestigeAdjustmentReview staleState = service.reviewSet(setter, PLAYER, 8)
                .toCompletableFuture().join();
        store.state.set(state(7, 5));
        assertThrows(CompletionException.class, () -> service.confirm(setter, staleState,
                "staff-gui", "Stale state").toCompletableFuture().join());
        assertTrue(store.adjustments.isEmpty());
    }

    @Test
    void boundedSelectorAndOfflineInitializationUseNoProviderState() {
        MemoryStore store = new MemoryStore(state(6, 1));
        AtomicInteger initializations = new AtomicInteger();
        ManualPrestigeAdministrationService service = service(store, new AtomicReference<>(REVISION),
                initializations);
        PermissionSubject setter = staff(PhaseSixPermissions.PLAYER_VIEW,
                PhaseSixPermissions.PLAYER_PRESTIGE_SET);

        ManualPrestigeTargetPage first = service.targets(setter, PLAYER, 0, 7).toCompletableFuture().join();
        ManualPrestigeTargetPage second = service.targets(setter, PLAYER, 1, 7).toCompletableFuture().join();

        assertEquals(6, first.targets().size());
        assertEquals(1, first.targets().stream()
                .mapToLong(ManualPrestigeAdjustmentReview::targetPrestige).min().orElseThrow());
        assertTrue(first.targets().stream().noneMatch(target -> target.targetPrestige() == 0));
        assertTrue(first.targets().stream().noneMatch(target -> target.targetPrestige() == 6));
        assertTrue(first.hasNext());
        assertTrue(second.hasPrevious());
        assertEquals(Set.of(8L, 9L, 10L), second.targets().stream()
                .map(ManualPrestigeAdjustmentReview::targetPrestige).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, initializations.get());
        assertTrue(store.adjustments.isEmpty());
    }

    private static ManualPrestigeAdministrationService service(
            MemoryStore store,
            AtomicReference<ConfigRevisionId> revision,
            AtomicInteger initializations) {
        return new ManualPrestigeAdministrationService(store, revision::get, Runnable::run,
                ignored -> {
                    initializations.incrementAndGet();
                    return Optional.empty();
                }, () -> new ManualPrestigeAdjustmentPolicy(0, OptionalLong.of(10)));
    }

    private static PermissionSubject staff(String... permissions) {
        return new PermissionSubject(new Actor("player", Optional.of(STAFF), "Owner"), Set.of(permissions));
    }

    private static PlayerPrestigeState state(long prestige, long revision) {
        return new PlayerPrestigeState(PLAYER, prestige, prestige, revision, REVISION,
                new ScopeId("numeric-p" + prestige), Optional.empty(), NOW, NOW);
    }

    private static final class MemoryStore implements PrestigeAdministrationStore {
        private final AtomicReference<PlayerPrestigeState> state;
        private final ArrayList<ManualPrestigeAdjustment> adjustments = new ArrayList<>();

        private MemoryStore(PlayerPrestigeState state) {
            this.state = new AtomicReference<>(state);
        }

        @Override
        public Optional<PlayerPrestigeState> find(UUID playerId) {
            return state.get().playerId().equals(playerId) ? Optional.of(state.get()) : Optional.empty();
        }

        @Override
        public PlayerPrestigeState adjust(ManualPrestigeAdjustment adjustment) {
            PlayerPrestigeState current = state.get();
            if (current.stateRevision() != adjustment.expectedStateRevision()
                    || current.currentPrestige() == adjustment.currentPrestige()) {
                throw new IllegalStateException("stale administrative adjustment");
            }
            PlayerPrestigeState updated = new PlayerPrestigeState(current.playerId(),
                    adjustment.currentPrestige(), adjustment.lifetimePrestige(), current.stateRevision() + 1,
                    adjustment.configRevision(), current.prestigeScope(), current.lastPrestigedAt(),
                    current.createdAt(), NOW);
            if (!state.compareAndSet(current, updated)) {
                throw new IllegalStateException("concurrent administrative adjustment");
            }
            adjustments.add(adjustment);
            return updated;
        }
    }
}
