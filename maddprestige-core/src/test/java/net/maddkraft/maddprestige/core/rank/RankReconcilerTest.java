package net.maddkraft.maddprestige.core.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.rank.AmbiguousRankMembership;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankReconcilerTest {
    private final UUID playerId = UUID.randomUUID();
    private final PlayerStageState internal = state("second");
    private final RankReconciler reconciler = new RankReconciler();

    @Test
    @DisplayName("[A07] Warn-only reports mismatch and performs no automatic action")
    void warnOnlyDoesNotMutate() {
        var decision = reconciler.assess(configuration(ReconciliationPolicy.WARN_ONLY), Optional.of(internal),
                external(Set.of("first_group")));
        assertEquals(ReconciliationStatus.WARNED, decision.status());
        assertEquals(ReconciliationAction.NONE, decision.action());
    }

    @Test
    @DisplayName("[A07] Authoritative mode deterministically requests one idempotent repair")
    void authoritativePlansRepairAndRecognizesMatch() {
        StageConfiguration configuration = configuration(ReconciliationPolicy.MADD_PRESTIGE_AUTHORITATIVE);
        var mismatch = reconciler.assess(configuration, Optional.of(internal), external(Set.of()));
        assertEquals(ReconciliationAction.PROJECT_INTERNAL_STATE, mismatch.action());
        assertEquals(new StageId("second"), mismatch.targetStage().orElseThrow());
        assertEquals(ReconciliationStatus.MATCHED,
                reconciler.assess(configuration, Optional.of(internal), external(Set.of("second_group"))).status());
    }

    @Test
    @DisplayName("[A07] Import-once requires exactly one unambiguous mapped group and never overwrites internal state")
    void importOnceIsExplicitAndUnambiguous() {
        StageConfiguration configuration = configuration(ReconciliationPolicy.IMPORT_ONCE);
        var ready = reconciler.assess(configuration, Optional.empty(), external(Set.of("second_group")));
        assertEquals(ReconciliationStatus.IMPORT_READY, ready.status());
        assertEquals(new StageId("second"), ready.targetStage().orElseThrow());
        assertEquals(ReconciliationStatus.AMBIGUOUS,
                reconciler.assess(configuration, Optional.empty(), external(Set.of())).status());
        assertEquals(ReconciliationStatus.AMBIGUOUS,
                reconciler.assess(configuration, Optional.empty(),
                        external(Set.of("first_group", "second_group"))).status());
        assertEquals(ReconciliationAction.NONE,
                reconciler.assess(configuration, Optional.of(internal), external(Set.of("first_group"))).action());
    }

    @Test
    @DisplayName("[A07] Contextual or temporary managed membership is preserved and fails closed")
    void ambiguityFailsClosed() {
        AmbiguousRankMembership contextual = new AmbiguousRankMembership("second_group",
                Map.of("server", Set.of("example")), Optional.empty());
        var decision = reconciler.assess(configuration(ReconciliationPolicy.MADD_PRESTIGE_AUTHORITATIVE),
                Optional.of(internal), new ManagedRankState(playerId, Set.of(), List.of(contextual)));
        assertEquals(ReconciliationStatus.AMBIGUOUS, decision.status());
        assertEquals(ReconciliationAction.NONE, decision.action());
    }

    @Test
    @DisplayName("[A07] Reconciliation rate gate is bounded and rate-limits repeated work")
    void rateGateIsBounded() {
        ReconciliationRateGate gate = new ReconciliationRateGate(Duration.ofSeconds(30), 2);
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        assertTrue(gate.tryAcquire(playerId, now));
        assertFalse(gate.tryAcquire(playerId, now.plusSeconds(1)));
        gate.tryAcquire(UUID.randomUUID(), now);
        gate.tryAcquire(UUID.randomUUID(), now);
        assertEquals(2, gate.trackedPlayers());
    }

    private PlayerStageState state(String stageId) {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        return new PlayerStageState(playerId, new StageId(stageId), 3, new ConfigRevisionId("revision_1"),
                now, now, now, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private ManagedRankState external(Set<String> groups) {
        return new ManagedRankState(playerId, groups, List.of());
    }

    private static StageConfiguration configuration(ReconciliationPolicy policy) {
        ProviderId provider = new ProviderId("rank_provider");
        StageId first = new StageId("first");
        StageId second = new StageId("second");
        return new StageConfiguration(2, true, Map.of(
                first, new StageDefinition(first, true, "First", Map.of(), StageProjection.group(provider, "first_group")),
                second, new StageDefinition(second, true, "Second", Map.of(), StageProjection.group(provider, "second_group"))),
                List.of(first, second), Optional.of(first), policy);
    }
}
