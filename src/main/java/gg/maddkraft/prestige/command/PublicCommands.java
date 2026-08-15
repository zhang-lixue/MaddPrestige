package gg.maddkraft.prestige.command;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.service.ActionResult;
import gg.maddkraft.prestige.service.ContestService;
import gg.maddkraft.prestige.service.EntitlementService;
import gg.maddkraft.prestige.service.HatterService;
import gg.maddkraft.prestige.service.PrestigeService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.service.RankService;
import gg.maddkraft.prestige.service.SeasonService;
import gg.maddkraft.prestige.storage.Database;
import gg.maddkraft.prestige.ui.MenuService;
import gg.maddkraft.prestige.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PublicCommands implements TabExecutor {
    private final Database database;
    private final ProfileService profiles;
    private final RankService ranks;
    private final PrestigeService prestiges;
    private final EntitlementService entitlements;
    private final SeasonService seasons;
    private final ContestService contests;
    private final HatterService hatter;
    private final MenuService menus;
    private volatile PluginSettings settings;

    public PublicCommands(Database database, ProfileService profiles, RankService ranks, PrestigeService prestiges,
                          EntitlementService entitlements, SeasonService seasons, ContestService contests,
                          HatterService hatter, MenuService menus, PluginSettings settings) {
        this.database = database;
        this.profiles = profiles;
        this.ranks = ranks;
        this.prestiges = prestiges;
        this.entitlements = entitlements;
        this.seasons = seasons;
        this.contests = contests;
        this.hatter = hatter;
        this.menus = menus;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "rankup" -> rankup(sender, args);
            case "prestige" -> prestige(sender, args);
            case "season" -> season(sender, args);
            case "maddhatter" -> maddHatter(sender, args);
            default -> false;
        };
    }

    private boolean rankup(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (args.length == 0) {
            menus.open(player, MenuService.Page.RANK);
            return true;
        }
        if (args[0].equalsIgnoreCase("confirm")) {
            sendResult(player, ranks.rankUp(player));
            return true;
        }
        Text.send(sender, settings, "<yellow>Usage: /rankup [confirm]</yellow>");
        return true;
    }

    private boolean prestige(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (args.length == 0) {
            menus.open(player, MenuService.Page.PRESTIGE);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu", "gui" -> menus.openPreferred(player);
            case "settings" -> menus.open(player, MenuService.Page.SETTINGS);
            case "confirm" -> {
                ActionResult result = prestiges.prestige(player);
                sendResult(player, result);
                if (result.success()) {
                    profiles.get(player.getUniqueId()).ifPresent(state ->
                            prestiges.milestoneMessages(state.prestigeLevel()).forEach(message -> Text.send(player, settings, message)));
                }
            }
            case "rewards" -> menus.open(player, MenuService.Page.REWARDS);
            case "buy" -> {
                if (args.length < 2) Text.send(player, settings, "<yellow>Usage: /prestige buy <homes|auction-listings|claim-blocks></yellow>");
                else {
                    PlayerState state = profile(player);
                    if (state != null) sendResult(player, entitlements.purchase(player, state, args[1]));
                }
            }
            case "top" -> showPrestigeTop(player);
            case "history" -> {
                PlayerState state = profile(player);
                if (state != null) Text.send(player, settings,
                        "<gold>Your record:</gold> <white>" + state.prestigeLevel() + " this chapter, "
                                + state.lifetimePrestiges() + " lifetime prestiges, and " + state.legacyStars() + " Legacy Stars.</white>");
            }
            default -> Text.send(player, settings, "<yellow>Usage: /prestige [menu|settings|confirm|rewards|buy|top|history]</yellow>");
        }
        return true;
    }

    private boolean season(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (args.length == 0) {
            menus.open(player, MenuService.Page.SEASON);
            return true;
        }
        var season = seasons.current();
        Text.send(player, settings, "<aqua>" + season.displayName() + "</aqua> <gray>— day "
                + seasons.dayNumber(Instant.now()) + ", " + season.status() + ", "
                + Text.duration(seasons.remaining(Instant.now())) + " remaining.</gray>");
        return true;
    }

    private boolean maddHatter(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (args.length == 0) {
            menus.open(player, MenuService.Page.HATTER);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> {
                List<Database.ScoreEntry> top = contests.leaderboard(10);
                if (top.isEmpty()) Text.send(player, settings, "<gray>The active contest has no scores yet.</gray>");
                else {
                    Text.send(player, settings, "<gold><bold>Hatter's Contest Top 10</bold></gold>");
                    for (int i = 0; i < top.size(); i++) {
                        Database.ScoreEntry entry = top.get(i);
                        Text.raw(player, "<gold>#" + (i + 1) + "</gold> <white>" + playerName(entry.playerId())
                                + "</white> <gray>— " + Text.number(entry.score()) + "</gray>");
                    }
                }
            }
            case "history" -> showHatterHistory(player);
            case "claim" -> sendResult(player, hatter.claim(player));
            default -> Text.send(player, settings, "<yellow>Usage: /maddhatter [top|history|claim]</yellow>");
        }
        return true;
    }

    private void showPrestigeTop(Player player) {
        try {
            List<Database.PrestigeEntry> top = database.prestigeLeaderboard(seasons.current().id(), 10);
            Text.send(player, settings, "<gold><bold>Chapter Prestige Top 10</bold></gold>");
            for (int index = 0; index < top.size(); index++) {
                Database.PrestigeEntry entry = top.get(index);
                Text.raw(player, "<gold>#" + (index + 1) + "</gold> <white>" + playerName(entry.playerId())
                        + "</white> <gray>— Prestige " + entry.prestigeLevel() + "</gray>");
            }
        } catch (SQLException exception) {
            Text.send(player, settings, "<red>Could not read the prestige leaderboard.</red>");
        }
    }

    private void showHatterHistory(Player player) {
        try {
            var history = hatter.history(10);
            if (history.isEmpty()) {
                Text.send(player, settings, "<gray>No previous MaddHatters have been archived.</gray>");
                return;
            }
            Text.send(player, settings, "<gold><bold>Former MaddHatters</bold></gold>");
            for (var entry : history) {
                Text.raw(player, "<white>" + playerName(entry.playerId()) + "</white> <gray>— "
                        + entry.startedAt().toString().substring(0, 10) + " to " + entry.endedAt().toString().substring(0, 10)
                        + " (" + entry.reason() + ")</gray>");
            }
        } catch (SQLException exception) {
            Text.send(player, settings, "<red>Could not read MaddHatter history.</red>");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        Text.send(sender, settings, settings.message("player-only", "<red>Players only.</red>"));
        return null;
    }

    private PlayerState profile(Player player) {
        PlayerState state = profiles.get(player.getUniqueId()).orElse(null);
        if (state == null) Text.send(player, settings, settings.message("profile-loading", "<yellow>Your profile is loading.</yellow>"));
        return state;
    }

    private void sendResult(CommandSender sender, ActionResult result) {
        Text.send(sender, settings, (result.success() ? "<green>" : "<red>") + result.message()
                + (result.success() ? "</green>" : "</red>"));
    }

    private String playerName(java.util.UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            if (command.getName().equalsIgnoreCase("prestige") && args.length == 2 && args[0].equalsIgnoreCase("buy")) {
                return match(args[1], List.of("homes", "auction-listings", "claim-blocks"));
            }
            return List.of();
        }
        List<String> options = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "rankup" -> List.of("confirm");
            case "prestige" -> List.of("menu", "settings", "confirm", "rewards", "buy", "top", "history");
            case "season" -> List.of("status");
            case "maddhatter" -> List.of("top", "history", "claim");
            default -> List.of();
        };
        return match(args[0], options);
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}
