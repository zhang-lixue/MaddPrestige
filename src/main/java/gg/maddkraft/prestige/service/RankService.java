package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.EconomyBridge;
import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.ProgressionRank;
import gg.maddkraft.prestige.model.Requirements;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class RankService {
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final EconomyBridge economy;
    private final PermissionBridge permissions;
    private final EntitlementService entitlements;
    private final IntegrationRelationshipService relationships;
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private volatile PluginSettings settings;

    public RankService(
            ProfileService profiles,
            SeasonService seasons,
            EconomyBridge economy,
            PermissionBridge permissions,
            EntitlementService entitlements,
            IntegrationRelationshipService relationships,
            PluginSettings settings
    ) {
        this.profiles = profiles;
        this.seasons = seasons;
        this.economy = economy;
        this.permissions = permissions;
        this.entitlements = entitlements;
        this.relationships = relationships;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public Optional<RankTarget> target(PlayerState state, Instant now) {
        Optional<ProgressionRank> next = state.rank().next();
        if (next.isEmpty()) return Optional.empty();
        PluginSettings.RankDefinition definition = settings.ranks().get(next.get());
        Requirements requirements = ProgressionMath.rankRequirements(
                settings, state, seasons.current(), now, definition.requirements()
        );
        return Optional.of(new RankTarget(next.get(), definition.displayName(), requirements));
    }

    public ActionResult rankUp(Player player) {
        ReentrantLock lock = locks.computeIfAbsent(player.getUniqueId(), ignored -> new ReentrantLock());
        if (!lock.tryLock()) return ActionResult.fail("A rank-up is already processing for your account.");
        try {
            PlayerState state = profiles.get(player.getUniqueId()).orElse(null);
            if (state == null) return ActionResult.fail("Your MaddKraft profile is still loading.");
            if (!seasons.current().status().permitsProgress()) return ActionResult.fail("Rank-up is frozen while the chapter resets.");
            RankTarget target = target(state, Instant.now()).orElse(null);
            if (target == null) return ActionResult.fail("You are already Unbound. Use /prestige to continue.");
            if (!state.ledger().satisfies(target.requirements())) return ActionResult.fail("You have not completed all rank-up objectives.");
            if (target.requirements().cost() > 0.0 && !economy.available()) return ActionResult.fail("The Vault economy integration is unavailable.");
            if (economy.available() && economy.balance(player) + 0.0001 < target.requirements().cost()) {
                return ActionResult.fail("You need " + economy.format(target.requirements().cost()) + " to rank up.");
            }

            PlayerState snapshot = state.snapshot();
            EconomyBridge.TransactionResult withdrawal = economy.withdraw(player, target.requirements().cost());
            if (!withdrawal.successful()) return ActionResult.fail("The rank-up payment failed: " + withdrawal.error());
            if (!permissions.setProgression(player, target.rank())) {
                economy.deposit(player, target.requirements().cost());
                return ActionResult.fail("LuckPerms could not update your progression group.");
            }

            try {
                state.advanceRank(target.rank());
                profiles.saveNow(state);
            } catch (SQLException | RuntimeException exception) {
                state.restoreFrom(snapshot);
                permissions.setProgression(player, snapshot.rank());
                EconomyBridge.TransactionResult refund = economy.deposit(player, target.requirements().cost());
                return ActionResult.fail(refund.successful()
                        ? "The rank-up was rolled back because saving failed. Contact staff if this repeats."
                        : "Rank-up failed and the automatic refund also failed. Contact an administrator immediately.");
            }

            boolean followUpComplete = true;
            try {
                entitlements.apply(player, state);
                relationships.trigger("rank-up", player, state, Map.of(
                        "old_rank", snapshot.rank().name(),
                        "new_rank", target.rank().name(),
                        "cost", target.requirements().cost()
                ));
            } catch (RuntimeException exception) {
                followUpComplete = false;
            }
            return ActionResult.ok("You advanced to " + target.displayName() + "."
                    + (followUpComplete ? "" : " Some optional permissions will be reconciled when you rejoin."));
        } finally {
            lock.unlock();
        }
    }

    public record RankTarget(ProgressionRank rank, String displayName, Requirements requirements) {}
}
