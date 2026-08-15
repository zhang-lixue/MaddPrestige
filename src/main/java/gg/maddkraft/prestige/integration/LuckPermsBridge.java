package gg.maddkraft.prestige.integration;

import gg.maddkraft.prestige.model.ProgressionRank;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsBridge implements PermissionBridge {
    private static final String TITLE_META_KEY = "maddkraft-title";
    private static final String ENTITLEMENT_PREFIX = "maddprestige.entitlement.";

    private final LuckPerms luckPerms;
    private final Map<ProgressionRank, String> progressionGroups;

    private LuckPermsBridge(LuckPerms luckPerms, Map<ProgressionRank, String> progressionGroups) {
        this.luckPerms = luckPerms;
        this.progressionGroups = progressionGroups;
    }

    public static PermissionBridge create(JavaPlugin plugin, boolean enabled, Map<ProgressionRank, String> groups) {
        if (!enabled || plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) return new NoopPermissionBridge();
        RegisteredServiceProvider<LuckPerms> registration = plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (registration == null || registration.getProvider() == null) return new NoopPermissionBridge();
        return new LuckPermsBridge(registration.getProvider(), groups);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean setProgression(Player player, ProgressionRank rank) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;
            Set<String> groupNames = new HashSet<>(progressionGroups.values());
            for (Node node : Set.copyOf(user.data().toCollection())) {
                if (node instanceof InheritanceNode inheritance && groupNames.contains(inheritance.getGroupName())) {
                    user.data().remove(node);
                }
            }
            user.data().add(InheritanceNode.builder(progressionGroups.get(rank)).build());
            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean setMaddHatter(UUID playerId, boolean enabled) {
        try {
            User user = luckPerms.getUserManager().getUser(playerId);
            if (user == null) {
                luckPerms.getUserManager().loadUser(playerId).thenAccept(loaded -> updateTitle(loaded, enabled));
                return true;
            }
            updateTitle(user, enabled);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void updateTitle(User user, boolean enabled) {
        for (Node node : Set.copyOf(user.data().toCollection())) {
            if (node instanceof MetaNode meta && TITLE_META_KEY.equals(meta.getMetaKey())) user.data().remove(node);
        }
        if (enabled) user.data().add(MetaNode.builder(TITLE_META_KEY, "maddhatter").build());
        luckPerms.getUserManager().saveUser(user);
    }

    @Override
    public void updateEntitlements(Player player, int homes, int listings) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return;
            for (Node node : Set.copyOf(user.data().toCollection())) {
                if (node.getKey().startsWith(ENTITLEMENT_PREFIX)) {
                    user.data().remove(node);
                }
            }
            user.data().add(PermissionNode.builder(ENTITLEMENT_PREFIX + "homes." + homes).build());
            user.data().add(PermissionNode.builder(ENTITLEMENT_PREFIX + "auction-listings." + listings).build());
            luckPerms.getUserManager().saveUser(user);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void updateRelationshipPermissions(Player player, Set<String> permissions,
                                              Set<String> managedPrefixes, Set<String> managedNodes) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return;
            for (Node node : Set.copyOf(user.data().toCollection())) {
                if (node instanceof PermissionNode && (managedNodes.contains(node.getKey())
                        || managedPrefixes.stream().anyMatch(node.getKey()::startsWith))) user.data().remove(node);
            }
            permissions.stream().filter(node -> node.matches("[a-z0-9_.-]+"))
                    .forEach(node -> user.data().add(PermissionNode.builder(node).build()));
            luckPerms.getUserManager().saveUser(user);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public CompletableFuture<Boolean> setPatronTier(
            UUID playerId,
            String selectedGroup,
            String selectedPermission,
            Set<String> managedGroups,
            Set<String> managedPermissions
    ) {
        return luckPerms.getUserManager().loadUser(playerId).thenCompose(user -> {
            for (Node node : Set.copyOf(user.data().toCollection())) {
                if ((node instanceof InheritanceNode inheritance && managedGroups.contains(inheritance.getGroupName()))
                        || (node instanceof PermissionNode permission && managedPermissions.contains(permission.getPermission()))) {
                    user.data().remove(node);
                }
            }
            if (selectedGroup != null && !selectedGroup.isBlank()) {
                user.data().add(InheritanceNode.builder(selectedGroup).build());
            }
            if (selectedPermission != null && !selectedPermission.isBlank()) {
                user.data().add(PermissionNode.builder(selectedPermission).build());
            }
            return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> true);
        }).exceptionally(ignored -> false);
    }

    @Override
    public CompletableFuture<Boolean> ensureGroups(Set<String> groupNames) {
        CompletableFuture<?>[] creations = groupNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> luckPerms.getGroupManager().createAndLoadGroup(name))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(creations).thenApply(ignored -> true).exceptionally(ignored -> false);
    }

    private static final class NoopPermissionBridge implements PermissionBridge {
        @Override public boolean available() { return false; }
        @Override public boolean setProgression(Player player, ProgressionRank rank) { return true; }
        @Override public boolean setMaddHatter(UUID playerId, boolean enabled) { return true; }
        @Override public void updateEntitlements(Player player, int homes, int listings) {}
        @Override public void updateRelationshipPermissions(Player player, Set<String> permissions,
                                                            Set<String> managedPrefixes, Set<String> managedNodes) {}
        @Override public CompletableFuture<Boolean> setPatronTier(UUID playerId, String selectedGroup, String selectedPermission,
                                                                  Set<String> managedGroups, Set<String> managedPermissions) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletableFuture<Boolean> ensureGroups(Set<String> groupNames) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
