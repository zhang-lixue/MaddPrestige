package net.maddkraft.maddprestige.integrations.worldguard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Direct read-only WorldGuard 7.0.18 public-API adapter. */
public final class OfficialWorldGuardRegionAccess implements WorldGuardRegionAccess {
    private final Server server;

    public OfficialWorldGuardRegionAccess(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Observation observe(UUID playerId, UUID worldId, String regionId) {
        Player player = server.getPlayer(Objects.requireNonNull(playerId, "player ID"));
        if (player == null || !player.isOnline()) {
            return Observation.unavailable("Player is offline");
        }
        World configuredWorld = server.getWorld(Objects.requireNonNull(worldId, "world ID"));
        if (configuredWorld == null) {
            return Observation.unavailable("Configured world is not loaded");
        }
        if (!configuredWorld.getUID().equals(player.getWorld().getUID())) {
            return Observation.outside();
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(configuredWorld));
        if (manager == null) {
            return Observation.unavailable("WorldGuard has no region manager for the configured world");
        }
        ProtectedRegion region = manager.getRegion(Objects.requireNonNull(regionId, "region ID"));
        if (region == null) {
            return Observation.unavailable("Configured WorldGuard region does not exist");
        }
        ApplicableRegionSet applicable = manager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));
        if (applicable.isVirtual()) {
            return Observation.unavailable("WorldGuard returned a virtual applicable-region set");
        }
        return applicable.getRegions().stream().map(ProtectedRegion::getId).anyMatch(region.getId()::equals)
                ? Observation.inside() : Observation.outside();
    }
}
