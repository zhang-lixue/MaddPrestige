package gg.maddkraft.prestige.ui;

import gg.maddkraft.prestige.MaddPrestigePlugin;
import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.CompatibilityService;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.service.ActionResult;
import gg.maddkraft.prestige.service.ContestService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.util.Text;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Permission-gated operational GUI. Mutating profile actions are persisted and audited immediately. */
public final class StaffMenuService implements Listener {
    private final MaddPrestigePlugin plugin;
    private final ProfileService profiles;
    private final ContestService contests;
    private final CompatibilityService compatibility;
    private final IntegrationRelationshipService relationships;
    private volatile PluginSettings settings;

    public StaffMenuService(MaddPrestigePlugin plugin, ProfileService profiles, ContestService contests,
                            CompatibilityService compatibility, IntegrationRelationshipService relationships,
                            PluginSettings settings) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.contests = contests;
        this.compatibility = compatibility;
        this.relationships = relationships;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public void open(Player player) {
        if (!allowed(player)) {
            Text.send(player, settings, settings.message("no-permission", "<red>You do not have permission.</red>"));
            return;
        }
        open(player, View.DASHBOARD, 0, null);
    }

    private void open(Player player, View view, int page, UUID target) {
        StaffHolder holder = new StaffHolder(view, player.getUniqueId(), page, target);
        Inventory inventory = Bukkit.createInventory(holder, settings.gui().size(), Text.mm(title(view)));
        holder.inventory = inventory;
        fill(inventory);
        switch (view) {
            case DASHBOARD -> drawDashboard(inventory);
            case INTEGRATIONS -> drawIntegrations(inventory, holder);
            case PLAYERS -> drawPlayers(inventory, holder);
            case EDITOR -> drawEditor(inventory, target);
        }
        player.openInventory(inventory);
    }

    private String title(View view) {
        return switch (view) {
            case DASHBOARD -> settings.gui().title("staff", "<red><bold>MaddPrestige Staff</bold></red>");
            case INTEGRATIONS -> settings.gui().title("integrations", "<aqua><bold>Plugin Compatibility</bold></aqua>");
            case PLAYERS -> settings.gui().title("players", "<gold><bold>Online Player Profiles</bold></gold>");
            case EDITOR -> settings.gui().title("player-editor", "<red><bold>Staff Profile Editor</bold></red>");
        };
    }

    private void drawDashboard(Inventory inventory) {
        put(inventory, "staff-status", 13, item(Material.BEACON, "<gold><bold>Live server status</bold></gold>", List.of(
                "<gray>Loaded profiles:</gray> <white>" + profiles.cachedStates().size() + "</white>",
                "<gray>Supported plugins enabled:</gray> <white>" + compatibility.enabledCount() + "</white>",
                "<gray>Configured relationships:</gray> <white>" + relationships.configuredProviderCount() + "</white>",
                "<gray>Active contest:</gray> <white>" + (contests.activeContest().isPresent() ? "Yes" : "No") + "</white>")));
        put(inventory, "staff-players", 20, item(Material.PLAYER_HEAD, "<gold><bold>Player profiles</bold></gold>", List.of(
                "<gray>Inspect and adjust loaded online profiles.</gray>",
                "<yellow>Click to open.</yellow>")));
        put(inventory, "staff-integrations", 22, item(Material.COMPARATOR, "<aqua><bold>Compatibility dashboard</bold></aqua>", List.of(
                "<gray>Inspect all configured optional plugins and adapters.</gray>",
                "<yellow>Click to open.</yellow>")));
        put(inventory, "staff-reload", 24, item(Material.REPEATER, "<yellow><bold>Reload configuration</bold></yellow>", List.of(
                "<gray>Validates and applies configurable progression and GUI values.</gray>",
                "<yellow>Shift-click to reload.</yellow>")));
        put(inventory, "staff-flush", 31, item(Material.WRITABLE_BOOK, "<green><bold>Flush data now</bold></green>", List.of(
                "<gray>Persists cached profiles and contest scores.</gray>",
                "<yellow>Click to flush.</yellow>")));
        footer(inventory, View.DASHBOARD);
    }

