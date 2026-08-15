package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.EconomyBridge;
import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.ProgressionRank;
import gg.maddkraft.prestige.model.Requirements;
import gg.maddkraft.prestige.storage.Database;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class PrestigeService {
    private final Database database;
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final EconomyBridge economy;
    private final PermissionBridge permissions;
    private final EntitlementService entitlements;
    private final LedgerService ledger;
    private final IntegrationRelationshipService relationships;
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private volatile PluginSettings settings;

    public PrestigeService(
            Database database,
            ProfileService profiles,
            SeasonService seasons,
            EconomyBridge economy,
            PermissionBridge permissions,
            EntitlementService entitlements,
            LedgerService ledger,
            IntegrationRelationshipService relationships,
            PluginSettings settings
    ) {
        this.database = database;
        this.profiles = profiles;
        this.seasons = seasons;
        this.economy = economy;
        this.permissions = permissions;
        this.entitlements = entitlements;
        this.ledger = ledger;
        this.relationships = relationships;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public PrestigeTarget target(PlayerState state, Instant now) {
        int targetLevel = state.prestigeLevel() + 1;
        Requirements requirements = ProgressionMath.prestigeRequirements(settings, state, seasons.current(), now);
        int teaLeaves = ProgressionMath.teaLeafReward(settings, targetLevel);
        double discount = Math.min(
                settings.prestige().maximumRankupDiscount(),
                targetLevel * settings.prestige().rankupDiscountPerPrestige()
        );
        return new PrestigeTarget(targetLevel, requirements, teaLeaves, discount);
    }

    public ActionResult prestige(Player player) {
        ReentrantLock lock = locks.computeIfAbsent(player.getUniqueId(), ignored -> new ReentrantLock());
        if (!lock.tryLock()) return ActionResult.fail("A prestige is already processing for your account.");
        String transactionId = null;
        try {
            PlayerState state = profiles.get(player.getUniqueId()).orElse(null);
            if (state == null) return ActionResult.fail("Your MaddKraft profile is still loading.");
            if (!seasons.current().status().permitsPrestige()) return ActionResult.fail("Prestiging is frozen while the chapter resets.");
            if (state.rank() != ProgressionRank.UNBOUND) return ActionResult.fail("Reach Unbound before prestiging.");
            Instant now = Instant.now();
            long cooldownRemaining = settings.prestige().cooldownSeconds()
                    - Duration.between(state.lastPrestigeAt(), now).toSeconds();
            if (cooldownRemaining > 0) return ActionResult.fail("Wait " + cooldownRemaining + " seconds before prestiging again.");

            PrestigeTarget target = target(state, now);
            if (!state.ledger().satisfies(target.requirements())) return ActionResult.fail("You have not completed all prestige objectives.");
            if (target.requirements().cost() > 0.0 && !economy.available()) return ActionResult.fail("The Vault economy integration is unavailable.");
            if (economy.available() && economy.balance(player) + 0.0001 < target.requirements().cost()) {
                return ActionResult.fail("You need " + economy.format(target.requirements().cost()) + " to prestige.");
            }

            PlayerState snapshot = state.snapshot();
            transactionId = database.beginPrestigeTransaction(state, target.level(), target.requirements().cost());
            EconomyBridge.TransactionResult withdrawal = economy.withdraw(player, target.requirements().cost());
            if (!withdrawal.successful()) {
                database.failPrestigeTransaction(transactionId, withdrawal.error());
                return ActionResult.fail("The prestige payment failed: " + withdrawal.error());
            }
            if (!permissions.setProgression(player, ProgressionRank.CURIOUS)) {
                economy.deposit(player, target.requirements().cost());
                database.failPrestigeTransaction(transactionId, "LuckPerms progression update failed");
                return ActionResult.fail("LuckPerms could not reset your progression group.");
            }

            try {
                int completedLevel = state.completePrestige(target.teaLeaves(), now);
                profiles.saveNow(state);
                try {
                    database.finishPrestigeTransaction(transactionId);
                } catch (SQLException exception) {
                    return ActionResult.fail("Prestige " + completedLevel
                            + " was saved, but its recovery record could not be finalized. Do not retry; contact staff.");
                }

                boolean followUpComplete = true;
                try {
                    entitlements.apply(player, state);
                    ledger.addPrestigeScore(state);
                    relationships.trigger("prestige", player, state, Map.of(
                            "completed_prestige", completedLevel,
                            "tea_reward", target.teaLeaves(),
                            "cost", target.requirements().cost()
                    ));
                } catch (RuntimeException exception) {
                    followUpComplete = false;
                }
                return ActionResult.ok("Prestige " + completedLevel + " complete. You earned " + target.teaLeaves()
                        + " Tea Leaves." + (followUpComplete ? ""
                        : " Some optional permissions will be reconciled when you rejoin."));
            } catch (SQLException | RuntimeException exception) {
                state.restoreFrom(snapshot);
                permissions.setProgression(player, snapshot.rank());
                EconomyBridge.TransactionResult refund = economy.deposit(player, target.requirements().cost());
                try {
                    database.failPrestigeTransaction(transactionId, exception.getClass().getSimpleName() + ": " + exception.getMessage()
                            + (refund.successful() ? "" : "; REFUND FAILED: " + refund.error()));
                } catch (SQLException ignored) {
                    // The still-pending record is deliberately retained for /mp recovery.
                }
                return ActionResult.fail(refund.successful()
                        ? "The prestige was rolled back because saving failed."
                        : "Prestige failed and the automatic refund also failed. Contact an administrator immediately.");
            }
        } catch (SQLException exception) {
            if (transactionId != null) {
                try { database.failPrestigeTransaction(transactionId, exception.getMessage()); } catch (SQLException ignored) {}
            }
            return ActionResult.fail("The prestige database is unavailable. Nothing was changed.");
        } finally {
            lock.unlock();
        }
    }

    public List<String> milestoneMessages(int level) {
        return settings.prestige().milestoneMessages().getOrDefault(level, List.of());
    }

    public record PrestigeTarget(int level, Requirements requirements, int teaLeaves, double resultingRankupDiscount) {}
}
