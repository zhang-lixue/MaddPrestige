package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.ConfigCompiler;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationCompiler;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfigurationCompiler;
import net.maddkraft.maddprestige.core.manual.ManualCounterDefinition;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.core.manual.ManualProgressBootstrap;
import net.maddkraft.maddprestige.core.manual.ManualProgressProvider;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationCompiler;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompilation;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.recovery.PendingOperationRecoveryService;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
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
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
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
    private RuntimeConfiguration configuration;
    private boolean ready;

    @Override
    public void onEnable() {
        try {
            Path dataDirectory = getDataFolder().toPath().toAbsolutePath().normalize();
            Files.createDirectories(dataDirectory);
            Map<String, String> documents = prepareAndReadDocuments(dataDirectory);

            Path database = dataDirectory.resolve("maddprestige-v2.sqlite");
            if (Files.notExists(database)) {
                Files.createFile(database);
            }
            SqliteFoundation foundation = new SqliteFoundation(database);
            new MigrationRunner(foundation,
                    new FileBackupService(database, dataDirectory.resolve("backups"), clock), clock)
                    .migrate(SqliteMigrations.phaseSix());

            configuration = compile(documents);
            registry = new ProviderRegistry();
            scheduler = new BukkitPaperTaskScheduler(this);
            registerBuiltInProviders();
            ManualMetricHandle mcMmoAdjustedXp = registerManualProgress(foundation);

            IntegrationTaskScheduler integrationScheduler = new IntegrationTaskScheduler() {
                @Override
                public <T> CompletionStage<T> call(Supplier<T> action) {
                    return scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, action);
                }
            };
            optionalIntegrations = new PhaseSevenOptionalIntegrationManager(
                    this, registry, integrationScheduler, mcMmoAdjustedXp,
                    new MaddPrestigePlaceholderCache(10_000), clock);
            optionalIntegrations.start(configuration.integrations().configuration());

            int recovered = new PendingOperationRecoveryService(new SqliteOperationRepository(foundation),
                    new SqlitePrestigeLifecycleRepository(foundation),
                    new SqliteRecoveryEventRepository(foundation), clock, registry).recover(256).size();
            bindCommand();
            ready = true;
            getLogger().info("MaddPrestige V2 Phase 7 ready; configuration is "
                    + (configuration.stages().active() ? "active" : "safely dormant")
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

    private synchronized RuntimeConfiguration compile(Map<String, String> documents) {
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), Optional.<ConfigRevisionId>empty(), documents,
                new Actor("SYSTEM", Optional.empty(), "Phase 7 production bootstrap"), clock.instant());
        CompiledConfiguration compiled = new ConfigCompiler().compile(draft);
        var stages = new StageConfigurationCompiler().compile(compiled);
        var phaseThree = new PhaseThreeConfigurationCompiler().compile(compiled, Map.<MetricBinding,
                net.maddkraft.maddprestige.api.metric.MetricDescriptor>of());
        var phaseFour = new PhaseFourConfigurationCompiler().compile(compiled);
        PhaseFiveIntegrationCompilation integrations = new PhaseFiveIntegrationCompiler()
                .compile(documents.get("integrations.yml"));
        ArrayList<String> failures = new ArrayList<>();
        stages.validation().findings().stream().filter(finding ->
                finding.severity() == net.maddkraft.maddprestige.api.validation.ValidationSeverity.ERROR)
                .forEach(finding -> failures.add(finding.path() + ": " + finding.explanation()));
        phaseThree.validation().findings().stream().filter(finding ->
                finding.severity() == net.maddkraft.maddprestige.api.validation.ValidationSeverity.ERROR)
                .forEach(finding -> failures.add(finding.path() + ": " + finding.explanation()));
        phaseFour.validation().findings().stream().filter(finding ->
                finding.severity() == net.maddkraft.maddprestige.api.validation.ValidationSeverity.ERROR)
                .forEach(finding -> failures.add(finding.path() + ": " + finding.explanation()));
        integrations.validation().findings().stream().filter(finding ->
                finding.severity() == net.maddkraft.maddprestige.api.validation.ValidationSeverity.ERROR)
                .forEach(finding -> failures.add(finding.path() + ": " + finding.explanation()));
        if (!failures.isEmpty() || stages.configuration().isEmpty()) {
            throw new IllegalArgumentException("Canonical configuration is invalid: " + String.join("; ", failures));
        }
        return new RuntimeConfiguration(Map.copyOf(documents), stages.configuration().orElseThrow(), integrations,
                compiled.contentHash().value(), Instant.now(clock));
    }

    private void registerBuiltInProviders() {
        ProviderHealth health = new ProviderHealth(ProviderHealthState.AVAILABLE,
                "paper.available", "Paper server-thread boundary is available", clock.instant());
        PaperWorldContextMetricProvider world = new PaperWorldContextMetricProvider(
                scheduler, getServer()::getPlayer, () -> health, clock);
        VanillaStatisticsProvider statistics = new VanillaStatisticsProvider(
                new net.maddkraft.maddprestige.api.id.ProviderId("paper_statistics"), "maddprestige",
                scheduler, getServer()::getPlayer, () -> health);
        registerActive(world);
        registerActive(statistics);
    }

    private ManualMetricHandle registerManualProgress(SqliteFoundation foundation) {
        manualWriter = Executors.newSingleThreadExecutor(runnable -> Thread.ofPlatform()
                .name("maddprestige-manual-writer").daemon(true).unstarted(runnable));
        manualProgress = ManualProgressProvider.bootstrap(new ProviderId("phase5_events"), "maddprestige",
                new SqliteManualProgressRepository(foundation), manualWriter, clock, 16, 250_000);
        ManualMetricHandle adjustedXp = manualProgress.owner().registerMetric(new ManualCounterDefinition(
                new MetricId("mcmmo_adjusted_xp_total"), MetricValueType.EXACT_DECIMAL, true, false,
                "mcMMO adjusted XP total", "Official post-adjustment mcMMO XP events", Map.of(
                        "source", "mcmmo", "ownership", "maddprestige"))).toCompletableFuture().join();
        registerOwned(manualProgress.provider(), configuration.integrations().configuration().mcMmoEnabled());
        manualFlushTask = getServer().getScheduler().runTaskTimer(this, () ->
                manualProgress.provider().flushAsync().exceptionally(failure -> {
                    getLogger().log(Level.SEVERE, "Manual progress flush failed; values remain dirty", failure);
                    return 0;
                }), 200L, 200L);
        return adjustedXp;
    }

    private void registerActive(net.maddkraft.maddprestige.api.provider.Provider provider) {
        registerOwned(provider, true);
    }

    private void registerOwned(net.maddkraft.maddprestige.api.provider.Provider provider, boolean active) {
        ProviderRegistration registration = registry.register("maddprestige", provider);
        if (active) {
            registry.activate(registration);
        }
        ownedRegistrations.add(registration);
    }

    private void bindCommand() {
        PluginCommand command = java.util.Objects.requireNonNull(getCommand("maddprestige"),
                "maddprestige command is absent from plugin.yml");
        PhaseSevenCommandExecutor executor = new PhaseSevenCommandExecutor(
                this::statusLines, this::providerLines, this::reloadLines);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private List<String> statusLines() {
        RuntimeConfiguration current = configuration;
        return List.of("[maddprestige.status] V2 Phase 7 " + (ready ? "READY" : "NOT READY"),
                "[maddprestige.config] " + (current.stages().active() ? "active" : "safely dormant")
                        + " hash=" + current.hash(),
                "[maddprestige.providers] " + registry.snapshots().size() + " registered; failures fail closed",
                "[maddprestige.doctor] SQLite migrations, canonical configuration, registry, recovery, commands, "
                        + "and optional lifecycle composition completed.");
    }

    private List<String> providerLines() {
        ArrayList<String> lines = new ArrayList<>(registry.snapshots().stream()
                .map(snapshot -> "[provider] " + snapshot.descriptor().id().value() + " generation="
                        + snapshot.generation() + " activation=" + snapshot.activation() + " health="
                        + snapshot.health().state())
                .toList());
        if (optionalIntegrations != null) {
            lines.addAll(optionalIntegrations.statusLines());
        }
        return List.copyOf(lines);
    }

    private List<String> reloadLines() {
        return List.of(
                "[config.reload.refused] Live file reload is not a configuration mutation authority.",
                "[config.authority] Use the canonical Phase 6 draft, preview, acknowledgement, apply, history, "
                        + "and remap workflow; the active runtime was not changed.",
                "[config.hash] " + configuration.hash());
    }

    private void shutdownOwnedState() {
        ready = false;
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
                manualProgress.provider().closeAsync().toCompletableFuture().join();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Manual progress shutdown flush failed", exception);
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
        if (manualWriter != null) {
            manualWriter.shutdown();
            manualWriter = null;
        }
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

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private record RuntimeConfiguration(
            Map<String, String> documents,
            StageConfiguration stages,
            PhaseFiveIntegrationCompilation integrations,
            String hash,
            Instant loadedAt) {
    }
}
