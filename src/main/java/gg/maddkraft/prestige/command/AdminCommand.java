package gg.maddkraft.prestige.command;

import gg.maddkraft.prestige.MaddPrestigePlugin;
import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.ContestMetric;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.SeasonStatus;
import gg.maddkraft.prestige.service.ActionResult;
import gg.maddkraft.prestige.service.ContestService;
import gg.maddkraft.prestige.service.EntitlementService;
import gg.maddkraft.prestige.service.HatterService;
import gg.maddkraft.prestige.service.LedgerService;
import gg.maddkraft.prestige.service.PatronService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.service.SeasonService;
import gg.maddkraft.prestige.storage.Database;
import gg.maddkraft.prestige.util.Text;
import gg.maddkraft.prestige.ui.StaffMenuService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public final class AdminCommand implements TabExecutor {
    private final MaddPrestigePlugin plugin;
    private final Database database;
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final ContestService contests;
    private final HatterService hatter;
    private final LedgerService ledger;
    private final PatronService patrons;
    private final EntitlementService entitlements;
    private final StaffMenuService staffMenus;
    private volatile PluginSettings settings;

    public AdminCommand(MaddPrestigePlugin plugin, Database database, ProfileService profiles, SeasonService seasons,
                        ContestService contests, HatterService hatter, LedgerService ledger,
                        PatronService patrons, EntitlementService entitlements, StaffMenuService staffMenus,
                        PluginSettings settings) {
        this.plugin = plugin;
        this.database = database;
        this.profiles = profiles;
        this.seasons = seasons;
        this.contests = contests;
        this.hatter = hatter;
        this.ledger = ledger;
        this.patrons = patrons;
        this.entitlements = entitlements;
        this.staffMenus = staffMenus;
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "reload" -> reload(sender);
                case "gui", "menu" -> gui(sender);
                case "inspect" -> inspect(sender, args);
                case "setprestige" -> setPrestige(sender, args);
                case "addtea" -> addTea(sender, args);
                case "progress" -> progress(sender, args);
                case "flush" -> flush(sender);
                case "season" -> season(sender, args);
                case "contest" -> contest(sender, args);
                case "hatter" -> hatter(sender, args);
                case "patron" -> patron(sender, args);
                case "recovery" -> recovery(sender, args);
                default -> help(sender);
            }
        } catch (IllegalArgumentException exception) {
            fail(sender, exception.getMessage());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Administrative operation failed: " + exception.getMessage());
            fail(sender, "Database operation failed; no further action was attempted.");
        }
        return true;
    }

    private void gui(CommandSender sender) {
        require(sender, "maddprestige.admin.gui");
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("The staff GUI can only be opened in game.");
        staffMenus.open(player);
    }

    private void reload(CommandSender sender) {
        require(sender, "maddprestige.admin.reload");
        ActionResult result = plugin.reloadPluginConfiguration();
        if (result.success()) settings = plugin.settings();
        send(sender, result);
        if (result.success()) plugin.audit(sender, "CONFIG_RELOAD", null, "Configuration reloaded");
    }

    private void inspect(CommandSender sender, String[] args) {
        require(sender, "maddprestige.admin.inspect");
        if (args.length < 2) throw new IllegalArgumentException("Usage: /mp inspect <player|uuid>");
        UUID target = requireKnownPlayer(args[1]);
        withProfile(sender, target, state -> {
            Text.send(sender, settings, "<gold>Profile " + playerName(target) + "</gold> <gray>(" + target + ")</gray>");
            Text.raw(sender, "<gray>Rank:</gray> <white>" + state.rank() + "</white> <gray>| Prestige:</gray> <white>"
                    + state.prestigeLevel() + "</white> <gray>| Lifetime:</gray> <white>" + state.lifetimePrestiges() + "</white>");
            Text.raw(sender, "<gray>Tea Leaves:</gray> <white>" + state.teaLeaves() + "</white> <gray>| Legacy Stars:</gray> <white>"
                    + state.legacyStars() + "</white>");
            Text.raw(sender, "<gray>Ledger:</gray> <white>$" + Text.number(state.ledger().serverEarnings()) + ", "
                    + Text.integer(state.ledger().mcMmoXp()) + " mcMMO, " + state.ledger().rabbitHoles() + " rabbit holes, "
                    + state.ledger().decreeObjectives() + " decrees, " + state.ledger().bosses() + " bosses</white>");
        });
    }

    private void setPrestige(CommandSender sender, String[] args) {
        require(sender, "maddprestige.admin.modify");
        if (args.length < 3) throw new IllegalArgumentException("Usage: /mp setprestige <player> <level>");
        UUID target = requireKnownPlayer(args[1]);
        int level = nonNegativeInt(args[2], "level");
        withProfile(sender, target, state -> {
            PlayerState before = state.snapshot();
            state.setPrestigeLevel(level);
            saveProfile(sender, state, before, "SET_PRESTIGE", "level=" + level);
        });
    }

    private void addTea(CommandSender sender, String[] args) {
        require(sender, "maddprestige.admin.modify");
        if (args.length < 3) throw new IllegalArgumentException("Usage: /mp addtea <player> <amount>");
        UUID target = requireKnownPlayer(args[1]);
        int amount = parseInt(args[2], "amount");
        withProfile(sender, target, state -> {
            PlayerState before = state.snapshot();
            state.addTeaLeaves(amount);
            saveProfile(sender, state, before, "ADD_TEA", "amount=" + amount);
        });
    }

    private void progress(CommandSender sender, String[] args) {
        require(sender, "maddprestige.admin.modify");
        if (args.length < 4) throw new IllegalArgumentException(
                "Usage: /mp progress <player> <earnings|mcmmo|rabbits|decrees|bosses> <amount>");
        UUID target = requireKnownPlayer(args[1]);
        double amount = positiveDouble(args[3], "amount");
        boolean success = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "earnings", "server-earnings" -> ledger.addServerEarnings(target, amount, "admin");
            case "mcmmo", "mcmmo-xp" -> ledger.addMcMmoXp(target, Math.round(amount));
            case "rabbits", "rabbit-holes" -> ledger.addRabbitHole(target, (int) Math.round(amount));
            case "decrees", "decree-objectives" -> ledger.addDecreeObjective(target, (int) Math.round(amount));
            case "bosses" -> ledger.addBoss(target, (int) Math.round(amount));
            default -> throw new IllegalArgumentException("Unknown progress type.");
        };
        if (!success) fail(sender, "Progress was rejected (profile unloaded, chapter frozen, or invalid amount).");
        else {
            ok(sender, "Progress added to " + playerName(target) + ".");
            plugin.audit(sender, "ADD_PROGRESS", target.toString(), args[2] + "=" + amount);
        }
    }

    private void flush(CommandSender sender) throws SQLException {
        require(sender, "maddprestige.admin.modify");
        profiles.flushNow();
        contests.flush();
        ok(sender, "All cached profiles and contest scores were flushed.");
        plugin.audit(sender, "FLUSH", null, "Manual database flush");
    }

    private void season(CommandSender sender, String[] args) throws SQLException {
        require(sender, "maddprestige.admin.season");
        if (args.length < 2) throw new IllegalArgumentException("Usage: /mp season <status|new> ...");
        if (args[1].equalsIgnoreCase("status")) {
            if (args.length < 3) {
                ok(sender, "Current chapter status: " + seasons.current().status());
                return;
            }
            SeasonStatus status = SeasonStatus.valueOf(args[2].toUpperCase(Locale.ROOT));
            if (status == SeasonStatus.CLOSED) throw new IllegalArgumentException("Use /mp season new to close and replace the chapter atomically.");
            seasons.setStatus(status);
            ok(sender, "Chapter status set to " + status + ".");
            plugin.audit(sender, "SEASON_STATUS", seasons.current().id(), status.name());
            return;
        }
        if (args[1].equalsIgnoreCase("new")) {
            if (args.length < 4) throw new IllegalArgumentException("Usage: /mp season new <id> <display name>");
            if (contests.activeContest().isPresent()) throw new IllegalArgumentException("Finish the active Hatter's Contest before changing chapters.");
            String id = args[2].toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9][a-z0-9_-]{1,31}")) throw new IllegalArgumentException("Chapter id must use 2-32 lowercase letters, numbers, _ or -.");
            String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            var created = seasons.beginNewChapter(id, name);
            contests.reloadActiveContest();
            ok(sender, "Started " + created.displayName() + " (" + created.id() + "). World resets remain a host-maintenance step.");
            plugin.audit(sender, "SEASON_NEW", created.id(), created.displayName());
            return;
        }
        throw new IllegalArgumentException("Usage: /mp season <status|new> ...");
    }

    private void contest(CommandSender sender, String[] args) throws SQLException {
        require(sender, "maddprestige.admin.hatter");
        if (args.length < 2) throw new IllegalArgumentException("Usage: /mp contest <start|end> ...");
        if (args[1].equalsIgnoreCase("start")) {
            ContestMetric metric = args.length >= 3 ? ContestMetric.parse(args[2]) : settings.hatter().defaultMetric();
            int days = args.length >= 4 ? nonNegativeInt(args[3], "days") : settings.hatter().defaultContestDurationDays();
            int minimum = args.length >= 5 ? nonNegativeInt(args[4], "minimum prestige") : settings.hatter().minimumPrestige();
            var started = contests.start(metric, Math.max(1, days), minimum);
            ok(sender, "Started Hatter's Contest " + started.id() + " using " + metric + ".");
            plugin.audit(sender, "CONTEST_START", started.id(), metric + ", days=" + days + ", minimum=" + minimum);
            return;
        }
        if (args[1].equalsIgnoreCase("end")) {
            UUID selected = args.length >= 3 ? requireKnownPlayer(args[2]) : null;
            ContestService.FinalizeResult result = contests.finalizeContest(selected);
            if (!result.success()) {
                fail(sender, result.message() + (result.tie() ? " Tied: " + result.tiedPlayers() : ""));
                return;
            }
            ActionResult transfer = hatter.transfer(result.winnerId(), result.contestId(), "Hatter's Contest completed");
            send(sender, transfer);
            plugin.audit(sender, "CONTEST_END", result.contestId(), "winner=" + result.winnerId());
            return;
        }
        throw new IllegalArgumentException("Usage: /mp contest <start|end> ...");
    }

    private void hatter(CommandSender sender, String[] args) {
        require(sender, "maddprestige.admin.hatter");
        if (args.length < 2) throw new IllegalArgumentException("Usage: /mp hatter <set|revoke> ...");
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 3) throw new IllegalArgumentException("Usage: /mp hatter set <player>");
            UUID target = requireKnownPlayer(args[2]);
            ActionResult result = hatter.transfer(target, null, "administrative appointment");
            send(sender, result);
            if (result.success()) plugin.audit(sender, "HATTER_SET", target.toString(), "Administrative appointment");
            return;
        }
        if (args[1].equalsIgnoreCase("revoke")) {
            ActionResult result = hatter.revoke("administrative revocation");
            send(sender, result);
            if (result.success()) plugin.audit(sender, "HATTER_REVOKE", null, "Administrative revocation");
            return;
        }
        throw new IllegalArgumentException("Usage: /mp hatter <set|revoke> ...");
    }

    private void patron(CommandSender sender, String[] args) {
        require(sender, "maddprestige.admin.patron");
        if (args.length < 4 || !args[1].equalsIgnoreCase("set")) {
            throw new IllegalArgumentException("Usage: /mp patron set <player> <tier|none>");
        }
        UUID target = resolveKnownPlayer(args[2]);
        if (target == null) {
            ActionResult queued = patrons.queueForFirstJoin(args[2], args[3]);
            send(sender, queued);
            if (queued.success()) plugin.audit(sender, "PATRON_QUEUE", args[2], "tier=" + args[3]);
            return;
        }
        patrons.setTier(target, args[3]).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) fail(sender, "LuckPerms patron update failed: " + error.getMessage());
            else {
                send(sender, result);
                if (result.success()) {
                    Player online = Bukkit.getPlayer(target);
                    if (online != null) profiles.get(target).ifPresent(state -> entitlements.apply(online, state));
                    plugin.audit(sender, "PATRON_SET", target.toString(), "tier=" + args[3]);
                }
            }
        }));
    }

    private void recovery(CommandSender sender, String[] args) throws SQLException {
        require(sender, "maddprestige.admin.modify");
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            var pending = database.pendingPrestigeTransactions();
            if (pending.isEmpty()) ok(sender, "No interrupted prestige transactions were found.");
            else {
                fail(sender, pending.size() + " interrupted prestige transaction(s) require manual review:");
                for (var entry : pending) Text.raw(sender, "<yellow>" + entry.id() + "</yellow> <gray>player="
                        + playerName(entry.playerId()) + ", target=" + entry.targetPrestige() + ", fee=" + entry.fee()
                        + ", created=" + entry.createdAt() + "</gray>");
            }
            return;
        }
        if (args[1].equalsIgnoreCase("mark") && args.length >= 4) {
            String transaction = args[2];
            String state = args[3].toLowerCase(Locale.ROOT);
            if (state.equals("completed")) database.finishPrestigeTransaction(transaction);
            else if (state.equals("failed")) database.failPrestigeTransaction(transaction,
                    args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "manually reviewed");
            else throw new IllegalArgumentException("Recovery state must be completed or failed.");
            ok(sender, "Transaction marked " + state + ". This command does not move money; verify/refund separately if needed.");
            plugin.audit(sender, "RECOVERY_MARK", transaction, state);
            return;
        }
        throw new IllegalArgumentException("Usage: /mp recovery <list|mark <id> <completed|failed> [reason]>");
    }

    private void saveProfile(CommandSender sender, PlayerState state, PlayerState before, String action, String details) {
        try {
            profiles.saveNow(state);
            ok(sender, "Updated " + playerName(state.playerId()) + ".");
            plugin.audit(sender, action, state.playerId().toString(), details);
        } catch (SQLException exception) {
            state.restoreFrom(before);
            fail(sender, "Saving failed; inspect the profile before retrying.");
        }
    }

    private void withProfile(CommandSender sender, UUID target, Consumer<PlayerState> action) {
        PlayerState cached = profiles.get(target).orElse(null);
        if (cached != null) {
            action.accept(cached);
            if (Bukkit.getPlayer(target) == null) profiles.unload(target);
            return;
        }
        Text.send(sender, settings, "<yellow>Loading that profile...</yellow>");
        profiles.load(target, seasons.current().id()).whenComplete((state, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || state == null) fail(sender, "Could not load that profile.");
            else {
                action.accept(state);
                if (Bukkit.getPlayer(target) == null) profiles.unload(target);
            }
        }));
    }

    private UUID requireKnownPlayer(String value) {
        UUID result = resolveKnownPlayer(value);
        if (result == null) throw new IllegalArgumentException("Unknown player. They must have joined once, or supply their UUID.");
        return result;
    }

    private UUID resolveKnownPlayer(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) {}
        Player online = Bukkit.getPlayerExact(value);
        if (online != null) return online.getUniqueId();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(value)) return offline.getUniqueId();
        }
        return null;
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    private void help(CommandSender sender) {
        Text.send(sender, settings, "<gold><bold>MaddPrestige Admin</bold></gold>");
        Text.raw(sender, "<gray>/mp gui — open the staff dashboard</gray>");
        Text.raw(sender, "<gray>/mp inspect <player> | setprestige | addtea | progress | flush</gray>");
        Text.raw(sender, "<gray>/mp season <status|new> | contest <start|end> | hatter <set|revoke></gray>");
        Text.raw(sender, "<gray>/mp patron set <player> <tier|none> | recovery <list|mark> | reload</gray>");
    }

    private void require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) throw new IllegalArgumentException("You do not have permission for that operation.");
    }

    private int nonNegativeInt(String value, String label) {
        int parsed = parseInt(value, label);
        if (parsed < 0) throw new IllegalArgumentException(label + " must be non-negative.");
        return parsed;
    }

    private int parseInt(String value, String label) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " must be a whole number."); }
    }

    private double positiveDouble(String value, String label) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0.0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a positive number.");
        }
    }

    private void send(CommandSender sender, ActionResult result) {
        Text.send(sender, settings, (result.success() ? "<green>" : "<red>") + result.message()
                + (result.success() ? "</green>" : "</red>"));
    }

    private void ok(CommandSender sender, String message) { Text.send(sender, settings, "<green>" + message + "</green>"); }
    private void fail(CommandSender sender, String message) { Text.send(sender, settings, "<red>" + message + "</red>"); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("help", "gui", "reload", "inspect", "setprestige", "addtea", "progress",
                "flush", "season", "contest", "hatter", "patron", "recovery"));
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "season" -> match(args[1], List.of("status", "new"));
                case "contest" -> match(args[1], List.of("start", "end"));
                case "hatter" -> match(args[1], List.of("set", "revoke"));
                case "patron" -> match(args[1], List.of("set"));
                case "recovery" -> match(args[1], List.of("list", "mark"));
                default -> playerMatches(args[1]);
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("season") && args[1].equalsIgnoreCase("status")) {
            return match(args[2], List.of("ACTIVE", "CLOSING", "FROZEN", "RESETTING"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("contest") && args[1].equalsIgnoreCase("start")) {
            return match(args[2], Arrays.stream(ContestMetric.values()).map(Enum::name).toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("patron") && args[1].equalsIgnoreCase("set")) {
            List<String> names = new java.util.ArrayList<>(patrons.tierNames());
            names.add("NONE");
            return match(args[3], names);
        }
        return List.of();
    }

    private List<String> playerMatches(String prefix) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
