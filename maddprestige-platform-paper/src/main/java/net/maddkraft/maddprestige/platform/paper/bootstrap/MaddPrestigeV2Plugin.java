package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.core.manual.ManualCounterDefinition;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.core.manual.ManualProgressBootstrap;
import net.maddkraft.maddprestige.core.manual.ManualProgressProvider;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompilation;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationConfiguration;
import net.maddkraft.maddprestige.integrations.luckperms.LuckPermsRewardProvider;
import net.maddkraft.maddprestige.persistence.admin.AtomicConfigurationFileStore;
import net.maddkraft.maddprestige.persistence.admin.SqliteConfigurationHistoryStore;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.recovery.PendingOperationRecoveryService;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteBackupService;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteDatabaseValidator;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteManualProgressRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePrestigeLifecycleRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteRecoveryEventRepository;
import net.maddkraft.maddprestige.platform.paper.BukkitPaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperWorldContextMetricProvider;
import net.maddkraft.maddprestige.platform.paper.VanillaStatisticsProvider;
import net.maddkraft.maddprestige.platform.paper.integration.PhaseSevenOptionalIntegrationManager;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import net.maddkraft.maddprestige.platform.paper.admin.PaperGuiInventoryGuard;
import net.maddkraft.maddprestige.platform.paper.admin.PaperPhaseSixCommandAdapter;
import net.maddkraft.maddprestige.platform.paper.admin.PaperConfirmationSessionListener;
import net.maddkraft.maddprestige.platform.paper.admin.PaperPhaseSixGuiController;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import net.maddkraft.maddprestige.platform.paper.provider.PaperProviderBridge;
import net.maddkraft.maddprestige.platform.paper.event.ProviderHealthChangedEvent;
import net.luckperms.api.LuckPerms;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** Phase 7 production composition root. The frozen V1 entrypoint is no longer selected by the descriptor. */
public final class MaddPrestigeV2Plugin extends JavaPlugin {
    private static final Map<String, String> DEFAULT_RESOURCES = Map.of(
            "progression.yml", "defaults/progression.yml",
            "requirements.yml", "defaults/requirements.yml",
            "rewards.yml", "defaults/rewards.yml",
            "lifecycle.yml", "defaults/lifecycle.yml",
            "integrations.yml", "integrations.yml");

    private final Clock clock = Clock.systemUTC();
    private final List<ProviderRegistration> ownedRegistrations = new ArrayList<>();
    private ProviderRegistry registry;
    private BukkitPaperTaskScheduler scheduler;
    private PhaseSevenOptionalIntegrationManager optionalIntegrations;
    private ManualProgressBootstrap manualProgress;
    private ExecutorService manualWriter;
    private BukkitTask manualFlushTask;
    private FailureLogThrottle manualFailureLogs;
    private ProviderRegistration manualProgressRegistration;
    private PaperProviderBridge providerBridge;
    private PaperMessageService messages;
    private ProductionRuntime runtime;
    private AutoCloseable healthEvents;
    private boolean ready;