    private void drawIntegrations(Inventory inventory, StaffHolder holder) {
        List<CompatibilityService.Entry> entries = compatibility.entries();
        int content = Math.max(1, inventory.getSize() - 9);
        int start = holder.pageIndex * content;
        holder.integrationEntries = entries.subList(Math.min(start, entries.size()), Math.min(start + content, entries.size()));
        for (int index = 0; index < holder.integrationEntries.size(); index++) {
            CompatibilityService.Entry entry = holder.integrationEntries.get(index);
            Material material = !entry.configured() ? Material.BARRIER
                    : entry.enabled() ? Material.LIME_CONCRETE
                    : entry.installed() ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            String state = !entry.configured() ? "Disabled in config"
                    : entry.enabled() ? "Enabled " + entry.version()
                    : entry.installed() ? "Installed but disabled" : "Not installed (optional)";
            IntegrationRelationshipService.RelationshipSummary relationship = relationships.summaryFor(entry.name());
            List<String> lore = new ArrayList<>(List.of(
                    "<gray>Mode:</gray> <white>" + display(entry.mode().name()) + "</white>",
                    "<gray>Status:</gray> <white>" + state + "</white>",
                    "<gray>Relationship:</gray> <white>" + (relationship.configured()
                            ? relationship.featureCount() + " features, " + relationship.commandCount() + " actions, "
                            + relationship.permissionCount() + " permissions, " + relationship.eventHookCount() + " event hooks"
                            : "not configured") + "</white>",
                    "",
                    "<dark_gray>" + entry.description() + "</dark_gray>"));
            if (relationship.configured()) {
                lore.add(relationship.enabled() ? "<green>Relationship enabled</green>" : "<red>Relationship disabled</red>");
                lore.add("<yellow>Shift-click to toggle and reload.</yellow>");
                if (relationship.eventHookCount() > 0) lore.add("<dark_gray>Restart required for event-hook changes.</dark_gray>");
            }
            inventory.setItem(index, item(material, (entry.enabled() ? "<green>" : "<gray>")
                    + "<bold>" + entry.name() + "</bold>", lore));
        }
        pagination(inventory, holder.pageIndex, start + content < entries.size());
        footer(inventory, View.INTEGRATIONS);
    }

