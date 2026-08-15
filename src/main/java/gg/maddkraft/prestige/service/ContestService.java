package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.ContestMetric;
import gg.maddkraft.prestige.model.HatterContest;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.storage.Database;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ContestService {
    private final Database database;
    private final SeasonService seasons;
    private final IntegrationRelationshipService relationships;
    private volatile PluginSettings settings;
    private final ConcurrentHashMap<UUID, ScoreAccumulator> scores = new ConcurrentHashMap<>();
    private volatile HatterContest active;

    public ContestService(Database database, SeasonService seasons, IntegrationRelationshipService relationships,
                          PluginSettings settings) throws SQLException {
        this.database = database;
        this.seasons = seasons;
        this.relationships = relationships;
        this.settings = settings;
        reloadActiveContest();
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public synchronized void reloadActiveContest() throws SQLException {
        active = database.activeContest(seasons.current().id()).orElse(null);
        scores.clear();
        if (active != null) {
            for (Database.ScoreEntry entry : database.contestLeaderboard(active.id(), 10_000)) {
                scores.put(entry.playerId(), new ScoreAccumulator(entry.score(), 0.0, entry.reachedAt()));
            }
        }
    }

    public Optional<HatterContest> activeContest() {
        return Optional.ofNullable(active);
    }

    public synchronized HatterContest start(ContestMetric metric, int durationDays, int minimumPrestige) throws SQLException {
        if (active != null) throw new IllegalStateException("A Hatter's Contest is already active");
        Instant now = Instant.now();
        active = database.startContest(
                seasons.current().id(),
                metric,
                now,
                now.plus(Math.max(1, durationDays), ChronoUnit.DAYS),
                Math.max(0, minimumPrestige)
        );
        scores.clear();
        relationships.trigger("contest-start", null, null, Map.of(
                "contest_id", active.id(), "metric", active.metric().name(),
                "minimum_prestige", active.minimumPrestige(), "season", active.seasonId()
        ));
        return active;
    }

    public void score(PlayerState state, ContestMetric source, double amount) {
        HatterContest contest = active;
        Instant now = Instant.now();
        if (contest == null || !contest.activeAt(now) || state.prestigeLevel() < contest.minimumPrestige()) return;
        Player player = Bukkit.getPlayer(state.playerId());
        if (player != null && player.hasPermission(settings.hatter().staffPermission())) return;
        double weighted = scoreFor(contest.metric(), source, amount);
        if (!Double.isFinite(weighted) || weighted <= 0.0) return;
        scores.compute(state.playerId(), (uuid, current) -> {
            ScoreAccumulator accumulator = current == null ? new ScoreAccumulator(0.0, 0.0, now) : current;
            accumulator.total += weighted;
            accumulator.dirtyDelta += weighted;
            accumulator.reachedAt = now;
            return accumulator;
        });
    }

    private double scoreFor(ContestMetric target, ContestMetric source, double amount) {
        if (target == source) return amount;
        if (target != ContestMetric.MADNESS_COMPOSITE) return 0.0;
        return switch (source) {
            case SERVER_EARNINGS -> amount / 1_000_000.0;
            case MCMMO_XP -> amount / 1_000.0;
            case RABBIT_HOLES -> amount * 100.0;
            case DECREE_OBJECTIVES -> amount * 75.0;
            case BOSSES -> amount * 150.0;
            case PRESTIGES -> amount * 500.0;
            case MADNESS_COMPOSITE -> amount;
        };
    }

    public synchronized void flush() throws SQLException {
        if (active == null) return;
        for (var entry : scores.entrySet()) {
            ScoreAccumulator accumulator = entry.getValue();
            double delta;
            synchronized (accumulator) {
                delta = accumulator.dirtyDelta;
                accumulator.dirtyDelta = 0.0;
            }
            if (delta <= 0.0) continue;
            try {
                database.addContestScore(active.id(), entry.getKey(), delta);
            } catch (SQLException exception) {
                synchronized (accumulator) {
                    accumulator.dirtyDelta += delta;
                }
                throw exception;
            }
        }
    }

    public List<Database.ScoreEntry> leaderboard(int limit) {
        return scores.entrySet().stream()
                .map(entry -> new Database.ScoreEntry(entry.getKey(), entry.getValue().total, entry.getValue().reachedAt))
                .sorted((a, b) -> {
                    int scoreCompare = Double.compare(b.score(), a.score());
                    return scoreCompare != 0 ? scoreCompare : a.reachedAt().compareTo(b.reachedAt());
                })
                .limit(Math.max(1, limit))
                .toList();
    }

    public OptionalDouble score(UUID playerId) {
        ScoreAccumulator accumulator = scores.get(playerId);
        return accumulator == null ? OptionalDouble.empty() : OptionalDouble.of(accumulator.total);
    }

    public synchronized FinalizeResult finalizeContest(UUID selectedWinner) throws SQLException {
        if (active == null) return FinalizeResult.failure("No active Hatter's Contest.");
        flush();
        List<Database.ScoreEntry> leaderboard = database.contestLeaderboard(active.id(), 100);
        if (leaderboard.isEmpty() && selectedWinner == null) return FinalizeResult.failure("The contest has no eligible scores.");

        UUID winner = selectedWinner;
        if (winner == null) {
            double top = leaderboard.getFirst().score();
            List<UUID> tied = leaderboard.stream()
                    .filter(entry -> Math.abs(entry.score() - top) < 0.000001)
                    .map(Database.ScoreEntry::playerId)
                    .toList();
            if (tied.size() > 1) return FinalizeResult.tie(tied);
            winner = leaderboard.getFirst().playerId();
        } else if (leaderboard.stream().noneMatch(entry -> entry.playerId().equals(selectedWinner))) {
            return FinalizeResult.failure("The selected winner did not participate in this contest.");
        }

        HatterContest completed = active;
        database.finishContest(completed.id(), winner);
        active = null;
        scores.clear();
        relationships.trigger("contest-end", Bukkit.getOfflinePlayer(winner), null, Map.of(
                "contest_id", completed.id(), "winner_uuid", winner.toString(), "metric", completed.metric().name()
        ));
        return FinalizeResult.success(winner, completed.id());
    }

    public PluginSettings.HatterDefinition defaults() {
        return settings.hatter();
    }

    private static final class ScoreAccumulator {
        private double total;
        private double dirtyDelta;
        private Instant reachedAt;

        private ScoreAccumulator(double total, double dirtyDelta, Instant reachedAt) {
            this.total = total;
            this.dirtyDelta = dirtyDelta;
            this.reachedAt = reachedAt;
        }
    }

    public record FinalizeResult(boolean success, boolean tie, String message, UUID winnerId, String contestId, List<UUID> tiedPlayers) {
        static FinalizeResult success(UUID winnerId, String contestId) {
            return new FinalizeResult(true, false, "Contest completed.", winnerId, contestId, List.of());
        }

        static FinalizeResult tie(List<UUID> tiedPlayers) {
            return new FinalizeResult(false, true, "The leading score is tied; run a final Mad Trial and specify its winner.", null, null, List.copyOf(tiedPlayers));
        }

        static FinalizeResult failure(String message) {
            return new FinalizeResult(false, false, message, null, null, List.of());
        }
    }
}
