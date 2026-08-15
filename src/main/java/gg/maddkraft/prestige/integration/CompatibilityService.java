package gg.maddkraft.prestige.integration;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Describes every supported server-stack plugin without linking to optional implementation classes. */
public final class CompatibilityService {
    private final JavaPlugin owner;
    private final List<Definition> definitions;

    public CompatibilityService(JavaPlugin owner) {
        this.owner = owner;
        this.definitions = List.of(
                direct("EconomyShopGUI", "Server-shop sales count as weighted earnings."),
                coexist("FancyHolograms", "Can display MaddPrestige PlaceholderAPI values."),
                coexist("MiniMOTD", "No MOTD or protocol state is replaced."),
                coexist("PurpurExtras", "No server internals or Purpur features are patched."),
                direct("UltimateMobCoins", "Mob Coins can count as weighted earnings."),
                coexist("Veinminer", "Block-processing events are left untouched.", "All-In-One-VeinMiner-TreeFeller"),
                coexist("Chunky", "Chunk generation and pregeneration are left untouched."),
                coexist("Citizens", "NPC metadata and registries are left untouched."),
                coexist("ClickVillagers", "Villager interaction state is left untouched."),
                coexist("CoreProtect", "MaddPrestige does not bypass or replace block logging."),
                coexist("DiscordSRV", "Chat and Discord relays are left untouched."),
                coexist("DriveBackupV2", "SQLite WAL/checkpoint shutdown is backup-safe."),
                bridge("Essentials", "Economy uses Vault; home limits use permission nodes.", "EssentialsX"),
                coexist("EssentialsSpawn", "Spawn and respawn handling are left untouched."),
                coexist("EzATN", "Authentication/network handling is left untouched."),
                bridge("GriefPrevention", "Permanent reward claim blocks use its public player data."),
                coexist("GrimAC", "No movement, packet, or exemption behavior is altered."),
                coexist("InventoryRollbackPlus", "Inventory snapshots and rollback stay authoritative."),
                coexist("LiteBans", "Punishment state and login checks are left untouched."),
                bridge("LuckPerms", "Progression, patron groups, and entitlements use its API."),
                coexist("Maintenance", "MaddPrestige remains safe while player joins are gated."),
                coexist("MaxCrates", "Crate rewards can call the public progress API."),
                direct("mcMMO", "Skill XP is collected from its public XP event."),
                coexist("Multiverse-Core", "World lifecycle remains owned by Multiverse."),
                coexist("Multiverse-Inventories", "No cross-world inventory data is cached."),
                coexist("Multiverse-NetherPortals", "Portal routing is left untouched."),
                coexist("Multiverse-Portals", "Portal routing is left untouched."),
                bridge("PlaceholderAPI", "Registers the maddprestige placeholder expansion."),
                coexist("Plan", "Player analytics and sessions are left untouched."),
                coexist("PlayTimeManager", "Play-time accounting is left untouched."),
                direct("QuickShop-Hikari", "Seller proceeds count as weighted earnings."),
                coexist("SilkSpawners_v2", "Spawner placement and drops are left untouched.", "SilkSpawners", "SilkTouchSpawners"),
                coexist("TAB", "Can consume MaddPrestige PlaceholderAPI values."),
                coexist("TreeFeller", "Tree processing and durability are left untouched.", "All-In-One-VeinMiner-TreeFeller", "TreeAssist"),
                bridge("Vault", "Uses the registered economy provider through Vault API.", "VaultUnlocked"),
                coexist("voicechat", "Voice packets and proximity state are left untouched.", "SimpleVoiceChat"),
                coexist("WorldEdit", "Edit sessions and block history are left untouched."),
                coexist("WorldGuard", "Regions and flags remain authoritative.")
        );
    }

    public List<Entry> entries() {
        List<Entry> result = new ArrayList<>(definitions.size());
        for (Definition definition : definitions) {
            Plugin detected = findPlugin(definition);
            boolean configured = owner.getConfig().getBoolean(
                    "integrations.compatibility." + definition.key() + ".enabled", true);
            result.add(new Entry(definition.name(), definition.mode(), definition.description(), configured,
                    detected != null, detected != null && detected.isEnabled(),
                    detected == null ? "" : detected.getPluginMeta().getVersion()));
        }
        ConfigurationSection customProviders = owner.getConfig().getConfigurationSection("relationships.providers");
        if (customProviders != null) {
            for (String id : customProviders.getKeys(false)) {
                String root = "relationships.providers." + id;
                String name = owner.getConfig().getString(root + ".plugin", id);
                if (name == null || name.isBlank()
                        || result.stream().anyMatch(entry -> entry.name().equalsIgnoreCase(name)
                        || key(entry.name()).equals(key(name)) || key(entry.name()).equals(key(id)))) continue;
                List<String> aliases = new ArrayList<>();
                aliases.add(name);
                aliases.addAll(owner.getConfig().getStringList(root + ".aliases"));
                Definition custom = new Definition(name, key(name), Mode.COEXISTENCE,
                        "Custom relationship: " + String.join(", ", owner.getConfig().getStringList(root + ".features")),
                        List.copyOf(aliases));
                Plugin detected = findPlugin(custom);
                boolean configured = owner.getConfig().getBoolean("relationships.enabled", true)
                        && owner.getConfig().getBoolean(root + ".enabled", true);
                result.add(new Entry(name, Mode.COEXISTENCE, custom.description(), configured,
                        detected != null, detected != null && detected.isEnabled(),
                        detected == null ? "" : detected.getPluginMeta().getVersion()));
            }
        }
        return List.copyOf(result);
    }

    public boolean configured(String name) {
        Definition definition = definitions.stream().filter(entry -> entry.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        return definition == null || owner.getConfig().getBoolean(
                "integrations.compatibility." + definition.key() + ".enabled", true);
    }

    public Plugin findEnabledPlugin(String name) {
        Definition definition = definitions.stream().filter(entry -> entry.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        Plugin plugin = definition == null ? owner.getServer().getPluginManager().getPlugin(name) : findPlugin(definition);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    public long enabledCount() {
        return entries().stream().filter(Entry::enabled).count();
    }

    private Plugin findPlugin(Definition definition) {
        for (String candidate : definition.aliases()) {
            Plugin plugin = owner.getServer().getPluginManager().getPlugin(candidate);
            if (plugin != null) return plugin;
        }
        return null;
    }

    private static Definition direct(String name, String description, String... aliases) {
        return definition(name, Mode.DIRECT_EVENT, description, aliases);
    }

    private static Definition bridge(String name, String description, String... aliases) {
        return definition(name, Mode.API_BRIDGE, description, aliases);
    }

    private static Definition coexist(String name, String description, String... aliases) {
        return definition(name, Mode.COEXISTENCE, description, aliases);
    }

    private static Definition definition(String name, Mode mode, String description, String... aliases) {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.addAll(Arrays.asList(aliases));
        return new Definition(name, key(name), mode, description, List.copyOf(names));
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public enum Mode { DIRECT_EVENT, API_BRIDGE, COEXISTENCE }

    public record Entry(String name, Mode mode, String description, boolean configured,
                        boolean installed, boolean enabled, String version) {}

    private record Definition(String name, String key, Mode mode, String description, List<String> aliases) {}
}
