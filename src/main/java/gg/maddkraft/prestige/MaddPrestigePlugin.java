package gg.maddkraft.prestige;

import gg.maddkraft.prestige.api.MaddPrestigeApi;
import gg.maddkraft.prestige.api.MaddPrestigeApiImpl;
import gg.maddkraft.prestige.command.AdminCommand;
import gg.maddkraft.prestige.command.PublicCommands;
import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.integration.EconomyBridge;
import gg.maddkraft.prestige.integration.CompatibilityService;
import gg.maddkraft.prestige.integration.GriefPreventionBridge;
import gg.maddkraft.prestige.integration.InstalledPluginHooks;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;
import gg.maddkraft.prestige.integration.LuckPermsBridge;
import gg.maddkraft.prestige.integration.PermissionBridge;
import gg.maddkraft.prestige.integration.VaultEconomyBridge;
import gg.maddkraft.prestige.listener.ExternalProgressListener;
import gg.maddkraft.prestige.listener.HatterItemListener;
import gg.maddkraft.prestige.listener.PlayerLifecycleListener;
import gg.maddkraft.prestige.placeholder.MaddPrestigeExpansion;
import gg.maddkraft.prestige.service.ActionResult;
import gg.maddkraft.prestige.service.ContestService;
import gg.maddkraft.prestige.service.EntitlementService;
import gg.maddkraft.prestige.service.HatterItemService;
import gg.maddkraft.prestige.service.HatterService;
import gg.maddkraft.prestige.service.LedgerService;
import gg.maddkraft.prestige.service.PatronService;
import gg.maddkraft.prestige.service.PlayerPreferenceService;
import gg.maddkraft.prestige.service.PrestigeService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.service.RankService;
import gg.maddkraft.prestige.service.SeasonService;
import gg.maddkraft.prestige.storage.Database;
import gg.maddkraft.prestige.ui.MenuService;
import gg.maddkraft.prestige.ui.StaffMenuService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.logging.Level;

public final class MaddPrestigePlugin extends JavaPlugin {
    private volatile PluginSettings settings;
    private Database database;
    private ProfileService profiles;
    private SeasonService seasons;
    private ContestService contests;
    private LedgerService ledger;
    private EconomyBridge economy;
    private PermissionBridge permissions;
    private EntitlementService entitlements;
    private RankService ranks;
    private PrestigeService prestiges;
    private HatterItemService hatterItems;
    private HatterService hatter;
    private PatronService patrons;
    private PlayerPreferenceService playerPreferences;
    private CompatibilityService compatibility;
    private IntegrationRelationshipService relationships;
    private MenuService menus;
    private StaffMenuService staffMenus;
    private PublicCommands publicCommands;
    private AdminCommand adminCommand;
    private BukkitTask flushTask;
    private BukkitTask inactivityTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        try {
            settings = PluginSettings.load(getConfig());
            Path dataFolder = getDataFolder().toPath().toAbsolutePath().normalize();
            Path databasePath = dataFolder.resolve(settings.databaseFile()).normalize();
            if (!databasePath.startsWith(dataFolder)) {
                throw new IllegalArgumentException("database.file must remain inside the MaddPrestige data folder");
            }

            database = new Database(databasePath, getLogger());
            database.initialize();
            profiles = new ProfileService(database, getLogger());
            playerPreferences = new PlayerPreferenceService(database, getLogger());
            compatibility = new CompatibilityService(this);
            economy = VaultEconomyBridge.create(this, settings.vaultEnabled() && compatibility.configured("Vault"));
            permissions = LuckPermsBridge.create(this, settings.luckPermsEnabled() && compatibility.configured("LuckPerms"), settings.progressionGroups());
            validateRequiredIntegrations(settings);
            if (permissions.available()) {
                var managedGroups = new HashSet<>(settings.progressionGroups().values());
                settings.patronTiers().values().forEach(tier -> managedGroups.add(tier.luckPermsGroup()));
                if (!permissions.ensureGroups(managedGroups).join()) {
                    throw new IllegalStateException("LuckPerms could not create or load the managed rank groups");
                }
            }
            relationships = new IntegrationRelationshipService(this, permissions, settings);
            seasons = new SeasonService(database, profiles, relationships, settings);
            contests = new ContestService(database, seasons, relationships, settings);
            ledger = new LedgerService(profiles, seasons, contests, relationships);

            GriefPreventionBridge griefPrevention = new GriefPreventionBridge(this, compatibility.configured("GriefPrevention"));
            entitlements = new EntitlementService(permissions, griefPrevention, profiles, relationships, settings);
            ranks = new RankService(profiles, seasons, economy, permissions, entitlements, relationships, settings);
            prestiges = new PrestigeService(database, profiles, seasons, economy, permissions, entitlements, ledger, relationships, settings);
            hatterItems = new HatterItemService(this, settings);
            hatter = new HatterService(database, seasons, permissions, hatterItems, relationships, settings);
            patrons = new PatronService(permissions, database, relationships, settings);
            menus = new MenuService(this, profiles, ranks, prestiges, entitlements, seasons, contests, hatter,
                    economy, playerPreferences, compatibility, settings);
            staffMenus = new StaffMenuService(this, profiles, contests, compatibility, relationships, settings);

            registerListeners();
            registerCommands();
            new InstalledPluginHooks(this, ledger, compatibility, relationships).registerAll();

            MaddPrestigeApi api = new MaddPrestigeApiImpl(ledger, profiles);
            getServer().getServicesManager().register(MaddPrestigeApi.class, api, this, ServicePriority.Normal);
            if (settings.placeholderEnabled() && compatibility.configured("PlaceholderAPI")
                    && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new MaddPrestigeExpansion(this, profiles, seasons, contests, hatter).register();
            }

            scheduleMaintenance();
            logCompatibilityReport();
            warnAboutInterruptedTransactions();
            getLogger().info("MaddPrestige enabled for " + seasons.current().displayName() + " on Paper 26.1.2.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "MaddPrestige could not enable safely; disabling without partial operation.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new PlayerLifecycleListener(this, profiles, seasons, permissions, entitlements,
                hatter, patrons, relationships), this);
        manager.registerEvents(new ExternalProgressListener(ledger), this);
        manager.registerEvents(new HatterItemListener(hatterItems), this);
        manager.registerEvents(menus, this);
        manager.registerEvents(staffMenus, this);
    }

