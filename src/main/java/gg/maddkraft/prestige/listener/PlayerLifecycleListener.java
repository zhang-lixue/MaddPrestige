package gg.maddkraft.prestige.listener;

import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.service.EntitlementService;
import gg.maddkraft.prestige.service.HatterService;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.service.PatronService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.service.SeasonService;
import gg.maddkraft.prestige.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.Map;

public final class PlayerLifecycleListener implements Listener {
    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final PermissionBridge permissions;
    private final EntitlementService entitlements;
    private final HatterService hatter;
    private final PatronService patrons;
    private final IntegrationRelationshipService relationships;

    public PlayerLifecycleListener(
            JavaPlugin plugin,
            ProfileService profiles,
            SeasonService seasons,
            PermissionBridge permissions,
            EntitlementService entitlements,
            HatterService hatter,
            PatronService patrons,
            IntegrationRelationshipService relationships
    ) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.seasons = seasons;
        this.permissions = permissions;
        this.entitlements = entitlements;
        this.hatter = hatter;
        this.patrons = patrons;
        this.relationships = relationships;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        profiles.load(joining.getUniqueId(), seasons.current().id()).whenComplete((state, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> finishJoin(joining, state, error)));
    }

    private void finishJoin(Player joining, PlayerState state, Throwable error) {
        Player player = plugin.getServer().getPlayer(joining.getUniqueId());
        if (player == null) {
            profiles.unload(joining.getUniqueId());
            return;
        }
        if (error != null || state == null) {
            plugin.getLogger().log(Level.SEVERE, "Could not load MaddPrestige profile for " + player.getName(), error);
            Text.raw(player, "<red>Your progression profile could not be loaded. Please rejoin or contact staff.</red>");
            return;
        }
        permissions.setProgression(player, state.rank());
        entitlements.apply(player, state);
        hatter.reconcile(player);
        patrons.applyPending(player.getName(), player.getUniqueId()).whenComplete((result, pendingError) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player current = plugin.getServer().getPlayer(player.getUniqueId());
                    if (current == null) return;
                    if (pendingError != null || (result != null && !result.success())) {
                        plugin.getLogger().warning("Pending Tebex fulfillment for " + player.getName() + " needs attention: "
                                + (pendingError == null ? result.message() : pendingError.getMessage()));
                    }
                    entitlements.apply(current, state);
                    relationships.trigger("player-join", current, state, Map.of());
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        profiles.unload(event.getPlayer().getUniqueId());
    }
}
