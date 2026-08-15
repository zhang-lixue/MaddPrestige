package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.model.ContestMetric;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;

import java.util.Map;
import java.util.UUID;

public final class LedgerService {
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final ContestService contests;
    private final IntegrationRelationshipService relationships;

    public LedgerService(ProfileService profiles, SeasonService seasons, ContestService contests,
                         IntegrationRelationshipService relationships) {
        this.profiles = profiles;
        this.seasons = seasons;
        this.contests = contests;
        this.relationships = relationships;
    }

    public boolean addServerEarnings(UUID playerId, double amount, String source) {
        if (!seasons.current().status().permitsProgress() || !Double.isFinite(amount) || amount <= 0.0) return false;
        PlayerState state = profiles.get(playerId).orElse(null);
        if (state == null || !state.seasonId().equals(seasons.current().id())) return false;
        state.ledger().addServerEarnings(amount);
        state.markDirty();
        contests.score(state, ContestMetric.SERVER_EARNINGS, amount);
        progress(playerId, state, ContestMetric.SERVER_EARNINGS, amount, source);
        return true;
    }

    public boolean addMcMmoXp(UUID playerId, long amount) {
        if (!seasons.current().status().permitsProgress() || amount <= 0L) return false;
        PlayerState state = profiles.get(playerId).orElse(null);
        if (state == null || !state.seasonId().equals(seasons.current().id())) return false;
        state.ledger().addMcMmoXp(amount);
        state.markDirty();
        contests.score(state, ContestMetric.MCMMO_XP, amount);
        progress(playerId, state, ContestMetric.MCMMO_XP, amount, "mcMMO");
        return true;
    }

    public boolean addRabbitHole(UUID playerId, int amount) {
        return addCounter(playerId, amount, ContestMetric.RABBIT_HOLES);
    }

    public boolean addDecreeObjective(UUID playerId, int amount) {
        return addCounter(playerId, amount, ContestMetric.DECREE_OBJECTIVES);
    }

    public boolean addBoss(UUID playerId, int amount) {
        return addCounter(playerId, amount, ContestMetric.BOSSES);
    }

    public void addPrestigeScore(PlayerState state) {
        contests.score(state, ContestMetric.PRESTIGES, 1.0);
    }

    private boolean addCounter(UUID playerId, int amount, ContestMetric metric) {
        if (!seasons.current().status().permitsProgress() || amount <= 0) return false;
        PlayerState state = profiles.get(playerId).orElse(null);
        if (state == null || !state.seasonId().equals(seasons.current().id())) return false;
        switch (metric) {
            case RABBIT_HOLES -> state.ledger().addRabbitHoles(amount);
            case DECREE_OBJECTIVES -> state.ledger().addDecreeObjectives(amount);
            case BOSSES -> state.ledger().addBosses(amount);
            default -> throw new IllegalArgumentException("unsupported counter " + metric);
        }
        state.markDirty();
        contests.score(state, metric, amount);
        progress(playerId, state, metric, amount, "external");
        return true;
    }

    private void progress(UUID playerId, PlayerState state, ContestMetric metric, double amount, String source) {
        relationships.triggerPlayerId("progress", playerId, state, Map.of(
                "progress_type", metric.name(),
                "amount", amount,
                "source", source == null ? "external" : source
        ));
    }
}
