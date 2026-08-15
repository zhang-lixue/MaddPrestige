package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.storage.Database;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Map;

public final class PatronService {
    private final PermissionBridge permissions;
    private final Database database;
    private final IntegrationRelationshipService relationships;
    private volatile PluginSettings settings;

    public PatronService(PermissionBridge permissions, Database database,
                         IntegrationRelationshipService relationships, PluginSettings settings) {
        this.permissions = permissions;
        this.database = database;
        this.relationships = relationships;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public CompletableFuture<ActionResult> setTier(UUID playerId, String rawTier) {
        String tier = normalize(rawTier);
        PluginSettings.PatronTierDefinition selected = null;
        if (!tier.equals("NONE") && !tier.equals("REMOVE")) {
            selected = settings.patronTiers().get(tier);
            if (selected == null) {
                return CompletableFuture.completedFuture(ActionResult.fail(
                        "Unknown patron tier. Choose " + String.join(", ", settings.patronTiers().keySet()) + ", or NONE."
                ));
            }
        }
        Set<String> managed = settings.patronTiers().values().stream()
                .map(PluginSettings.PatronTierDefinition::luckPermsGroup)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> managedPermissions = settings.patronTiers().values().stream()
                .map(PluginSettings.PatronTierDefinition::entitlementPermission)
                .collect(Collectors.toUnmodifiableSet());
        String selectedGroup = selected == null ? null : selected.luckPermsGroup();
        String selectedPermission = selected == null ? null : selected.entitlementPermission();
        String displayName = selected == null ? "no paid rank" : selected.displayName();
        String relationshipTier = selected == null ? "NONE" : tier;
        return permissions.setPatronTier(playerId, selectedGroup, selectedPermission, managed, managedPermissions)
                .thenApply(success -> {
                    if (success) {
                        relationships.triggerPlayerId("patron-change", playerId, null,
                                Map.of("patron_tier", relationshipTier, "patron_display", displayName));
                        return ActionResult.ok("Patron tier set to " + displayName + ".");
                    }
                    return ActionResult.fail("LuckPerms could not update that patron tier.");
                });
    }

    public ActionResult queueForFirstJoin(String username, String rawTier) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            return ActionResult.fail("That is not a valid Minecraft username.");
        }
        String tier = normalize(rawTier);
        if (!tier.equals("NONE") && !tier.equals("REMOVE") && !settings.patronTiers().containsKey(tier)) {
            return ActionResult.fail("Unknown patron tier. Choose " + String.join(", ", settings.patronTiers().keySet()) + ", or NONE.");
        }
        try {
            database.queuePatronGrant(username, tier);
            return ActionResult.ok("Queued patron tier " + tier + " for " + username + " when they next join.");
        } catch (SQLException exception) {
            return ActionResult.fail("Could not queue that Tebex fulfillment: " + exception.getMessage());
        }
    }

    public CompletableFuture<ActionResult> applyPending(String username, UUID playerId) {
        try {
            var pending = database.pendingPatronGrant(username);
            if (pending.isEmpty()) return CompletableFuture.completedFuture(ActionResult.ok("No pending patron tier."));
            return setTier(playerId, pending.get().tier()).thenApply(result -> {
                if (result.success()) {
                    try {
                        database.deletePendingPatronGrant(username);
                    } catch (SQLException exception) {
                        return ActionResult.fail("Patron tier applied, but its pending record could not be cleared.");
                    }
                }
                return result;
            });
        } catch (SQLException exception) {
            return CompletableFuture.completedFuture(ActionResult.fail("Could not read pending patron fulfillment."));
        }
    }

    public Set<String> tierNames() {
        return settings.patronTiers().keySet();
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
