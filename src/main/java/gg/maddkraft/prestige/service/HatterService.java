package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.model.HatterHolder;
import gg.maddkraft.prestige.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

public final class HatterService {
    private final Database database;
    private final SeasonService seasons;
    private final PermissionBridge permissions;
    private final HatterItemService items;
    private final IntegrationRelationshipService relationships;
    private volatile PluginSettings settings;
    private volatile HatterHolder holder;

    public HatterService(
            Database database,
            SeasonService seasons,
            PermissionBridge permissions,
            HatterItemService items,
            IntegrationRelationshipService relationships,
            PluginSettings settings
    ) throws SQLException {
        this.database = database;
        this.seasons = seasons;
        this.permissions = permissions;
        this.items = items;
        this.relationships = relationships;
        this.settings = settings;
        this.holder = database.hatterHolder().orElse(null);
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public Optional<HatterHolder> holder() {
        return Optional.ofNullable(holder);
    }

    public synchronized ActionResult transfer(UUID newHolder, String contestId, String reason) {
        HatterHolder previous = holder;
        try {
            if (previous != null && previous.playerId().equals(newHolder)) {
                return ActionResult.fail("That player is already The MaddHatter.");
            }
            if (previous != null) permissions.setMaddHatter(previous.playerId(), false);
            HatterHolder created = database.transferHatter(newHolder, seasons.current().id(), contestId, reason);
            holder = created;
            permissions.setMaddHatter(newHolder, true);

            if (previous != null) {
                Player oldPlayer = Bukkit.getPlayer(previous.playerId());
                if (oldPlayer != null) items.reconcile(oldPlayer, created);
            }
            Player newPlayer = Bukkit.getPlayer(newHolder);
            if (newPlayer != null) {
                items.reconcile(newPlayer, created);
                items.claim(newPlayer, created);
            }
            if (previous != null) {
                relationships.trigger("hatter-lost", Bukkit.getOfflinePlayer(previous.playerId()), null,
                        Map.of("reason", safe(reason)));
            }
            relationships.trigger("hatter-gained", Bukkit.getOfflinePlayer(newHolder), null,
                    Map.of("contest_id", safe(contestId), "reason", safe(reason)));
            return ActionResult.ok(Bukkit.getOfflinePlayer(newHolder).getName() + " is now The MaddHatter.");
        } catch (SQLException exception) {
            if (previous != null) permissions.setMaddHatter(previous.playerId(), true);
            return ActionResult.fail("The title transfer failed before completion: " + exception.getMessage());
        }
    }

    public synchronized ActionResult revoke(String reason) {
        if (holder == null) return ActionResult.fail("The MaddHatter position is already vacant.");
        HatterHolder previous = holder;
        try {
            database.revokeHatter(seasons.current().id(), reason);
            holder = null;
            permissions.setMaddHatter(previous.playerId(), false);
            Player player = Bukkit.getPlayer(previous.playerId());
            if (player != null) items.reconcile(player, null);
            relationships.trigger("hatter-lost", Bukkit.getOfflinePlayer(previous.playerId()), null,
                    Map.of("reason", safe(reason)));
            return ActionResult.ok("The MaddHatter position is now vacant.");
        } catch (SQLException exception) {
            return ActionResult.fail("The title could not be revoked: " + exception.getMessage());
        }
    }

    public void reconcile(Player player) {
        HatterHolder current = holder;
        permissions.setMaddHatter(player.getUniqueId(), current != null && current.playerId().equals(player.getUniqueId()));
        items.reconcile(player, current);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public ActionResult claim(Player player) {
        return items.claim(player, holder);
    }

    public Optional<ActionResult> revokeIfInactive(Instant now) {
        HatterHolder current = holder;
        if (current == null) return Optional.empty();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(current.playerId());
        if (offline.isOnline()) return Optional.empty();
        try {
            Instant lastSeen = database.lastSeen(current.playerId()).orElse(current.since());
            if (Duration.between(lastSeen, now).toDays() < settings.hatter().inactivityDays()) return Optional.empty();
            return Optional.of(revoke("inactive for " + settings.hatter().inactivityDays() + " days"));
        } catch (SQLException exception) {
            return Optional.of(ActionResult.fail("Could not check MaddHatter inactivity: " + exception.getMessage()));
        }
    }

    public List<Database.HatterHistoryEntry> history(int limit) throws SQLException {
        return database.hatterHistory(limit);
    }
}
