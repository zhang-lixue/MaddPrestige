package gg.maddkraft.prestige.integration;

import gg.maddkraft.prestige.api.MaddPrestigeActionEvent;
import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Config-driven relationship layer for optional plugins. It supports outbound
 * lifecycle commands, public Bukkit events, and dynamic entitlement nodes.
 */
public final class IntegrationRelationshipService {
    private final JavaPlugin owner;
    private final PermissionBridge permissions;
    private final ThreadLocal<Integer> triggerDepth = ThreadLocal.withInitial(() -> 0);
    private volatile PluginSettings settings;

    public IntegrationRelationshipService(JavaPlugin owner, PermissionBridge permissions, PluginSettings settings) {
        this.owner = owner;
        this.permissions = permissions;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public void applyEntitlements(Player player, Map<String, Integer> values) {
        PluginSettings.RelationshipSettings relationshipSettings = settings.relationships();
        if (!relationshipSettings.enabled()) {
            permissions.updateRelationshipPermissions(player, Set.of(), managedPrefixes(), managedNodes());
            return;
        }
        Set<String> nodes = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        Set<String> managedNodes = new HashSet<>();
        for (PluginSettings.ExternalRelationship relationship : relationshipSettings.providers().values()) {
            relationship.entitlementPermissions().values().forEach(mapping -> {
                prefixes.addAll(mapping.clearPrefixes());
                managedNodes.addAll(mapping.clearNodes());
            });
            if (!active(relationship)) continue;
            for (Map.Entry<String, PluginSettings.EntitlementPermissionMapping> entry
                    : relationship.entitlementPermissions().entrySet()) {
                int value = values.getOrDefault(entry.getKey(), 0);
                PluginSettings.EntitlementPermissionMapping mapping = entry.getValue();
                if (value < mapping.minimumValue()) continue;
                for (String template : mapping.nodes()) {
                    String expanded = replace(template, entitlementVariables(values, entry.getKey(), value));
                    if (expanded.matches("[a-z0-9_.-]+")) nodes.add(expanded);
                }
            }
        }
        permissions.updateRelationshipPermissions(player, nodes, prefixes, managedNodes);
    }

    public void trigger(String rawAction, OfflinePlayer player, PlayerState state, Map<String, ?> suppliedValues) {
        String action = normalize(rawAction);
        if (!action.matches("[a-z0-9-]{1,64}")) return;
        if (!Bukkit.isPrimaryThread()) {
            Map<String, ?> snapshot = suppliedValues == null ? Map.of() : new LinkedHashMap<>(suppliedValues);
            owner.getServer().getScheduler().runTask(owner, () -> trigger(action, player, state, snapshot));
            return;
        }
        PluginSettings.RelationshipSettings relationshipSettings = settings.relationships();
        if (!relationshipSettings.enabled()) return;
        if (action.equals("progress") && !relationshipSettings.emitProgressActions()) return;
        int depth = triggerDepth.get();
        if (depth >= relationshipSettings.maxTriggerDepth()) {
            owner.getLogger().warning("Relationship trigger depth limit reached for " + action + "; nested action skipped.");
            return;
        }
        triggerDepth.set(depth + 1);
        try {
            Map<String, String> values = variables(action, player, state,
                    suppliedValues == null ? Map.of() : suppliedValues);
            owner.getServer().getPluginManager().callEvent(new MaddPrestigeActionEvent(action, player, values));

            int dispatched = 0;
            for (Map.Entry<String, PluginSettings.ExternalRelationship> provider
                    : relationshipSettings.providers().entrySet()) {
                PluginSettings.ExternalRelationship relationship = provider.getValue();
                if (!active(relationship)) continue;
                List<String> configured = relationship.commands().getOrDefault(action, List.of());
                for (String rawCommand : configured) {
                    if (dispatched >= relationshipSettings.maxCommandsPerTrigger()) {
                        owner.getLogger().warning("Relationship command limit reached for trigger " + action);
                        return;
                    }
                    Map<String, String> withPlugin = new HashMap<>(values);
                    withPlugin.put("provider", provider.getKey());
                    withPlugin.put("plugin", relationship.displayName());
                    String command = replace(rawCommand, withPlugin).trim();
                    if (command.startsWith("/")) command = command.substring(1);
                    if (command.isBlank() || command.contains("\n") || command.contains("\r")) continue;
                    if (relationshipSettings.skipCommandsWithUnresolvedTokens()
                            && command.matches(".*\\{[A-Za-z0-9_]+}.*")) {
                        owner.getLogger().warning("Skipped relationship command with unresolved token for provider "
                                + provider.getKey() + " action " + action);
                        continue;
                    }
                    String commandRoot = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                    String bareRoot = commandRoot.contains(":")
                            ? commandRoot.substring(commandRoot.lastIndexOf(':') + 1) : commandRoot;
                    if (relationshipSettings.blockedCommandRoots().contains(commandRoot)
                            || relationshipSettings.blockedCommandRoots().contains(bareRoot)) {
                        owner.getLogger().warning("Blocked relationship command root '" + commandRoot
                                + "' for provider " + provider.getKey());
                        continue;
                    }
                    boolean success;
                    try {
                        success = owner.getServer().dispatchCommand(owner.getServer().getConsoleSender(), command);
                    } catch (RuntimeException exception) {
                        owner.getLogger().warning("Relationship command failed for provider " + provider.getKey()
                                + " action " + action + ": " + exception.getMessage());
                        continue;
                    }
                    dispatched++;
                    if (relationshipSettings.logDispatchedCommands()) {
                        owner.getLogger().info("Relationship action=" + action + " provider=" + provider.getKey()
                                + " command=" + commandRoot + " success=" + success);
                    }
                }
            }
        } catch (RuntimeException exception) {
            owner.getLogger().warning("Relationship action " + action + " was isolated after an error: "
                    + exception.getMessage());
        } finally {
            if (depth == 0) triggerDepth.remove();
            else triggerDepth.set(depth);
        }
    }

    public void triggerPlayerId(String action, UUID playerId, PlayerState state, Map<String, ?> suppliedValues) {
        if (!Bukkit.isPrimaryThread()) {
            owner.getServer().getScheduler().runTask(owner,
                    () -> triggerPlayerId(action, playerId, state, suppliedValues));
            return;
        }
        trigger(action, playerId == null ? null : Bukkit.getOfflinePlayer(playerId), state, suppliedValues);
    }

    public RelationshipSummary summaryFor(String pluginName) {
        for (Map.Entry<String, PluginSettings.ExternalRelationship> entry : settings.relationships().providers().entrySet()) {
            PluginSettings.ExternalRelationship relationship = entry.getValue();
            if (relationship.displayName().equalsIgnoreCase(pluginName)
                    || relationship.pluginNames().stream().anyMatch(name -> name.equalsIgnoreCase(pluginName))) {
                int commandCount = relationship.commands().values().stream().mapToInt(List::size).sum();
                int permissionCount = relationship.entitlementPermissions().values().stream()
                        .mapToInt(mapping -> mapping.nodes().size()).sum();
                return new RelationshipSummary(entry.getKey(), true, relationship.enabled(), active(relationship),
                        relationship.features().size(), relationship.eventHooks().size(), commandCount, permissionCount);
            }
        }
        return new RelationshipSummary("", false, false, false, 0, 0, 0, 0);
    }

    public int configuredProviderCount() {
        return settings.relationships().providers().size();
    }

    public boolean acceptsInbound(String providerId) {
        PluginSettings.RelationshipSettings relationshipSettings = settings.relationships();
        PluginSettings.ExternalRelationship relationship = relationshipSettings.providers().get(providerId);
        return relationshipSettings.enabled() && relationship != null && relationship.enabled();
    }

    private boolean active(PluginSettings.ExternalRelationship relationship) {
        if (!relationship.enabled()) return false;
        if (!relationship.requirePlugin()) return true;
        for (String name : relationship.pluginNames()) {
            Plugin plugin = owner.getServer().getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) return true;
        }
        return false;
    }

