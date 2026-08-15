package gg.maddkraft.prestige.storage;

import gg.maddkraft.prestige.model.ContestMetric;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.ProgressionRank;
import gg.maddkraft.prestige.model.Season;
import gg.maddkraft.prestige.model.SeasonStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {
    @TempDir Path temporary;

    @Test
    void persistsProgressionRecoveryContestsAndTebexQueue() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (Database database = new Database(temporary.resolve("maddprestige.db"), Logger.getAnonymousLogger())) {
            database.initialize();
            Season season = database.ensureActiveSeason("chapter-1", "Chapter One", 150);
            PlayerState state = database.loadPlayer(playerId, season.id());
            state.setRank(ProgressionRank.UNBOUND);
            state.setPrestigeLevel(9);
            state.addTeaLeaves(4);
            state.buyPerk("claim-blocks");
            state.ledger().addServerEarnings(42_000_000);
            state.ledger().addRabbitHoles(10);
            database.savePlayer(state);

            PlayerState restored = database.loadPlayer(playerId, season.id());
            assertEquals(ProgressionRank.UNBOUND, restored.rank());
            assertEquals(9, restored.prestigeLevel());
            assertEquals(4, restored.teaLeaves());
            assertEquals(1, restored.claimPerkLevel());
            assertEquals(42_000_000, restored.ledger().serverEarnings());

            String transaction = database.beginPrestigeTransaction(restored, 10, 25_000_000);
            assertEquals(1, database.pendingPrestigeTransactions().size());
            database.finishPrestigeTransaction(transaction);
            assertTrue(database.pendingPrestigeTransactions().isEmpty());

            database.queuePatronGrant("Alice_123", "WHITE_QUEEN");
            assertEquals("WHITE_QUEEN", database.pendingPatronGrant("alice_123").orElseThrow().tier());
            database.deletePendingPatronGrant("ALICE_123");
            assertTrue(database.pendingPatronGrant("Alice_123").isEmpty());

            Instant now = Instant.now();
            var contest = database.startContest(season.id(), ContestMetric.MCMMO_XP, now, now.plus(7, ChronoUnit.DAYS), 10);
            database.addContestScore(contest.id(), playerId, 100);
            database.addContestScore(contest.id(), playerId, 50);
            assertEquals(150.0, database.contestLeaderboard(contest.id(), 10).getFirst().score());
            database.finishContest(contest.id(), playerId);

            var holder = database.transferHatter(playerId, season.id(), contest.id(), "test contest");
            assertEquals(playerId, database.hatterHolder().orElseThrow().playerId());
            database.revokeHatter(season.id(), "test complete");
            assertTrue(database.hatterHolder().isEmpty());
            assertEquals(holder.playerId(), database.hatterHistory(10).getFirst().playerId());

            database.updateSeasonStatus(season.id(), SeasonStatus.FROZEN);
            database.convertLegacyStars(season.id(), 5);
            Season next = database.createNewSeason("chapter-2", "Chapter Two", 150);
            assertEquals(2, next.number());
            PlayerState nextState = database.loadPlayer(playerId, next.id());
            assertEquals(1, nextState.legacyStars());
            assertEquals(1, nextState.claimPerkLevel(), "lifetime claim reward must load in a new chapter");

            database.recordAdminAction("CONSOLE", "TEST", playerId.toString(), "integration test");

            assertTrue(database.loadPlayerPreferences(playerId).menuSounds());
            var preferences = new Database.PlayerPreferences(false, true, false, "REWARDS");
            database.savePlayerPreferences(playerId, preferences);
            assertEquals(preferences, database.loadPlayerPreferences(playerId));
        }
    }
}
