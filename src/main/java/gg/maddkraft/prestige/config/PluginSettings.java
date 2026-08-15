package gg.maddkraft.prestige.config;

import gg.maddkraft.prestige.model.ContestMetric;
import gg.maddkraft.prestige.model.ProgressionRank;
import gg.maddkraft.prestige.model.Requirements;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PluginSettings {
    private final String databaseFile;
    private final int flushIntervalSeconds;
    private final boolean luckPermsEnabled;
    private final boolean luckPermsRequired;
    private final boolean vaultEnabled;
    private final boolean vaultRequiredForMoney;
    private final boolean mcMmoEnabled;
    private final boolean placeholderEnabled;
    private final Map<ProgressionRank, String> progressionGroups;
    private final Map<ProgressionRank, RankDefinition> ranks;
    private final PrestigeDefinition prestige;
    private final SeasonDefinition season;
    private final HatterDefinition hatter;
    private final Map<String, PerkDefinition> perks;
    private final Map<String, Map<String, Integer>> patronEntitlements;
    private final Map<String, PatronTierDefinition> patronTiers;
    private final int baseHomes;
    private final int baseListings;
    private final int baseClaimBlocks;
    private final Map<String, String> messages;
    private final GuiDefinition gui;
    private final RelationshipSettings relationships;

    private PluginSettings(
            String databaseFile,
            int flushIntervalSeconds,
            boolean luckPermsEnabled,
            boolean luckPermsRequired,
            boolean vaultEnabled,
            boolean vaultRequiredForMoney,
            boolean mcMmoEnabled,
            boolean placeholderEnabled,
            Map<ProgressionRank, String> progressionGroups,
            Map<ProgressionRank, RankDefinition> ranks,
            PrestigeDefinition prestige,
            SeasonDefinition season,
            HatterDefinition hatter,
            Map<String, PerkDefinition> perks,
            Map<String, Map<String, Integer>> patronEntitlements,
            Map<String, PatronTierDefinition> patronTiers,
            int baseHomes,
            int baseListings,
            int baseClaimBlocks,
            Map<String, String> messages,
            GuiDefinition gui,
            RelationshipSettings relationships
    ) {
        this.databaseFile = databaseFile;
        this.flushIntervalSeconds = flushIntervalSeconds;
        this.luckPermsEnabled = luckPermsEnabled;
        this.luckPermsRequired = luckPermsRequired;
        this.vaultEnabled = vaultEnabled;
        this.vaultRequiredForMoney = vaultRequiredForMoney;
        this.mcMmoEnabled = mcMmoEnabled;
        this.placeholderEnabled = placeholderEnabled;
        this.progressionGroups = Map.copyOf(progressionGroups);
        this.ranks = Map.copyOf(ranks);
        this.prestige = prestige;
        this.season = season;
        this.hatter = hatter;
        this.perks = Map.copyOf(perks);
        this.patronEntitlements = Map.copyOf(patronEntitlements);
        this.patronTiers = Map.copyOf(patronTiers);
        this.baseHomes = baseHomes;
        this.baseListings = baseListings;
        this.baseClaimBlocks = baseClaimBlocks;
        this.messages = Map.copyOf(messages);
        this.gui = gui;
        this.relationships = relationships;
    }

    public static PluginSettings load(FileConfiguration config) {
        Map<ProgressionRank, String> groups = new EnumMap<>(ProgressionRank.class);
        for (ProgressionRank rank : ProgressionRank.values()) {
            groups.put(rank, config.getString("integrations.luckperms.progression-groups." + rank.name(), rank.name().toLowerCase()));
        }

        Map<ProgressionRank, RankDefinition> rankDefinitions = new EnumMap<>(ProgressionRank.class);
        for (ProgressionRank rank : ProgressionRank.values()) {
            String root = "progression.ranks." + rank.name();
            rankDefinitions.put(rank, new RankDefinition(
                    config.getString(root + ".display-name", titleCase(rank.name())),
                    readRequirements(config, root)
            ));
        }

        PrestigeDefinition prestige = new PrestigeDefinition(
                new Requirements(
                        nonNegative(config.getDouble("prestige.base-fee")),
                        nonNegative(config.getDouble("prestige.base-server-earnings")),
                        Math.max(0L, config.getLong("prestige.base-mcmmo-xp")),
                        Math.max(0, config.getInt("prestige.base-rabbit-holes")),
                        Math.max(0, config.getInt("prestige.base-decree-objectives")),
                        Math.max(0, config.getInt("prestige.base-bosses"))
                ),
                Math.max(0.0, config.getDouble("prestige.linear-scale-per-prestige", 0.18)),
                Math.max(0, config.getInt("prestige.cooldown-seconds", 300)),
                Math.max(0, config.getInt("prestige.base-tea-leaves", 1)),
                Math.max(1, config.getInt("prestige.bonus-tea-leaf-every", 5)),
                Math.max(0.0, config.getDouble("prestige.rankup-discount-per-prestige", 0.01)),
                clamp(config.getDouble("prestige.maximum-rankup-discount", 0.15), 0.0, 0.95),
                readMilestones(config.getConfigurationSection("prestige.milestones"))
        );

        SeasonDefinition season = new SeasonDefinition(
                config.getString("season.default-id", "chapter-1"),
                config.getString("season.default-name", "Chapter One"),
                Math.max(1, config.getInt("season.target-length-days", 150)),
                Math.max(1, config.getInt("season.legacy-star-every-prestiges", 5)),
                config.getBoolean("season.late-join-catchup.enabled", true),
                Math.max(0, config.getInt("season.late-join-catchup.starts-after-day", 75)),
                Math.max(0.0, config.getDouble("season.late-join-catchup.requirement-reduction-per-week", 0.025)),
                clamp(config.getDouble("season.late-join-catchup.maximum-reduction", 0.20), 0.0, 0.95)
        );

        Material hatMaterial = Material.matchMaterial(config.getString("maddhatter.hat-material", "LEATHER_HELMET"));
        if (hatMaterial == null || hatMaterial == Material.AIR || hatMaterial == Material.CAVE_AIR || hatMaterial == Material.VOID_AIR) {
            hatMaterial = Material.LEATHER_HELMET;
        }
        HatterDefinition hatter = new HatterDefinition(
                Math.max(0, config.getInt("maddhatter.minimum-prestige", 10)),
                Math.max(1, config.getInt("maddhatter.inactivity-days", 14)),
                config.getString("maddhatter.title", "<gold><bold>THE MADDHATTER</bold></gold>"),
                hatMaterial,
                Math.max(1, config.getInt("maddhatter.contest.default-duration-days", 28)),
                ContestMetric.parse(config.getString("maddhatter.contest.default-metric", "MCMMO_XP")),
                config.getString("maddhatter.staff-permission", "maddprestige.staff")
        );

        Map<String, PerkDefinition> perks = new HashMap<>();
        perks.put("homes", readPerk(config, "rewards.prestige-perks.homes", 2, 1, 10));
        perks.put("auction-listings", readPerk(config, "rewards.prestige-perks.auction-listings", 2, 1, 15));
        perks.put("claim-blocks", readPerk(config, "rewards.prestige-perks.claim-blocks", 1, 250, 5000));

        Map<String, Map<String, Integer>> entitlements = new HashMap<>();
        entitlements.put("homes", readIntMap(config.getConfigurationSection("rewards.patron-permissions.homes")));
        entitlements.put("auction-listings", readIntMap(config.getConfigurationSection("rewards.patron-permissions.auction-listings")));

        Map<String, PatronTierDefinition> patronTiers = new HashMap<>();
        ConfigurationSection tierSection = config.getConfigurationSection("patron.tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                String normalized = key.trim().toUpperCase().replace('-', '_').replace(' ', '_');
                String root = "patron.tiers." + key;
                String group = config.getString(root + ".luckperms-group", "").trim().toLowerCase();
                if (!group.isBlank()) {
                    patronTiers.put(normalized, new PatronTierDefinition(
                            config.getString(root + ".display-name", titleCase(normalized.replace('_', ' '))),
                            group,
                            config.getString(root + ".entitlement-permission", "maddkraft.patron." + normalized.toLowerCase().replace("_", "")),
                            config.getInt(root + ".priority", 0)
                    ));
                }
            }
        }

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection messageSection = config.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) messages.put(key, messageSection.getString(key, ""));
        }

        Map<String, String> guiTitles = readStringMap(config.getConfigurationSection("gui.titles"));
        Map<String, Integer> guiSlots = readIntMap(config.getConfigurationSection("gui.slots"));
        Map<String, Material> guiMaterials = readMaterialMap(config.getConfigurationSection("gui.materials"));
        int requestedSize = config.getInt("gui.size", 54);
        int guiSize = Math.max(27, Math.min(54, ((requestedSize + 8) / 9) * 9));
        GuiDefinition gui = new GuiDefinition(
                config.getBoolean("gui.enabled", true),
                guiSize,
                config.getBoolean("gui.player-settings-enabled", true),
                config.getBoolean("gui.staff-menu-enabled", true),
                config.getString("gui.staff-permission", "maddprestige.admin.gui"),
                guiTitles,
                guiSlots,
                guiMaterials,
                Math.max(1, config.getInt("gui.staff-controls.tea-step", 1)),
                Math.max(1, config.getInt("gui.staff-controls.prestige-step", 1)),
                Math.max(1.0, config.getDouble("gui.staff-controls.server-earnings-step", 1000.0)),
                Math.max(1L, config.getLong("gui.staff-controls.mcmmo-xp-step", 1000L)),
                Math.max(1, config.getInt("gui.staff-controls.objective-step", 1))
        );

        Map<String, ExternalRelationship> providers = new HashMap<>();
        ConfigurationSection providerSection = config.getConfigurationSection("relationships.providers");
        if (providerSection != null) {
            for (String id : providerSection.getKeys(false)) {
                if (!id.matches("[A-Za-z0-9_-]+")) continue;
                String root = "relationships.providers." + id;
                String pluginName = config.getString(root + ".plugin", id);
                List<String> pluginNames = new ArrayList<>();
                if (pluginName != null && !pluginName.isBlank()) pluginNames.add(pluginName.trim());
                for (String alias : config.getStringList(root + ".aliases")) {
                    if (!alias.isBlank() && !pluginNames.contains(alias.trim())) pluginNames.add(alias.trim());
                }
                Map<String, List<String>> commands = readStringListMap(config.getConfigurationSection(root + ".commands"));
                List<ConfiguredEventHook> eventHooks = new ArrayList<>();
                ConfigurationSection hookSection = config.getConfigurationSection(root + ".event-hooks");
                if (hookSection != null) {
                    for (String hookId : hookSection.getKeys(false)) {
                        if (!hookId.matches("[A-Za-z0-9_-]+")) continue;
                        String hookRoot = root + ".event-hooks." + hookId;
                        String eventClass = config.getString(hookRoot + ".event-class", "").trim();
                        String playerPath = config.getString(hookRoot + ".player-path", "getPlayer").trim();
                        String progressType = config.getString(hookRoot + ".progress-type", "").trim().toUpperCase();
                        if (eventClass.isBlank() || playerPath.isBlank() || progressType.isBlank()) continue;
                        eventHooks.add(new ConfiguredEventHook(
                                hookId,
                                eventClass,
                                playerPath,
                                config.getString(hookRoot + ".amount-path", "").trim(),
                                nonNegative(config.getDouble(hookRoot + ".fixed-amount", 1.0)),
                                nonNegative(config.getDouble(hookRoot + ".weight", 1.0)),
                                progressType,
                                config.getString(hookRoot + ".source", id + ":" + hookId),
                                readConditions(config.getStringList(hookRoot + ".equals")),
                                config.getBoolean(hookRoot + ".ignore-cancelled", true)
                        ));
                    }
                }
                Map<String, EntitlementPermissionMapping> mappings = new HashMap<>();
                ConfigurationSection entitlementSection = config.getConfigurationSection(root + ".entitlement-permissions");
                if (entitlementSection != null) {
                    for (String entitlement : entitlementSection.getKeys(false)) {
                        String mappingRoot = root + ".entitlement-permissions." + entitlement;
                        mappings.put(entitlement.toLowerCase().replace('_', '-'), new EntitlementPermissionMapping(
                                Math.max(0, config.getInt(mappingRoot + ".minimum-value", 0)),
                                cleanPermissionValues(config.getStringList(mappingRoot + ".nodes"), true),
                                cleanPermissionValues(config.getStringList(mappingRoot + ".clear-prefixes"), false),
                                cleanPermissionValues(config.getStringList(mappingRoot + ".clear-nodes"), false)
                        ));
                    }
                }
                providers.put(id, new ExternalRelationship(
                        config.getBoolean(root + ".enabled", true),
                        config.getString(root + ".display-name", pluginName == null ? id : pluginName),
                        List.copyOf(pluginNames),
                        config.getBoolean(root + ".require-plugin", true),
                        List.copyOf(config.getStringList(root + ".features")),
                        List.copyOf(eventHooks),
                        Map.copyOf(commands),
                        Map.copyOf(mappings)
                ));
            }
        }
        RelationshipSettings relationships = new RelationshipSettings(
                config.getBoolean("relationships.enabled", true),
                config.getBoolean("relationships.emit-progress-actions", false),
                Math.max(1, Math.min(250, config.getInt("relationships.max-commands-per-trigger", 50))),
                Math.max(1, Math.min(10, config.getInt("relationships.max-trigger-depth", 3))),
                config.getBoolean("relationships.log-dispatched-commands", false),
                config.getBoolean("relationships.skip-commands-with-unresolved-tokens", true),
                config.getStringList("relationships.blocked-command-roots").stream()
                        .map(String::trim).map(String::toLowerCase).filter(value -> value.matches("[a-z0-9:_-]+"))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                Map.copyOf(providers)
        );

        return new PluginSettings(
                config.getString("database.file", "maddprestige.db"),
                Math.max(5, config.getInt("database.flush-interval-seconds", 30)),
                config.getBoolean("integrations.luckperms.enabled", true),
                config.getBoolean("integrations.luckperms.required", false),
                config.getBoolean("integrations.vault.enabled", true),
                config.getBoolean("integrations.vault.required-for-money-objectives", true),
                config.getBoolean("integrations.mcmmo.enabled", true),
                config.getBoolean("integrations.placeholderapi.enabled", true),
                groups,
                rankDefinitions,
                prestige,
                season,
                hatter,
                perks,
                entitlements,
                patronTiers,
                Math.max(0, config.getInt("rewards.base.homes", 1)),
                Math.max(0, config.getInt("rewards.base.auction-listings", 3)),
                Math.max(0, config.getInt("rewards.base.claim-blocks", 500)),
                messages,
                gui,
                relationships
        );
    }

    private static Requirements readRequirements(FileConfiguration config, String root) {
        return new Requirements(
                nonNegative(config.getDouble(root + ".cost")),
                nonNegative(config.getDouble(root + ".server-earnings")),
                Math.max(0L, config.getLong(root + ".mcmmo-xp")),
                Math.max(0, config.getInt(root + ".rabbit-holes")),
                Math.max(0, config.getInt(root + ".decree-objectives")),
                Math.max(0, config.getInt(root + ".bosses"))
        );
    }

    private static Map<Integer, List<String>> readMilestones(ConfigurationSection section) {
        Map<Integer, List<String>> milestones = new HashMap<>();
        if (section == null) return milestones;
        for (String rawLevel : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(rawLevel);
                List<String> messages = new ArrayList<>(section.getStringList(rawLevel + ".messages"));
                milestones.put(level, List.copyOf(messages));
            } catch (NumberFormatException ignored) {
            }
        }
        return milestones;
    }

    private static PerkDefinition readPerk(FileConfiguration config, String root, int defaultCost, int defaultAmount, int defaultMax) {
        return new PerkDefinition(
                Math.max(1, config.getInt(root + ".points-per-level", defaultCost)),
                Math.max(1, config.getInt(root + ".amount-per-level", defaultAmount)),
                Math.max(1, config.getInt(root + ".maximum", defaultMax))
        );
    }

    private static Map<String, Integer> readIntMap(ConfigurationSection section) {
        Map<String, Integer> result = new HashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) result.put(key, Math.max(0, section.getInt(key)));
        return Map.copyOf(result);
    }

    private static Map<String, String> readStringMap(ConfigurationSection section) {
        Map<String, String> result = new HashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) result.put(key, section.getString(key, ""));
        return Map.copyOf(result);
    }

    private static Map<String, Material> readMaterialMap(ConfigurationSection section) {
        Map<String, Material> result = new HashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(key, ""));
            if (material != null && material != Material.AIR && material != Material.CAVE_AIR
                    && material != Material.VOID_AIR) result.put(key, material);
        }
        return Map.copyOf(result);
    }

    private static Map<String, List<String>> readStringListMap(ConfigurationSection section) {
        Map<String, List<String>> result = new HashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            List<String> clean = section.getStringList(key).stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank() && !value.contains("\n") && !value.contains("\r"))
                    .map(value -> value.length() > 512 ? value.substring(0, 512) : value)
                    .toList();
            result.put(key.toLowerCase().replace('_', '-'), clean);
        }
        return result;
    }

    private static List<String> cleanPermissionValues(List<String> values, boolean templates) {
        return values.stream().map(String::trim).map(String::toLowerCase)
                .filter(value -> !value.isBlank())
                .filter(value -> value.matches(templates ? "[a-z0-9_.{}-]+" : "[a-z0-9_.-]+"))
                .toList();
    }

    private static Map<String, String> readConditions(List<String> values) {
        Map<String, String> result = new HashMap<>();
        for (String value : values) {
            int split = value.indexOf('=');
            if (split <= 0 || split == value.length() - 1) continue;
            String path = value.substring(0, split).trim();
            String expected = value.substring(split + 1).trim();
            if (path.matches("[A-Za-z_$][A-Za-z0-9_$.]*") && !expected.isBlank()) result.put(path, expected);
        }
        return Map.copyOf(result);
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public String databaseFile() { return databaseFile; }
    public int flushIntervalSeconds() { return flushIntervalSeconds; }
    public boolean luckPermsEnabled() { return luckPermsEnabled; }
    public boolean luckPermsRequired() { return luckPermsRequired; }
    public boolean vaultEnabled() { return vaultEnabled; }
    public boolean vaultRequiredForMoney() { return vaultRequiredForMoney; }
    public boolean mcMmoEnabled() { return mcMmoEnabled; }
    public boolean placeholderEnabled() { return placeholderEnabled; }
    public Map<ProgressionRank, String> progressionGroups() { return progressionGroups; }
    public Map<ProgressionRank, RankDefinition> ranks() { return ranks; }
    public PrestigeDefinition prestige() { return prestige; }
    public SeasonDefinition season() { return season; }
    public HatterDefinition hatter() { return hatter; }
    public Map<String, PerkDefinition> perks() { return perks; }
    public Map<String, Map<String, Integer>> patronEntitlements() { return patronEntitlements; }
    public Map<String, PatronTierDefinition> patronTiers() { return patronTiers; }
    public int baseHomes() { return baseHomes; }
    public int baseListings() { return baseListings; }
    public int baseClaimBlocks() { return baseClaimBlocks; }
    public String message(String key, String fallback) { return messages.getOrDefault(key, fallback); }
    public GuiDefinition gui() { return gui; }
    public RelationshipSettings relationships() { return relationships; }

    public record RankDefinition(String displayName, Requirements requirements) {}
    public record PrestigeDefinition(
            Requirements baseRequirements,
            double linearScale,
            int cooldownSeconds,
            int baseTeaLeaves,
            int bonusTeaLeafEvery,
            double rankupDiscountPerPrestige,
            double maximumRankupDiscount,
            Map<Integer, List<String>> milestoneMessages
    ) {}
    public record SeasonDefinition(
            String defaultId,
            String defaultName,
            int targetLengthDays,
            int legacyStarEveryPrestiges,
            boolean catchupEnabled,
            int catchupStartsAfterDay,
            double catchupReductionPerWeek,
            double maximumCatchupReduction
    ) {}
    public record HatterDefinition(
            int minimumPrestige,
            int inactivityDays,
            String title,
            Material hatMaterial,
            int defaultContestDurationDays,
            ContestMetric defaultMetric,
            String staffPermission
    ) {}
    public record PerkDefinition(int teaLeafCost, int amountPerLevel, int maximumValue) {}
    public record PatronTierDefinition(String displayName, String luckPermsGroup, String entitlementPermission, int priority) {}
    public record GuiDefinition(
            boolean enabled,
            int size,
            boolean playerSettingsEnabled,
            boolean staffMenuEnabled,
            String staffPermission,
            Map<String, String> titles,
            Map<String, Integer> slots,
            Map<String, Material> materials,
            int teaStep,
            int prestigeStep,
            double serverEarningsStep,
            long mcMmoXpStep,
            int objectiveStep
    ) {
        public String title(String key, String fallback) { return titles.getOrDefault(key, fallback); }
        public int slot(String key, int fallback) { return slots.getOrDefault(key, fallback); }
        public Material material(String key, Material fallback) { return materials.getOrDefault(key, fallback); }
    }
    public record RelationshipSettings(boolean enabled, boolean emitProgressActions,
                                       int maxCommandsPerTrigger,
                                       int maxTriggerDepth, boolean logDispatchedCommands,
                                       boolean skipCommandsWithUnresolvedTokens,
                                       java.util.Set<String> blockedCommandRoots,
                                       Map<String, ExternalRelationship> providers) {}
    public record ExternalRelationship(boolean enabled, String displayName, List<String> pluginNames,
                                       boolean requirePlugin, List<String> features,
                                       List<ConfiguredEventHook> eventHooks,
                                       Map<String, List<String>> commands,
                                       Map<String, EntitlementPermissionMapping> entitlementPermissions) {}
    public record ConfiguredEventHook(String id, String eventClass, String playerPath,
                                      String amountPath, double fixedAmount, double weight,
                                      String progressType, String source,
                                      Map<String, String> equalsConditions, boolean ignoreCancelled) {}
    public record EntitlementPermissionMapping(int minimumValue, List<String> nodes,
                                               List<String> clearPrefixes, List<String> clearNodes) {}
}
