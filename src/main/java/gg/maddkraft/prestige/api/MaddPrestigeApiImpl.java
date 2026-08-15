package gg.maddkraft.prestige.api;

import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.service.LedgerService;
import gg.maddkraft.prestige.service.ProfileService;

import java.util.Optional;
import java.util.UUID;

public final class MaddPrestigeApiImpl implements MaddPrestigeApi {
    private final LedgerService ledger;
    private final ProfileService profiles;

    public MaddPrestigeApiImpl(LedgerService ledger, ProfileService profiles) {
        this.ledger = ledger;
        this.profiles = profiles;
    }

    @Override public boolean addServerEarnings(UUID playerId, double amount, String source) { return ledger.addServerEarnings(playerId, amount, source); }
    @Override public boolean addMcMmoXp(UUID playerId, long amount) { return ledger.addMcMmoXp(playerId, amount); }
    @Override public boolean completeRabbitHole(UUID playerId, int amount) { return ledger.addRabbitHole(playerId, amount); }
    @Override public boolean completeDecreeObjective(UUID playerId, int amount) { return ledger.addDecreeObjective(playerId, amount); }
    @Override public boolean defeatBoss(UUID playerId, int amount) { return ledger.addBoss(playerId, amount); }
    @Override public Optional<PlayerState> playerState(UUID playerId) { return profiles.get(playerId).map(PlayerState::snapshot); }
}
