package gg.maddkraft.prestige.integration;

import gg.maddkraft.prestige.model.ProgressionRank;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface PermissionBridge {
    boolean available();

    boolean setProgression(Player player, ProgressionRank rank);

    boolean setMaddHatter(UUID playerId, boolean enabled);

    void updateEntitlements(Player player, int homes, int listings);

    void updateRelationshipPermissions(Player player, Set<String> permissions,
                                       Set<String> managedPrefixes, Set<String> managedNodes);

    CompletableFuture<Boolean> setPatronTier(
            UUID playerId,
            String selectedGroup,
            String selectedPermission,
            Set<String> managedGroups,
            Set<String> managedPermissions
    );

    CompletableFuture<Boolean> ensureGroups(Set<String> groupNames);
}