    @Override
    public void onEnable() {
        try {
            Path dataDirectory = getDataFolder().toPath().toAbsolutePath().normalize();
            Files.createDirectories(dataDirectory);
            prepareAndReadDocuments(dataDirectory);
            messages = PaperMessageService.open(dataDirectory, getClassLoader(),
                    detail -> getLogger().warning(detail));

            Path database = dataDirectory.resolve("maddprestige-v2.sqlite");
            if (Files.notExists(database)) {
                Files.createFile(database);
            }
            SqliteFoundation foundation = new SqliteFoundation(database);
            var migrations = SqliteMigrations.phaseNineB();
            new MigrationRunner(foundation,
                    new SqliteBackupService(foundation, dataDirectory.resolve("backups"), migrations, clock), clock)
                    .migrate(migrations);
            SqliteDatabaseValidator.validate(database, migrations);
            AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                    dataDirectory.resolve("configuration"), clock);
            Optional<net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision> startup =
                    StartupConfigurationLoader.load(snapshots, new SqliteConfigurationHistoryStore(foundation));
            StartupPersistenceCompatibility.assess(foundation, startup);

            registry = new ProviderRegistry();
            scheduler = new BukkitPaperTaskScheduler(this);
            healthEvents = registry.addHealthListener((providerId, previous, current) -> scheduler.submit(
                    ExecutionThread.PAPER_SERVER_THREAD, () -> {
                        getServer().getPluginManager().callEvent(new ProviderHealthChangedEvent(
                                new ProviderHealthChangedSnapshot(providerId, previous.state(), current.state(),
                                        current.code(), clock.instant())));
                        return null;
                    }));
            registerBuiltInProviders();
            registerLuckPermsIfPresent();
            PhaseFiveIntegrationConfiguration initialIntegrations = startup.map(value ->
                    integrationConfiguration(value.compiled().documents()).configuration())
                    .orElseGet(PhaseFiveIntegrationConfiguration::disabled);
            boolean firstBootSetupDiscovery = startup.isEmpty();
            org.bukkit.plugin.Plugin mcMmo = getServer().getPluginManager().getPlugin("mcMMO");
            ManualMetricHandle mcMmoAdjustedXp = registerManualProgress(foundation,
                    initialManualProgressActive(initialIntegrations.mcMmoEnabled(), firstBootSetupDiscovery,
                            mcMmo != null && mcMmo.isEnabled()));

            IntegrationTaskScheduler integrationScheduler = new IntegrationTaskScheduler() {
                @Override
                public <T> CompletionStage<T> call(Supplier<T> action) {
                    return scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, action);
                }
            };
            optionalIntegrations = new PhaseSevenOptionalIntegrationManager(
                    this, registry, integrationScheduler, mcMmoAdjustedXp,
                    new MaddPrestigePlaceholderCache(10_000), clock);
            optionalIntegrations.start(initialIntegrations, firstBootSetupDiscovery);
            providerBridge = new PaperProviderBridge(this, registry, clock);
            providerBridge.start().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            int recovered = new PendingOperationRecoveryService(new SqliteOperationRepository(foundation),
                    new SqlitePrestigeLifecycleRepository(foundation),
                    new SqliteRecoveryEventRepository(foundation), clock, registry).recover(256).size();
            runtime = new ProductionRuntime(this, registry, foundation, startup, dataDirectory, clock, scheduler,
                    optionalIntegrations.placeholderOutput(), this::reconcileRuntime);
            bindCommand();
            ready = true;
            getServer().getServicesManager().register(MaddPrestigeService.class, runtime.service(), this,
                    ServicePriority.Normal);
            getLogger().info("MaddPrestige V2 " + getPluginMeta().getVersion()
                    + " release candidate ready; configuration is "
                    + (runtime.operational() ? "active at " + runtime.revision().orElseThrow().value()
                            : runtime.authoritativeRevision().map(value ->
                                    "fail-closed pending compatible composition at " + value.value())
                                    .orElse("safely dormant"))
                    + "; startup recovery inspected " + recovered + " operation(s).");
        } catch (RuntimeException | IOException exception) {
            getLogger().log(Level.SEVERE, "MaddPrestige V2 startup failed closed", exception);
            shutdownOwnedState();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        shutdownOwnedState();
    }

    private void registerBuiltInProviders() {
        ProviderHealth health = new ProviderHealth(ProviderHealthState.AVAILABLE,
                "paper.available", "Paper server-thread boundary is available", clock.instant());
        PaperWorldContextMetricProvider world = new PaperWorldContextMetricProvider(
                scheduler, getServer()::getPlayer, () -> health, clock);
        VanillaStatisticsProvider statistics = new VanillaStatisticsProvider(
                new net.maddkraft.maddprestige.api.id.ProviderId("paper_statistics"), "maddprestige",
                scheduler, getServer()::getOfflinePlayer, () -> health);
        registerActive(world);
        registerActive(statistics);
    }

    private void registerLuckPermsIfPresent() {
        org.bukkit.plugin.Plugin dependency = getServer().getPluginManager().getPlugin("LuckPerms");
        if (dependency == null || !dependency.isEnabled()) {
            getLogger().info("LuckPerms plugin is absent; optional LuckPerms rewards remain unavailable.");
            return;
        }
        var service = getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (service == null || !service.getPlugin().isEnabled()) {
            getLogger().info("LuckPerms service is absent; optional LuckPerms rewards remain unavailable.");
            return;
        }
        LuckPerms api = service.getProvider();
        LuckPermsRewardProvider adapter = new LuckPermsRewardProvider(api,
                service.getPlugin().getPluginMeta().getVersion(),
                () -> {
                    var current = getServer().getServicesManager().getRegistration(LuckPerms.class);
                    return current != null && current.getProvider() == api && current.getPlugin().isEnabled();
                }, clock);
        registerActive(adapter);
    }

    private ManualMetricHandle registerManualProgress(SqliteFoundation foundation, boolean mcMmoEnabled) {
        manualWriter = Executors.newSingleThreadExecutor(runnable -> Thread.ofPlatform()
                .name("maddprestige-manual-writer").daemon(true).unstarted(runnable));
        manualProgress = ManualProgressProvider.bootstrap(new ProviderId("phase5_events"), "maddprestige",
                new SqliteManualProgressRepository(foundation), manualWriter, clock, 16, 250_000);
        ManualMetricHandle adjustedXp = manualProgress.owner().registerMetric(new ManualCounterDefinition(
                new MetricId("mcmmo_adjusted_xp_total"), MetricValueType.EXACT_DECIMAL, true, false,
                "mcMMO adjusted XP total", "Official post-adjustment mcMMO XP events", Map.of(
                        "source", "mcmmo", "ownership", "maddprestige"))).toCompletableFuture().join();
        manualProgressRegistration = registerOwned(manualProgress.provider(), mcMmoEnabled);
        manualFailureLogs = new FailureLogThrottle(clock, java.time.Duration.ofMinutes(5));
        manualFlushTask = getServer().getScheduler().runTaskTimer(this, () ->
                manualProgress.provider().flushAsync().whenComplete((ignored, failure) -> {
                    registry.refreshHealth(manualProgressRegistration.providerId());
                    if (failure != null && manualFailureLogs.acquire()) {
                        getLogger().log(Level.SEVERE, "Manual progress persistence is stalled; values remain dirty",
                                failure);
                    } else if (failure == null) {
                        manualFailureLogs.recovered();
                    }
                }), 200L, 200L);
        return adjustedXp;
    }

    private void registerActive(net.maddkraft.maddprestige.api.provider.Provider provider) {
        registerOwned(provider, true);
    }

    static boolean initialManualProgressActive(
            boolean configured,
            boolean firstBootSetupDiscovery,
            boolean mcMmoAvailable) {
        return configured || firstBootSetupDiscovery && mcMmoAvailable;
    }

    private ProviderRegistration registerOwned(
            net.maddkraft.maddprestige.api.provider.Provider provider,
            boolean active) {
        ProviderRegistration registration = registry.register("maddprestige", provider);
        if (active) {
            registry.activate(registration);
        }
        ownedRegistrations.add(registration);
        return registration;
    }

    private synchronized void reconcileRuntime(PhaseFiveIntegrationConfiguration replacement) {
        optionalIntegrations.reconcile(replacement);
        if (replacement.mcMmoEnabled()) {
            registry.activate(manualProgressRegistration);
        } else {
            registry.deactivate(manualProgressRegistration);
        }
    }

    private void bindCommand() {
        PluginCommand command = java.util.Objects.requireNonNull(getCommand("maddprestige"),
                "maddprestige command is absent from plugin.yml");
        PaperPhaseSixGuiController guiController = new PaperPhaseSixGuiController(runtime.gui(),
                new PaperGuiInventoryGuard(), scheduler, messages);
        getServer().getPluginManager().registerEvents(guiController, this);
        PaperPhaseSixCommandAdapter adapter = new PaperPhaseSixCommandAdapter(runtime.commands(),
                runtime.completion(), scheduler, guiController, messages);
        command.setExecutor(adapter);
        command.setTabCompleter(adapter);
        getServer().getOnlinePlayers().forEach(player ->
                runtime.beginPlayerConfirmationSession(player.getUniqueId()));
        getServer().getPluginManager().registerEvents(new PaperConfirmationSessionListener(
                runtime::beginPlayerConfirmationSession, runtime::endPlayerConfirmationSession), this);
    }

    private void shutdownOwnedState() {
        ready = false;
        messages = null;
        if (runtime != null) {
            getServer().getServicesManager().unregister(runtime.service());
            runtime.close();
            runtime = null;
        }
        if (healthEvents != null) {
            try {
                healthEvents.close();
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Provider health event observer cleanup failed safely", exception);
            }
            healthEvents = null;
        }
        if (providerBridge != null) {
            providerBridge.close();
            providerBridge = null;
        }
        if (optionalIntegrations != null) {
            optionalIntegrations.stop();
            optionalIntegrations = null;
        }
        if (manualFlushTask != null) {
            manualFlushTask.cancel();
            manualFlushTask = null;
        }
        if (manualProgress != null) {
            try {
                manualProgress.provider().closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Manual progress shutdown flush failed", exception);
            } catch (java.util.concurrent.TimeoutException exception) {
                getLogger().log(Level.SEVERE, "Manual progress shutdown flush exceeded its deadline", exception);
            } catch (java.util.concurrent.ExecutionException exception) {
                getLogger().log(Level.SEVERE, "Manual progress shutdown flush failed", exception.getCause());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                getLogger().log(Level.SEVERE, "Manual progress shutdown flush was interrupted", exception);
            }
            manualProgress = null;
        }
        if (registry != null) {
            List.copyOf(ownedRegistrations).reversed().forEach(registration -> {
                try {
                    registry.unregister(registration);
                } catch (IllegalStateException ignored) {
                    // An exact registration can already be absent during partial-start rollback.
                }
            });
        }
        ownedRegistrations.clear();
        manualProgressRegistration = null;
        if (manualWriter != null) {
            manualWriter.shutdown();
            try {
                if (!manualWriter.awaitTermination(3, TimeUnit.SECONDS)) {
                    manualWriter.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                manualWriter.shutdownNow();
            }
            manualWriter = null;
        }
    }

    private static PhaseFiveIntegrationCompilation integrationConfiguration(Map<String, String> documents) {
        String source = documents.get("integrations.yml");
        if (source == null) {
            throw new IllegalArgumentException("Active configuration is missing integrations.yml");
        }
        PhaseFiveIntegrationCompilation compilation = new PhaseFiveIntegrationCompiler().compile(source);
        List<String> failures = compilation.validation().findings().stream()
                .filter(finding -> finding.severity()
                        == net.maddkraft.maddprestige.api.validation.ValidationSeverity.ERROR)
                .map(finding -> finding.path() + ": " + finding.explanation()).toList();
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException("Integration configuration is invalid: " + String.join("; ", failures));
        }
        return compilation;
    }

    private Map<String, String> prepareAndReadDocuments(Path directory) throws IOException {
        for (Map.Entry<String, String> entry : DEFAULT_RESOURCES.entrySet()) {
            Path target = directory.resolve(entry.getKey()).normalize();
            if (!target.startsWith(directory)) {
                throw new IOException("Configuration path escaped data directory");
            }
            if (Files.notExists(target)) {
                try (InputStream input = getClassLoader().getResourceAsStream(entry.getValue())) {
                    if (input == null) {
                        throw new IOException("Bundled default resource is absent: " + entry.getValue());
                    }
                    Files.write(target, input.readAllBytes(), StandardOpenOption.CREATE_NEW);
                }
            }
        }
        return readDocuments(directory);
    }

    private static Map<String, String> readDocuments(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String name : DEFAULT_RESOURCES.keySet().stream().sorted().toList()) {
            Path source = normalized.resolve(name).normalize();
            if (!source.startsWith(normalized) || !Files.isRegularFile(source)) {
                throw new IOException("Required configuration document is absent or unsafe: " + name);
            }
            result.put(name, Files.readString(source));
        }
        return Map.copyOf(result);
    }

}