    private void registerCommands() {
        publicCommands = new PublicCommands(database, profiles, ranks, prestiges, entitlements, seasons, contests, hatter, menus, settings);
        for (String name : List.of("rankup", "prestige", "season", "maddhatter")) {
            var command = Objects.requireNonNull(getCommand(name), "Missing command " + name + " in plugin.yml");
            command.setExecutor(publicCommands);
            command.setTabCompleter(publicCommands);
        }
        adminCommand = new AdminCommand(this, database, profiles, seasons, contests, hatter, ledger, patrons,
                entitlements, staffMenus, settings);
        var command = Objects.requireNonNull(getCommand("maddprestige"), "Missing maddprestige command in plugin.yml");
        command.setExecutor(adminCommand);
        command.setTabCompleter(adminCommand);
    }

    private void scheduleMaintenance() {
        long flushTicks = settings.flushIntervalSeconds() * 20L;
        flushTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            profiles.flushDirtyAsync();
            try {
                contests.flush();
            } catch (SQLException exception) {
                getLogger().log(Level.SEVERE, "Could not flush Hatter's Contest scores", exception);
            }
        }, flushTicks, flushTicks);
        inactivityTask = getServer().getScheduler().runTaskTimer(this, () ->
                hatter.revokeIfInactive(Instant.now()).ifPresent(result -> {
                    if (result.success()) getServer().broadcast(gg.maddkraft.prestige.util.Text.mm(
                            "<gold>The MaddHatter seat is vacant after a prolonged absence.</gold>"));
                    else getLogger().warning(result.message());
                }), 20L * 60L, 20L * 60L * 60L);
    }

    private void validateRequiredIntegrations(PluginSettings candidate) {
        if (candidate.luckPermsRequired() && !permissions.available()) {
            throw new IllegalStateException("LuckPerms is configured as required but its service is unavailable");
        }
        if (candidate.vaultRequiredForMoney() && !economy.available()) {
            throw new IllegalStateException("Vault economy is required for the configured money objectives but is unavailable");
        }
    }

    public synchronized ActionResult reloadPluginConfiguration() {
        try {
            reloadConfig();
            PluginSettings candidate = PluginSettings.load(getConfig());
            validateRequiredIntegrations(candidate);
            settings = candidate;
            seasons.reload(candidate);
            contests.reload(candidate);
            ranks.reload(candidate);
            prestiges.reload(candidate);
            entitlements.reload(candidate);
            hatterItems.reload(candidate);
            hatter.reload(candidate);
            patrons.reload(candidate);
            relationships.reload(candidate);
            menus.reload(candidate);
            staffMenus.reload(candidate);
            publicCommands.reload(candidate);
            adminCommand.reload(candidate);
            return ActionResult.ok("MaddPrestige configuration reloaded. Restart to change database paths or event-hook definitions.");
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Rejected invalid reloaded configuration", exception);
            return ActionResult.fail("Configuration rejected: " + exception.getMessage());
        }
    }

    private void warnAboutInterruptedTransactions() throws SQLException {
        var pending = database.pendingPrestigeTransactions();
        if (!pending.isEmpty()) {
            getLogger().severe("Found " + pending.size() + " interrupted prestige transaction(s). Use /mp recovery list before allowing manual refunds.");
        }
    }

    private void logCompatibilityReport() {
        for (CompatibilityService.Entry entry : compatibility.entries()) {
            getLogger().info("Compatibility: " + entry.name() + " [" + entry.mode() + "] = "
                    + (!entry.configured() ? "disabled by config"
                    : !entry.installed() ? "not installed (optional)"
                    : entry.version() + (entry.enabled() ? " (enabled)" : " (disabled)")));
        }
    }

    public void audit(CommandSender actor, String action, String target, String details) {
        String actorName = actor == null ? "SYSTEM" : actor.getName();
        getLogger().info("AUDIT actor=" + actorName + " action=" + action + " target=" + target + " details=" + details);
        try {
            database.recordAdminAction(actorName, action, target, details);
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "Could not persist administrative audit entry", exception);
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    @Override
    public void onDisable() {
        if (flushTask != null) flushTask.cancel();
        if (inactivityTask != null) inactivityTask.cancel();
        if (contests != null) {
            try { contests.flush(); } catch (SQLException exception) {
                getLogger().log(Level.SEVERE, "Could not flush contest scores during shutdown", exception);
            }
        }
        if (profiles != null) profiles.close();
        if (database != null) {
            try { database.close(); } catch (SQLException exception) {
                getLogger().log(Level.SEVERE, "Could not close MaddPrestige database", exception);
            }
        }
        getServer().getServicesManager().unregisterAll(this);
    }
}