    private Set<String> managedPrefixes() {
        Set<String> prefixes = new HashSet<>();
        settings.relationships().providers().values().forEach(relationship ->
                relationship.entitlementPermissions().values().forEach(mapping -> prefixes.addAll(mapping.clearPrefixes())));
        return prefixes;
    }

    private Set<String> managedNodes() {
        Set<String> nodes = new HashSet<>();
        settings.relationships().providers().values().forEach(relationship ->
                relationship.entitlementPermissions().values().forEach(mapping -> nodes.addAll(mapping.clearNodes())));
        return nodes;
    }

    private Map<String, String> variables(String action, OfflinePlayer player, PlayerState state,
                                          Map<String, ?> suppliedValues) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("action", action);
        values.put("player", player == null || player.getName() == null ? "" : player.getName());
        values.put("uuid", player == null ? "" : player.getUniqueId().toString());
        if (state != null) {
            values.put("rank", state.rank().name());
            values.put("prestige", Integer.toString(state.prestigeLevel()));
            values.put("tea_leaves", Integer.toString(state.teaLeaves()));
            values.put("legacy_stars", Integer.toString(state.legacyStars()));
            values.put("server_earnings", Double.toString(state.ledger().serverEarnings()));
            values.put("mcmmo_xp", Long.toString(state.ledger().mcMmoXp()));
            values.put("rabbit_holes", Integer.toString(state.ledger().rabbitHoles()));
            values.put("decree_objectives", Integer.toString(state.ledger().decreeObjectives()));
            values.put("bosses", Integer.toString(state.ledger().bosses()));
            values.put("season", state.seasonId());
        }
        suppliedValues.forEach((key, value) -> {
            String normalized = tokenKey(key);
            if (normalized.matches("[a-z0-9_]+")) values.put(normalized, value == null ? "" : String.valueOf(value));
        });
        return Map.copyOf(values);
    }

    private Map<String, String> entitlementVariables(Map<String, Integer> all, String entitlement, int value) {
        Map<String, String> variables = new HashMap<>();
        variables.put("value", Integer.toString(value));
        variables.put("entitlement", entitlement);
        all.forEach((key, amount) -> variables.put(key.replace('-', '_'), Integer.toString(amount)));
        return variables;
    }

    private String replace(String input, Map<String, String> values) {
        String result = input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private String tokenKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public record RelationshipSummary(String id, boolean configured, boolean enabled, boolean active,
                                      int featureCount, int eventHookCount,
                                      int commandCount, int permissionCount) {}
}