    private void drawPlayers(Inventory inventory, StaffHolder holder) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int content = Math.max(1, inventory.getSize() - 9);
        int start = holder.pageIndex * content;
        List<Player> visible = online.subList(Math.min(start, online.size()), Math.min(start + content, online.size()));
        holder.targets = visible.stream().map(Player::getUniqueId).toList();
        for (int index = 0; index < visible.size(); index++) {
            Player target = visible.get(index);
            PlayerState state = profiles.get(target.getUniqueId()).orElse(null);
            List<String> lore = new ArrayList<>();
            if (state == null) lore.add("<yellow>Profile loading.</yellow>");
            else {
                lore.add("<gray>Rank:</gray> <white>" + display(state.rank().name()) + "</white>");
                lore.add("<gray>Prestige:</gray> <gold>" + state.prestigeLevel() + "</gold>");
                lore.add("<gray>Tea Leaves:</gray> <green>" + state.teaLeaves() + "</green>");
                lore.add("");
                lore.add("<yellow>Click to edit.</yellow>");
            }
            inventory.setItem(index, item(Material.PLAYER_HEAD, "<gold><bold>" + target.getName() + "</bold></gold>", lore));
        }
        pagination(inventory, holder.pageIndex, start + content < online.size());
        footer(inventory, View.PLAYERS);
    }

    private void drawEditor(Inventory inventory, UUID targetId) {
        PlayerState state = targetId == null ? null : profiles.get(targetId).orElse(null);
        if (state == null) {
            inventory.setItem(13, item(Material.BARRIER, "<red>Profile is no longer loaded</red>", List.of(
                    "<gray>Return to the player list and try again.</gray>")));
            footer(inventory, View.EDITOR);
            return;
        }
        inventory.setItem(4, item(Material.PLAYER_HEAD, "<gold><bold>" + playerName(targetId) + "</bold></gold>", List.of(
                "<gray>Rank:</gray> <white>" + display(state.rank().name()) + "</white>",
                "<gray>Prestige:</gray> <gold>" + state.prestigeLevel() + "</gold>",
                "<gray>Tea Leaves:</gray> <green>" + state.teaLeaves() + "</green>",
                "<dark_gray>Shift-click multiplies configured increments by 10.</dark_gray>")));
        editorButton(inventory, 10, Material.RED_DYE, "Remove Tea Leaves", "-" + settings.gui().teaStep());
        editorButton(inventory, 12, Material.LIME_DYE, "Add Tea Leaves", "+" + settings.gui().teaStep());
        editorButton(inventory, 14, Material.REDSTONE, "Lower Prestige", "-" + settings.gui().prestigeStep());
        editorButton(inventory, 16, Material.NETHER_STAR, "Raise Prestige", "+" + settings.gui().prestigeStep());
        editorButton(inventory, 20, Material.GOLD_INGOT, "Server Earnings", "+" + Text.number(settings.gui().serverEarningsStep()));
        editorButton(inventory, 22, Material.EXPERIENCE_BOTTLE, "mcMMO XP", "+" + settings.gui().mcMmoXpStep());
        editorButton(inventory, 29, Material.RABBIT_FOOT, "Rabbit Holes", "+" + settings.gui().objectiveStep());
        editorButton(inventory, 31, Material.PAPER, "Decree Objectives", "+" + settings.gui().objectiveStep());
        editorButton(inventory, 33, Material.DRAGON_HEAD, "Bosses", "+" + settings.gui().objectiveStep());
        footer(inventory, View.EDITOR);
    }

    private void editorButton(Inventory inventory, int slot, Material material, String name, String amount) {
        if (slot < inventory.getSize()) inventory.setItem(slot, item(material, "<yellow><bold>" + name + "</bold></yellow>", List.of(
                "<gray>Adjustment:</gray> <white>" + amount + "</white>", "<yellow>Click to apply.</yellow>")));
    }

    private void pagination(Inventory inventory, int page, boolean hasNext) {
        int previous = inventory.getSize() - 9;
        int next = inventory.getSize() - 1;
        if (page > 0) inventory.setItem(previous, item(Material.ARROW, "<yellow>Previous page</yellow>", List.of()));
        if (hasNext) inventory.setItem(next, item(Material.ARROW, "<yellow>Next page</yellow>", List.of()));
    }

    private void footer(Inventory inventory, View view) {
        int back = inventory.getSize() - 5;
        inventory.setItem(back, item(settings.gui().material("back", Material.ARROW),
                view == View.DASHBOARD ? "<green>Return to player menu</green>" : "<yellow>Back to staff dashboard</yellow>", List.of()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof StaffHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.viewer) || !allowed(player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        int previous = event.getView().getTopInventory().getSize() - 9;
        int back = event.getView().getTopInventory().getSize() - 5;
        int next = event.getView().getTopInventory().getSize() - 1;
        if (slot == back) {
            if (holder.view == View.DASHBOARD) player.performCommand("prestige menu");
            else open(player, View.DASHBOARD, 0, null);
            return;
        }
        if (slot == previous && holder.pageIndex > 0) {
            open(player, holder.view, holder.pageIndex - 1, holder.target);
            return;
        }
        if (slot == next && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            open(player, holder.view, holder.pageIndex + 1, holder.target);
            return;
        }
        switch (holder.view) {
            case DASHBOARD -> dashboardClick(player, slot, event.isShiftClick());
            case PLAYERS -> {
                if (slot < holder.targets.size()) open(player, View.EDITOR, 0, holder.targets.get(slot));
            }
            case EDITOR -> editorClick(player, holder.target, slot, event.isShiftClick());
            case INTEGRATIONS -> integrationClick(player, holder, slot, event.isShiftClick());
        }
    }

    private void integrationClick(Player player, StaffHolder holder, int slot, boolean shift) {
        if (!shift || slot < 0 || slot >= holder.integrationEntries.size()) return;
        CompatibilityService.Entry entry = holder.integrationEntries.get(slot);
        IntegrationRelationshipService.RelationshipSummary relationship = relationships.summaryFor(entry.name());
        if (!relationship.configured() || relationship.id().isBlank()) return;
        boolean next = !relationship.enabled();
        plugin.getConfig().set("relationships.providers." + relationship.id() + ".enabled", next);
        plugin.saveConfig();
        ActionResult result = plugin.reloadPluginConfiguration();
        if (result.success()) {
            plugin.audit(player, "RELATIONSHIP_TOGGLE", relationship.id(), "enabled=" + next);
            send(player, ActionResult.ok("Relationship " + relationship.id() + " is now "
                    + (next ? "enabled" : "disabled") + ". Restart if its event hooks changed."));
        } else {
            send(player, result);
        }
        open(player, View.INTEGRATIONS, holder.pageIndex, null);
    }

    private void dashboardClick(Player player, int slot, boolean shift) {
        if (slot == settings.gui().slot("staff-players", 20)) open(player, View.PLAYERS, 0, null);
        else if (slot == settings.gui().slot("staff-integrations", 22)) open(player, View.INTEGRATIONS, 0, null);
        else if (slot == settings.gui().slot("staff-reload", 24)) {
            if (!shift) {
                Text.send(player, settings, "<yellow>Shift-click Reload Configuration to confirm.</yellow>");
                return;
            }
            ActionResult result = plugin.reloadPluginConfiguration();
            send(player, result);
            open(player, View.DASHBOARD, 0, null);
        } else if (slot == settings.gui().slot("staff-flush", 31)) {
            try {
                profiles.flushNow();
                contests.flush();
                plugin.audit(player, "GUI_FLUSH", null, "Manual staff GUI flush");
                send(player, ActionResult.ok("All cached profiles and contest scores were saved."));
            } catch (SQLException exception) {
                send(player, ActionResult.fail("The database flush failed; check the server log."));
            }
        }
    }

    private void editorClick(Player actor, UUID targetId, int slot, boolean shift) {
        PlayerState state = targetId == null ? null : profiles.get(targetId).orElse(null);
        if (state == null) {
            send(actor, ActionResult.fail("That player's profile is no longer loaded."));
            return;
        }
        int multiplier = shift ? 10 : 1;
        PlayerState before = state.snapshot();
        String action;
        String detail;
        switch (slot) {
            case 10 -> { int amount = settings.gui().teaStep() * multiplier; state.addTeaLeaves(-amount); action = "GUI_REMOVE_TEA"; detail = "amount=" + amount; }
            case 12 -> { int amount = settings.gui().teaStep() * multiplier; state.addTeaLeaves(amount); action = "GUI_ADD_TEA"; detail = "amount=" + amount; }
            case 14 -> { int amount = settings.gui().prestigeStep() * multiplier; state.setPrestigeLevel(state.prestigeLevel() - amount); action = "GUI_LOWER_PRESTIGE"; detail = "amount=" + amount; }
            case 16 -> { int amount = settings.gui().prestigeStep() * multiplier; state.setPrestigeLevel(state.prestigeLevel() + amount); action = "GUI_RAISE_PRESTIGE"; detail = "amount=" + amount; }
            case 20 -> { double amount = settings.gui().serverEarningsStep() * multiplier; state.ledger().addServerEarnings(amount); state.markDirty(); action = "GUI_ADD_EARNINGS"; detail = "amount=" + amount; }
            case 22 -> { long amount = settings.gui().mcMmoXpStep() * multiplier; state.ledger().addMcMmoXp(amount); state.markDirty(); action = "GUI_ADD_MCMMO"; detail = "amount=" + amount; }
            case 29 -> { int amount = settings.gui().objectiveStep() * multiplier; state.ledger().addRabbitHoles(amount); state.markDirty(); action = "GUI_ADD_RABBITS"; detail = "amount=" + amount; }
            case 31 -> { int amount = settings.gui().objectiveStep() * multiplier; state.ledger().addDecreeObjectives(amount); state.markDirty(); action = "GUI_ADD_DECREES"; detail = "amount=" + amount; }
            case 33 -> { int amount = settings.gui().objectiveStep() * multiplier; state.ledger().addBosses(amount); state.markDirty(); action = "GUI_ADD_BOSSES"; detail = "amount=" + amount; }
            default -> { return; }
        }
        try {
            profiles.saveNow(state);
            plugin.audit(actor, action, targetId.toString(), detail);
            actor.playSound(actor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.1f);
            open(actor, View.EDITOR, 0, targetId);
        } catch (SQLException exception) {
            state.restoreFrom(before);
            send(actor, ActionResult.fail("Saving failed; the in-memory edit was rolled back."));
        }
    }

    private boolean allowed(Player player) {
        return settings.gui().staffMenuEnabled() && player.hasPermission(settings.gui().staffPermission());
    }

    private void send(Player player, ActionResult result) {
        Text.send(player, settings, (result.success() ? "<green>" : "<red>") + result.message()
                + (result.success() ? "</green>" : "</red>"));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(settings.gui().material("filler", Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private void put(Inventory inventory, String key, int fallback, ItemStack item) {
        int slot = settings.gui().slot(key, fallback);
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
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

    private String playerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString().substring(0, 8) : player.getName();
    }

    private String display(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private enum View { DASHBOARD, INTEGRATIONS, PLAYERS, EDITOR }

    private static final class StaffHolder implements InventoryHolder {
        private final View view;
        private final UUID viewer;
        private final int pageIndex;
        private final UUID target;
        private Inventory inventory;
        private List<UUID> targets = List.of();
        private List<CompatibilityService.Entry> integrationEntries = List.of();

        private StaffHolder(View view, UUID viewer, int pageIndex, UUID target) {
            this.view = view;
            this.viewer = viewer;
            this.pageIndex = pageIndex;
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
