package gg.maddkraft.prestige.ui;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.EconomyBridge;
import gg.maddkraft.prestige.integration.CompatibilityService;
import gg.maddkraft.prestige.model.HatterContest;
import gg.maddkraft.prestige.model.HatterHolder;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.Requirements;
import gg.maddkraft.prestige.service.ActionResult;
import gg.maddkraft.prestige.service.ContestService;
import gg.maddkraft.prestige.service.EntitlementService;
import gg.maddkraft.prestige.service.HatterService;
import gg.maddkraft.prestige.service.PrestigeService;
import gg.maddkraft.prestige.service.PlayerPreferenceService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.service.RankService;
import gg.maddkraft.prestige.service.SeasonService;
import gg.maddkraft.prestige.storage.Database;
import gg.maddkraft.prestige.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MenuService implements Listener {
    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final RankService ranks;
    private final PrestigeService prestiges;
    private final EntitlementService entitlements;
    private final SeasonService seasons;
    private final ContestService contests;
    private final HatterService hatter;
    private final EconomyBridge economy;
    private final PlayerPreferenceService preferences;
    private final CompatibilityService compatibility;
    private volatile PluginSettings settings;

    public MenuService(
            JavaPlugin plugin,
            ProfileService profiles,
            RankService ranks,
            PrestigeService prestiges,
            EntitlementService entitlements,
            SeasonService seasons,
            ContestService contests,
            HatterService hatter,
            EconomyBridge economy,
            PlayerPreferenceService preferences,
            CompatibilityService compatibility,
            PluginSettings settings
    ) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.prestiges = prestiges;
        this.entitlements = entitlements;
        this.seasons = seasons;
        this.contests = contests;
        this.hatter = hatter;
        this.economy = economy;
        this.preferences = preferences;
        this.compatibility = compatibility;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public void open(Player player, Page page) {
        if (!settings.gui().enabled()) {
            Text.send(player, settings, "<yellow>The inventory GUI is disabled by staff.</yellow>");
            return;
        }
        PlayerState state = profiles.get(player.getUniqueId()).orElse(null);
        if (state == null) {
            Text.send(player, settings, settings.message("profile-loading", "<yellow>Your profile is loading.</yellow>"));
            return;
        }
        MenuHolder holder = new MenuHolder(page, player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, settings.gui().size(), Text.mm(title(page)));
        holder.inventory = inventory;
        fill(inventory);
        drawNavigation(inventory, page);
        switch (page) {
            case RANK -> drawRank(inventory, player, state);
            case PRESTIGE -> drawPrestige(inventory, player, state);
            case REWARDS -> drawRewards(inventory, player, state);
            case SEASON -> drawSeason(inventory, state);
            case HATTER -> drawHatter(inventory, player);
            case SETTINGS -> drawSettings(inventory, player);
        }
        player.openInventory(inventory);
    }

    public void openPreferred(Player player) {
        String preferred = preferences.get(player.getUniqueId()).defaultPage();
        try {
            Page page = Page.valueOf(preferred);
            open(player, page == Page.SETTINGS ? Page.PRESTIGE : page);
        } catch (IllegalArgumentException ignored) {
            open(player, Page.PRESTIGE);
        }
    }

    private String title(Page page) {
        return switch (page) {
            case RANK -> settings.gui().title("rank", "<dark_purple><bold>MaddKraft</bold></dark_purple> <gray>• Rank Up</gray>");
            case PRESTIGE -> settings.gui().title("prestige", "<gold><bold>Through the Looking Glass</bold></gold>");
            case REWARDS -> settings.gui().title("rewards", "<green><bold>The Tea Table</bold></green>");
            case SEASON -> settings.gui().title("season", "<aqua><bold>A New Chapter</bold></aqua>");
            case HATTER -> settings.gui().title("hatter", "<gradient:#e6b94e:#8060f2><bold>The MaddHatter</bold></gradient>");
            case SETTINGS -> settings.gui().title("settings", "<yellow><bold>My Prestige Settings</bold></yellow>");
        };
    }

    private void drawRank(Inventory inventory, Player player, PlayerState state) {
        Optional<RankService.RankTarget> target = ranks.target(state, Instant.now());
        put(inventory, "rank-overview", 13, item(Material.NAME_TAG, "<light_purple><bold>Free Progression</bold></light_purple>", List.of(
                "<gray>Current:</gray> <white>" + display(state.rank().name()) + "</white>",
                "<gray>Prestige:</gray> <gold>" + state.prestigeLevel() + "</gold>",
                "",
                "<dark_gray>Earned through play, never purchased.</dark_gray>"
        )));
        if (target.isEmpty()) {
            put(inventory, "rank-objectives", 22, item(Material.END_CRYSTAL, "<gold><bold>You are Unbound</bold></gold>", List.of(
                    "<gray>Your next step is a prestige.</gray>",
                    "<yellow>Use the Prestige tab below.</yellow>"
            )));
            return;
        }
        RankService.RankTarget next = target.get();
        put(inventory, "rank-objectives", 22, objectiveItem(Material.WRITABLE_BOOK, "<white><bold>Path to " + next.displayName() + "</bold></white>",
                player, state, next.requirements()));
        put(inventory, "rank-action", 31, item(
                state.ledger().satisfies(next.requirements()) ? Material.LIME_DYE : Material.GRAY_DYE,
                "<green><bold>Advance to " + next.displayName() + "</bold></green>",
                List.of("<gray>Click to validate every objective and pay the fee.</gray>")
        ));
    }

    private void drawPrestige(Inventory inventory, Player player, PlayerState state) {
        PrestigeService.PrestigeTarget target = prestiges.target(state, Instant.now());
        put(inventory, "prestige-overview", 13, item(Material.NETHER_STAR, "<gold><bold>Prestige " + target.level() + "</bold></gold>", List.of(
                "<gray>Reset your free progression rank and run ledger.</gray>",
                "<gray>Keep patron ranks, cosmetics, and lifetime records.</gray>",
                "",
                "<green>Reward: " + target.teaLeaves() + " Tea Leaves</green>",
                "<light_purple>Future rank-up discount: " + Math.round(target.resultingRankupDiscount() * 100.0) + "%</light_purple>"
        )));
        put(inventory, "prestige-objectives", 22, objectiveItem(Material.RECOVERY_COMPASS, "<white><bold>Looking Glass Requirements</bold></white>",
                player, state, target.requirements()));
        put(inventory, "prestige-action", 31, item(Material.AMETHYST_SHARD, "<gradient:#e6b94e:#8060f2><bold>Prestige Now</bold></gradient>", List.of(
                "<red>This resets your run ledger and free rank.</red>",
                "<yellow><bold>Shift-click to confirm.</bold></yellow>"
        )));
    }

    private ItemStack objectiveItem(Material material, String name, Player player, PlayerState state, Requirements requirements) {
        List<String> lore = new ArrayList<>();
        boolean compact = preferences.get(player.getUniqueId()).compactNumbers();
        lore.add(progress("Balance", economy.available() ? economy.balance(player) : 0.0, requirements.cost(), economy.available(), compact));
        lore.add(progress("Server earnings", state.ledger().serverEarnings(), requirements.serverEarnings(), true, compact));
        lore.add(progress("mcMMO XP", state.ledger().mcMmoXp(), requirements.mcMmoXp(), true, compact));
        lore.add(progress("Rabbit Holes", state.ledger().rabbitHoles(), requirements.rabbitHoles(), true, compact));
        lore.add(progress("Decree objectives", state.ledger().decreeObjectives(), requirements.decreeObjectives(), true, compact));
        lore.add(progress("Bosses", state.ledger().bosses(), requirements.bosses(), true, compact));
        return item(material, name, lore);
    }

    private void drawRewards(Inventory inventory, Player player, PlayerState state) {
        EntitlementService.Entitlements current = entitlements.calculate(player, state);
        put(inventory, "rewards-balance", 13, item(Material.FLOWER_BANNER_PATTERN, "<green><bold>Tea Leaves: " + state.teaLeaves() + "</bold></green>", List.of(
                "<gray>Earn Tea Leaves by prestiging.</gray>",
                "<gray>Patron and earned limits use the higher value, not both added.</gray>"
        )));
        drawPerk(inventory, settings.gui().slot("rewards-homes", 20), Material.RED_BED, "homes", "Homes", current.homes(), state.homesPerkLevel());
        drawPerk(inventory, settings.gui().slot("rewards-listings", 22), Material.CHEST, "auction-listings", "Player Shops", current.auctionListings(), state.listingsPerkLevel());
        drawPerk(inventory, settings.gui().slot("rewards-claims", 24), Material.GOLDEN_SHOVEL, "claim-blocks", "Permanent Claim Blocks", current.claimBlocks(), state.claimPerkLevel());
    }

    private void drawPerk(Inventory inventory, int slot, Material material, String key, String displayName, int currentValue, int level) {
        PluginSettings.PerkDefinition perk = settings.perks().get(key);
        int nextValue = (key.equals("homes") ? settings.baseHomes()
                : key.equals("auction-listings") ? settings.baseListings() : settings.baseClaimBlocks())
                + (level + 1) * perk.amountPerLevel();
        boolean maximum = nextValue > perk.maximumValue();
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item(material, "<gold><bold>" + displayName + "</bold></gold>", List.of(
                "<gray>Current allowance:</gray> <white>" + currentValue + "</white>",
                maximum ? "<green>Maximum purchased.</green>" : "<yellow>Next: +" + perk.amountPerLevel() + " for " + perk.teaLeafCost() + " Tea Leaves</yellow>",
                maximum ? "" : "<gray>Click to purchase.</gray>"
        )));
    }

    private void drawSeason(Inventory inventory, PlayerState state) {
        var season = seasons.current();
        put(inventory, "season-overview", 13, item(Material.WRITTEN_BOOK, "<aqua><bold>" + season.displayName() + "</bold></aqua>", List.of(
                "<gray>Chapter:</gray> <white>" + season.number() + "</white>",
                "<gray>Status:</gray> <white>" + display(season.status().name()) + "</white>",
                "<gray>Day:</gray> <white>" + seasons.dayNumber(Instant.now()) + "</white>",
                "<gray>Time remaining:</gray> <white>" + Text.duration(seasons.remaining(Instant.now())) + "</white>"
        )));
        put(inventory, "season-player", 22, item(Material.ECHO_SHARD, "<light_purple><bold>Your Chapter</bold></light_purple>", List.of(
                "<gray>Prestige:</gray> <gold>" + state.prestigeLevel() + "</gold>",
                "<gray>Tea Leaves:</gray> <green>" + state.teaLeaves() + "</green>",
                "<gray>Lifetime Legacy Stars:</gray> <yellow>" + state.legacyStars() + "</yellow>",
                "",
                "<dark_gray>Chapter transitions are staged during maintenance.</dark_gray>"
        )));
    }

    private void drawHatter(Inventory inventory, Player viewer) {
        Optional<HatterHolder> current = hatter.holder();
        String name = current.map(holder -> playerName(holder.playerId())).orElse("Vacant");
        put(inventory, "hatter-holder", 13, item(Material.LEATHER_HELMET, "<gradient:#e6b94e:#8060f2><bold>" + name + "</bold></gradient>", List.of(
                current.map(holder -> "<gray>Reigning since " + holder.since().toString().substring(0, 10) + "</gray>").orElse("<gray>No current MaddHatter.</gray>"),
                "<gold>One title. One authentic hat. No gameplay perks.</gold>"
        )));
        Optional<HatterContest> active = contests.activeContest();
        List<String> contestLore = new ArrayList<>();
        if (active.isPresent()) {
            HatterContest contest = active.get();
            contestLore.add("<gray>Metric:</gray> <white>" + display(contest.metric().name()) + "</white>");
            contestLore.add("<gray>Minimum prestige:</gray> <white>" + contest.minimumPrestige() + "</white>");
            contestLore.add("<gray>Ends:</gray> <white>" + contest.endsAt().toString().substring(0, 10) + "</white>");
            contestLore.add("");
            List<Database.ScoreEntry> top = contests.leaderboard(5);
            for (int index = 0; index < top.size(); index++) {
                Database.ScoreEntry entry = top.get(index);
                contestLore.add("<gold>#" + (index + 1) + "</gold> <white>" + playerName(entry.playerId())
                        + "</white> <gray>— " + Text.number(entry.score()) + "</gray>");
            }
        } else {
            contestLore.add("<gray>No Hatter's Contest is active.</gray>");
        }
        put(inventory, "hatter-contest", 22, item(Material.CLOCK, "<gold><bold>Hatter's Contest</bold></gold>", contestLore));
        if (current.isPresent() && current.get().playerId().equals(viewer.getUniqueId())) {
            put(inventory, "hatter-claim", 31, item(Material.CHEST, "<green><bold>Reclaim the Authentic Hat</bold></green>", List.of(
                    "<gray>Returns the unique cosmetic if yours is missing.</gray>"
            )));
        }
    }

    private void drawNavigation(Inventory inventory, Page selected) {
        nav(inventory, "nav-rank", 45, Material.NAME_TAG, Page.RANK, selected, "Ranks");
        nav(inventory, "nav-prestige", 46, Material.NETHER_STAR, Page.PRESTIGE, selected, "Prestige");
        nav(inventory, "nav-rewards", 47, Material.FLOWER_BANNER_PATTERN, Page.REWARDS, selected, "Rewards");
        nav(inventory, "nav-season", 48, Material.WRITTEN_BOOK, Page.SEASON, selected, "Chapter");
        nav(inventory, "nav-hatter", 49, Material.LEATHER_HELMET, Page.HATTER, selected, "MaddHatter");
        if (settings.gui().playerSettingsEnabled()) {
            nav(inventory, "nav-settings", 51, Material.COMPARATOR, Page.SETTINGS, selected, "Settings");
        }
        if (settings.gui().staffMenuEnabled() && holderCanUseStaff(inventory)) {
            int slot = settings.gui().slot("nav-staff", 53);
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item(
                    settings.gui().material("nav-staff", Material.COMMAND_BLOCK),
                    "<red><bold>Staff Panel</bold></red>", List.of("<yellow>Click to open</yellow>")));
        }
    }

    private boolean holderCanUseStaff(Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof MenuHolder holder)) return false;
        Player player = Bukkit.getPlayer(holder.viewer);
        return player != null && player.hasPermission(settings.gui().staffPermission());
    }

    private void nav(Inventory inventory, String key, int fallback, Material material, Page page, Page selected, String name) {
        int slot = settings.gui().slot(key, fallback);
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item(settings.gui().material(key, material),
                (page == selected ? "<green><bold>" : "<gray>") + name + (page == selected ? "</bold></green>" : "</gray>"),
                List.of(page == selected ? "<dark_gray>Current page</dark_gray>" : "<yellow>Click to open</yellow>")));
    }

    private void drawSettings(Inventory inventory, Player player) {
        Database.PlayerPreferences current = preferences.get(player.getUniqueId());
        put(inventory, "settings-sounds", 20, toggleItem("Menu sounds", current.menuSounds(),
                "Play feedback sounds after menu actions."));
        put(inventory, "settings-numbers", 22, toggleItem("Compact numbers", current.compactNumbers(),
                "Display large objectives as 1.2M instead of 1,200,000."));
        put(inventory, "settings-integrations", 24, toggleItem("Integration notices", current.showIntegrationStatus(),
                "Show the live compatibility count in this menu."));
        put(inventory, "settings-default-page", 31, item(Material.COMPASS,
                "<gold><bold>Default page: " + display(current.defaultPage()) + "</bold></gold>", List.of(
                        "<gray>Used by /prestige menu.</gray>",
                        "<yellow>Click to cycle.</yellow>")));
        if (current.showIntegrationStatus()) {
            put(inventory, "settings-status", 13, item(Material.OBSERVER,
                    "<aqua><bold>Server compatibility</bold></aqua>", List.of(
                            "<green>" + compatibility.enabledCount() + "</green><gray> supported plugins currently enabled.</gray>",
                            "<dark_gray>Staff can inspect every adapter in /mp gui.</dark_gray>")));
        }
    }

    private ItemStack toggleItem(String name, boolean enabled, String description) {
        return item(settings.gui().material(enabled ? "enabled" : "disabled", enabled ? Material.LIME_DYE : Material.GRAY_DYE),
                (enabled ? "<green><bold>" : "<gray><bold>") + name + "</bold>" + (enabled ? "</green>" : "</gray>"),
                List.of("<gray>" + description + "</gray>", "", enabled ? "<green>Enabled</green>" : "<red>Disabled</red>",
                        "<yellow>Click to toggle.</yellow>"));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(settings.gui().material("filler", Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private void put(Inventory inventory, String key, int fallback, ItemStack item) {
        int slot = settings.gui().slot(key, fallback);
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.viewer)) return;
        int slot = event.getRawSlot();
        Page navigation = navigationAt(slot);
        if (navigation != null) {
            open(player, navigation);
            return;
        }
        if (slot == settings.gui().slot("nav-staff", 53)
                && player.hasPermission(settings.gui().staffPermission())) {
            player.performCommand("maddprestige gui");
            return;
        }

        PlayerState state = profiles.get(player.getUniqueId()).orElse(null);
        if (state == null) return;
        ActionResult result = null;
        if (holder.page == Page.RANK && slot == settings.gui().slot("rank-action", 31)) result = ranks.rankUp(player);
        if (holder.page == Page.PRESTIGE && slot == settings.gui().slot("prestige-action", 31)) {
            if (!event.isShiftClick()) {
                Text.send(player, settings, "<yellow>Shift-click the prestige button to confirm the reset and fee.</yellow>");
                return;
            }
            result = prestiges.prestige(player);
            if (result.success()) {
                for (String milestone : prestiges.milestoneMessages(state.prestigeLevel())) Text.send(player, settings, milestone);
            }
        }
        if (holder.page == Page.REWARDS) {
            if (slot == settings.gui().slot("rewards-homes", 20)) result = entitlements.purchase(player, state, "homes");
            if (slot == settings.gui().slot("rewards-listings", 22)) result = entitlements.purchase(player, state, "auction-listings");
            if (slot == settings.gui().slot("rewards-claims", 24)) result = entitlements.purchase(player, state, "claim-blocks");
        }
        if (holder.page == Page.HATTER && slot == settings.gui().slot("hatter-claim", 31)) result = hatter.claim(player);
        if (holder.page == Page.SETTINGS) {
            if (slot == settings.gui().slot("settings-sounds", 20)) preferences.toggleSounds(player.getUniqueId());
            else if (slot == settings.gui().slot("settings-numbers", 22)) preferences.toggleCompactNumbers(player.getUniqueId());
            else if (slot == settings.gui().slot("settings-integrations", 24)) preferences.toggleIntegrationStatus(player.getUniqueId());
            else if (slot == settings.gui().slot("settings-default-page", 31)) preferences.cycleDefaultPage(player.getUniqueId());
            else return;
            play(player, Sound.UI_BUTTON_CLICK);
            open(player, Page.SETTINGS);
            return;
        }
        if (result != null) {
            Text.send(player, settings, (result.success() ? "<green>" : "<red>") + result.message() + (result.success() ? "</green>" : "</red>"));
            play(player, result.success() ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.ENTITY_VILLAGER_NO);
            open(player, holder.page);
        }
    }

    private Page navigationAt(int slot) {
        if (slot == settings.gui().slot("nav-rank", 45)) return Page.RANK;
        if (slot == settings.gui().slot("nav-prestige", 46)) return Page.PRESTIGE;
        if (slot == settings.gui().slot("nav-rewards", 47)) return Page.REWARDS;
        if (slot == settings.gui().slot("nav-season", 48)) return Page.SEASON;
        if (slot == settings.gui().slot("nav-hatter", 49)) return Page.HATTER;
        if (settings.gui().playerSettingsEnabled() && slot == settings.gui().slot("nav-settings", 51)) return Page.SETTINGS;
        return null;
    }

    private void play(Player player, Sound sound) {
        if (preferences.get(player.getUniqueId()).menuSounds()) {
            player.playSound(player.getLocation(), sound, 0.7f, 1.1f);
        }
    }

    private String progress(String label, double actual, double required, boolean available, boolean compact) {
        if (required <= 0.0) return "<green>✔ " + label + ": not required</green>";
        if (!available) return "<red>✘ " + label + ": integration unavailable</red>";
        boolean complete = actual + 0.0001 >= required;
        return (complete ? "<green>✔ " : "<red>✘ ") + label + ": " + number(actual, compact) + " / " + number(required, compact)
                + (complete ? "</green>" : "</red>");
    }

    private String number(double value, boolean compact) {
        if (!compact || Math.abs(value) < 1_000.0) return Text.number(value);
        String suffix;
        double scaled;
        if (Math.abs(value) >= 1_000_000_000.0) { scaled = value / 1_000_000_000.0; suffix = "B"; }
        else if (Math.abs(value) >= 1_000_000.0) { scaled = value / 1_000_000.0; suffix = "M"; }
        else { scaled = value / 1_000.0; suffix = "K"; }
        return String.format(java.util.Locale.ROOT, "%.1f%s", scaled, suffix).replace(".0", "");
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        if (material == null || material.isAir() || !material.isItem()) material = Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    private String display(String value) {
        String normalized = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    public enum Page { RANK, PRESTIGE, REWARDS, SEASON, HATTER, SETTINGS }

    private static final class MenuHolder implements InventoryHolder {
        private final Page page;
        private final UUID viewer;
        private Inventory inventory;

        private MenuHolder(Page page, UUID viewer) {
            this.page = page;
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
