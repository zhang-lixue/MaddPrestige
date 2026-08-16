package net.maddkraft.maddprestige.integrations.vault;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;

/** Immutable binding to one Vault service generation. */
public final class VaultEconomyBinding {
    private final Economy economy;
    private final Function<UUID, OfflinePlayer> players;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;

    public VaultEconomyBinding(
            Economy economy,
            Function<UUID, OfflinePlayer> players,
            IntegrationTaskScheduler scheduler,
            MutableProviderHealth health) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.health = Objects.requireNonNull(health, "health");
    }

    Economy economy() {
        return economy;
    }

    OfflinePlayer player(UUID playerId) {
        return players.apply(playerId);
    }

    IntegrationTaskScheduler scheduler() {
        return scheduler;
    }

    MutableProviderHealth health() {
        return health;
    }

    boolean available() {
        return health.isUsable() && economy.isEnabled();
    }

    double exactAmount(BigDecimal value) {
        double converted = structuralAmount(value);
        int digits = economy.fractionalDigits();
        BigDecimal scaled = digits < 0 ? value : value.setScale(digits, RoundingMode.UNNECESSARY);
        if (BigDecimal.valueOf(converted).compareTo(scaled) != 0) {
            throw new IllegalArgumentException("Vault amount cannot be represented exactly by the provider API");
        }
        return converted;
    }

    static double structuralAmount(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Vault amount must be positive");
        }
        double converted = value.doubleValue();
        if (!Double.isFinite(converted) || BigDecimal.valueOf(converted).compareTo(value) != 0) {
            throw new IllegalArgumentException("Vault amount cannot be represented exactly by the provider API");
        }
        return converted;
    }
}
