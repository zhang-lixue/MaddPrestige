package gg.maddkraft.prestige.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerStateTest {
    @Test
    void prestigeResetsRunButKeepsLifetimeState() {
        PlayerState state = new PlayerState(UUID.randomUUID(), "chapter-1");
        state.setRank(ProgressionRank.UNBOUND);
        state.addTeaLeaves(3);
        state.buyPerk("claim-blocks");
        state.ledger().addServerEarnings(50_000_000);
        state.ledger().addMcMmoXp(600_000);

        int completed = state.completePrestige(2, Instant.parse("2026-07-01T00:00:00Z"));

        assertEquals(1, completed);
        assertEquals(ProgressionRank.CURIOUS, state.rank());
        assertEquals(5, state.teaLeaves());
        assertEquals(1, state.lifetimePrestiges());
        assertEquals(0.0, state.ledger().serverEarnings());
        assertEquals(0, state.ledger().mcMmoXp());

        state.beginSeason("chapter-2");
        assertEquals(0, state.prestigeLevel());
        assertEquals(0, state.teaLeaves());
        assertEquals(1, state.claimPerkLevel(), "permanent GriefPrevention rewards must survive chapters");
    }

    @Test
    void snapshotCanRollbackEveryMutableField() {
        PlayerState state = new PlayerState(UUID.randomUUID(), "chapter-1");
        state.addTeaLeaves(9);
        state.ledger().addBosses(4);
        PlayerState before = state.snapshot();

        state.setPrestigeLevel(12);
        state.spendTeaLeaves(5);
        state.ledger().addRabbitHoles(7);
        state.restoreFrom(before);

        assertEquals(0, state.prestigeLevel());
        assertEquals(9, state.teaLeaves());
        assertEquals(4, state.ledger().bosses());
        assertEquals(0, state.ledger().rabbitHoles());
    }
}
