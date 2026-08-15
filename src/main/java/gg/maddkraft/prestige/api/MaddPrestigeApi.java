package gg.maddkraft.prestige.api;

import gg.maddkraft.prestige.model.PlayerState;

import java.util.Optional;
import java.util.UUID;

public interface MaddPrestigeApi {
    boolean addServerEarnings(UUID playerId, double amount, String source);

    boolean addMcMmoXp(UUID playerId, long amount);

    boolean completeRabbitHole(UUID playerId, int amount);

    boolean completeDecreeObjective(UUID playerId, int amount);

    boolean defeatBoss(UUID playerId, int amount);

    Optional<PlayerState> playerState(UUID playerId);
}
