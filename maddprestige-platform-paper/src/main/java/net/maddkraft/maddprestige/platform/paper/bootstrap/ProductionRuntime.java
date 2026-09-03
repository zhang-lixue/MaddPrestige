package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.provider.ProviderSnapshot;
import net.maddkraft.maddprestige.api.service.CurrencyBalanceView;
import net.maddkraft.maddprestige.api.service.OperationEvaluation;
import net.maddkraft.maddprestige.api.service.OperationEvaluationStatus;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.OperationSimulationView;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.api.service.RequirementProgressStatus;
import net.maddkraft.maddprestige.api.service.RequirementProgressView;
import net.maddkraft.maddprestige.api.service.SeasonView;
import net.maddkraft.maddprestige.api.service.ServiceError;
import net.maddkraft.maddprestige.api.service.StageView;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdministrationService;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.ContextualHelpService;
import net.maddkraft.maddprestige.core.admin.command.PhaseSixCommandService;
import net.maddkraft.maddprestige.core.admin.command.StaffHistoryCommandService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService;
import net.maddkraft.maddprestige.core.admin.config.PhaseSixConfigurationCandidate;
import net.maddkraft.maddprestige.core.admin.config.PhaseSixConfigurationWorkflow;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.ConfigurationHistoryDiagnosticProbe;
import net.maddkraft.maddprestige.core.admin.diagnostic.DatabaseDiagnosticProbe;
import net.maddkraft.maddprestige.core.admin.diagnostic.DatabaseHealth;
import net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticProviderReference;
import net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSubsystemState;
import net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalDiagnosticProbe;
import net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalSnapshot;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.admin.ui.CanonicalGuiActionExecutor;
import net.maddkraft.maddprestige.core.admin.ui.CanonicalGuiMutationExecutor;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiService;
import net.maddkraft.maddprestige.core.admin.ui.StaffGuiService;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistoryPresentation;
import net.maddkraft.maddprestige.core.admin.ui.StaffHistorySource;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerDirectory;
import net.maddkraft.maddprestige.core.admin.ui.StaffPlayerIdentity;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Component;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.ConfigurationSummary;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Health;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Snapshot;
import net.maddkraft.maddprestige.core.admin.ui.StaffSystemStatusSource.Summary;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationService;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PrestigeIntent;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.BaselineInitializationService;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationResult;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.requirement.ScalingStrategy;
import net.maddkraft.maddprestige.core.schema.PhaseSixSchema;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompilation;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationSchema;
import net.maddkraft.maddprestige.persistence.admin.AtomicConfigurationFileStore;
import net.maddkraft.maddprestige.persistence.PrestigeHistoryRecord;
import net.maddkraft.maddprestige.persistence.admin.SqliteConfigurationHistoryStore;
import net.maddkraft.maddprestige.persistence.admin.SqlitePrestigeAdministrationStore;
import net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore;
import net.maddkraft.maddprestige.persistence.plan.PrestigeOperationExecutor;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteCurrencyLedgerStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteDatabaseValidator;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerInitializationStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePrestigeLifecycleRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteRecoveryEventRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteRequirementStateRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteSeasonStore;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.event.ConfigAppliedEvent;
import net.maddkraft.maddprestige.platform.paper.event.PaperOperationLifecycle;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import net.maddkraft.maddprestige.platform.paper.placeholder.PlaceholderSnapshotPublisher;
import net.maddkraft.maddprestige.platform.paper.service.ProductionMaddPrestigeService;
import org.bukkit.plugin.Plugin;

/** Live operation composition backed by the exact Phase 6 configuration revision authority. */
public final class ProductionRuntime implements AutoCloseable {
    private final Plugin plugin;
    private final Clock clock;
    private final ProviderRegistry providers;
    private final SqliteFoundation foundation;
    private final RuntimeConfigurationReconciler runtimeReconciler;
    private final PaperTaskScheduler scheduler;
    private final ConfigurationService canonical = new ConfigurationService();
    private final PhaseSixConfigurationWorkflow configuration;
    private final SqlitePlayerPrestigeRepository prestiges;
    private final SqliteSeasonStore seasons;
    private final SqliteCurrencyLedgerStore currencyLedger;
    private final SqliteOperationRepository operations;
    private final SqlitePlayerInitializationStore playerInitialization;
    private final SqliteRequirementStateRepository requirementStates;
    private final SqliteStageReferenceMigrationStore transitions;
    private final PrestigeAuthorizationService prestigeAuthorization;
    private final PrestigeOperationExecutor prestigeExecutor;
    private final ReentrantLock[] playerInitializationLocks = initializationLocks();
    private final ExecutorService worker;
    private final PaperOperationLifecycle lifecycleEvents;
    private final PlaceholderSnapshotPublisher placeholders;
    private final ProductionMaddPrestigeService service;
    private final PhaseSixCommandService commands;
    private final CommandCompletionService completion;
    private final OperationConfirmationService confirmations;
    private final GuiSessionService gui;
    private final PlayerGuiService playerGui;
    private final StaffGuiService staffGui;
    private final AtomicReference<List<StaffPlayerIdentity>> knownStaffPlayers =
            new AtomicReference<>(List.of());
    private final AtomicReference<ConfigRevisionId> publishedRevision = new AtomicReference<>();
    private final AtomicReference<StoredConfigurationRevision> authoritativeRevision = new AtomicReference<>();
    private final AtomicBoolean providerRecompositionQueued = new AtomicBoolean();
    private final AtomicReference<ValidationReport> latestValidation =
            new AtomicReference<>(ValidationReport.VALID);
    private final AtomicReference<Optional<String>> compositionFailure =
            new AtomicReference<>(Optional.empty());
    private final AutoCloseable providerLifecycle;

    public ProductionRuntime(
            Plugin plugin,
            ProviderRegistry providers,
            SqliteFoundation foundation,
            Optional<StoredConfigurationRevision> startup,
            Path dataDirectory,
            Clock clock,
            PaperTaskScheduler scheduler,
            MaddPrestigePlaceholderCache placeholderCache,
            RuntimeConfigurationReconciler runtimeReconciler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.foundation = Objects.requireNonNull(foundation, "SQLite foundation");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.runtimeReconciler = Objects.requireNonNull(runtimeReconciler, "runtime reconciler");
        Objects.requireNonNull(startup, "startup configuration");
        prestiges = new SqlitePlayerPrestigeRepository(foundation);
        seasons = new SqliteSeasonStore(foundation);
        operations = new SqliteOperationRepository(foundation);
        playerInitialization = new SqlitePlayerInitializationStore(foundation);
        transitions = new SqliteStageReferenceMigrationStore(foundation);
        configuration = new PhaseSixConfigurationWorkflow(canonical, providers, transitions,
                List.of(compiled -> integrationCompilation(compiled.documents()).validation()));

        requirementStates = new SqliteRequirementStateRepository(foundation);
        SqlitePrestigeLifecycleRepository prestigeLifecycle = new SqlitePrestigeLifecycleRepository(foundation);
        currencyLedger = new SqliteCurrencyLedgerStore(foundation);
        worker = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("maddprestige-operation-", 0).factory());
        lifecycleEvents = new PaperOperationLifecycle(plugin, scheduler, clock);
        AuthoritativeProgressContextFactory progressContexts =
                new AuthoritativeProgressContextFactory(prestiges::find, seasons);
        prestigeAuthorization = new PrestigeAuthorizationService(configuration::active,
                this::prestigeState, progressContexts::prestige, requirementStates, providers, currencyLedger,
                prestigeLifecycle, seasons::active, clock);
        prestigeExecutor = new PrestigeOperationExecutor(prestigeLifecycle, operations, providers,
                this::activeRevision, clock, lifecycleEvents, this::revalidatePrestige,
                this::initializePlayerAfterPre);
        placeholders = new PlaceholderSnapshotPublisher(plugin, placeholderCache, prestiges,
                this::initializePlayerLifecycleOutcome);
        service = new ProductionMaddPrestigeService(this::progress, this::stageCatalog, this::evaluateRankUp,
                this::evaluatePrestige, this::currencyBalances, this::activeSeason, this::rankUp, this::prestige,
                providers, lifecycleEvents);

        SchemaRegistry schema = productionSchema();
        SqliteConfigurationHistoryStore history = new SqliteConfigurationHistoryStore(foundation);
        AtomicConfigurationFileStore snapshots = new AtomicConfigurationFileStore(
                dataDirectory.resolve("configuration"), clock);
        ConfigurationAdministrationService administration = new ConfigurationAdministrationService(canonical,
                configuration, schema, history, snapshots, clock, this::configurationApplied);
        ConfigurationIntrospectionService introspection = new ConfigurationIntrospectionService(schema,
                canonical::active, latestValidation::get);
        OperationPreviewService previews = new OperationPreviewService(
                ignored -> CompletableFuture.completedFuture(RankUpAuthorizationResult.rejected(
                        "Rank-up is compatibility-only; use numeric Prestige")),
                intent -> CompletableFuture.supplyAsync(() -> authorizePrestige(intent), worker));
        confirmations = new OperationConfirmationService(previews,
                ignored -> CompletableFuture.failedFuture(new IllegalStateException(
                        "Rank-up execution is compatibility-only")),
                plan -> CompletableFuture.supplyAsync(() -> prestigeExecutor.execute(plan), worker),
                this::activeRevision, () -> configuration.active()
                        .map(active -> active.phaseFour().configuration().prestige().confirmationMaximumLifetime())
                        .orElse(OperationConfirmationService.DEFAULT_MAXIMUM_LIFETIME), clock);
        Supplier<CompletionStage<DatabaseHealth>> databaseHealth = () -> CompletableFuture.supplyAsync(() -> {
            var validation = SqliteDatabaseValidator.validate(
                    foundation.databaseFile(), SqliteMigrations.phaseNineB());
            return new DatabaseHealth(true, validation.schemaVersion() == 12, "SQLite",
                    "validated schema " + validation.schemaVersion());
        }, worker);
        var databaseProbe = new DatabaseDiagnosticProbe(databaseHealth);
        var operationalProbe = new PhaseSixOperationalDiagnosticProbe(providers,
                () -> CompletableFuture.supplyAsync(this::operationalDiagnosticSnapshot, worker));
        var historyProbe = new ConfigurationHistoryDiagnosticProbe(history, worker);
        DoctorService doctor = new DoctorService(providers, canonical::active,
                List.of(databaseProbe, operationalProbe, historyProbe), clock);
        WhyService why = new WhyService(
                ignored -> CompletableFuture.completedFuture(RankUpAuthorizationResult.rejected(
                        "Rank-up is compatibility-only; use numeric Prestige")),
                intent -> CompletableFuture.supplyAsync(() -> authorizePrestige(intent), worker));
        PlayerProgressViewService playerViews = new PlayerProgressViewService(previews);
        ManualPrestigeAdministrationService manualPrestige = new ManualPrestigeAdministrationService(
                new SqlitePrestigeAdministrationStore(foundation, clock),
                () -> activeRevision().orElseThrow(), worker, this::initializePlayerLifecycle);
        CanonicalGuiMutationExecutor mutations = new CanonicalGuiMutationExecutor(administration);
        CanonicalGuiActionExecutor guiActions = new CanonicalGuiActionExecutor(playerViews, previews, confirmations,
                doctor, administration, mutations);
        gui = new GuiSessionService(this::activeRevision, guiActions, mutations, Duration.ofMinutes(5), clock);
        playerGui = new PlayerGuiService(gui, playerViews, previews, confirmations);
        refreshKnownStaffPlayers();
        StaffPlayerDirectory staffPlayers = new StaffPlayerDirectory() {
            @Override
            public List<StaffPlayerIdentity> onlinePlayers() {
                return knownStaffPlayers.get().stream().filter(StaffPlayerIdentity::online).toList();
            }

            @Override
            public List<StaffPlayerIdentity> knownPlayers() {
                return knownStaffPlayers.get();
            }
        };
        var recoveryEvents = new SqliteRecoveryEventRepository(foundation);
        StaffHistorySource staffHistory = new StaffHistorySource() {
            @Override
            public CompletionStage<Page> recent(int offset, int limit) {
                Map<UUID, String> visibleNames = visiblePlayerNames();
                return CompletableFuture.supplyAsync(() -> historyPage(
                        prestigeLifecycle.recentHistory(offset, limit), offset, visibleNames, recoveryEvents), worker);
            }

            @Override
            public CompletionStage<Page> forPlayer(UUID playerId, int offset, int limit) {
                Map<UUID, String> visibleNames = visiblePlayerNames();
                return CompletableFuture.supplyAsync(() -> historyPage(
                        prestigeLifecycle.history(playerId, offset, limit), offset, visibleNames, recoveryEvents), worker);
            }

            @Override
            public CompletionStage<Optional<Entry>> entry(UUID playerId, UUID entryId) {
                Map<UUID, String> visibleNames = visiblePlayerNames();
                OperationId operationId = new OperationId(entryId);
                return CompletableFuture.supplyAsync(() -> prestigeLifecycle.historyEntry(playerId, operationId)
                        .map(value -> staffHistoryEntry(value, visibleNames,
                                !recoveryEvents.find(value.operationId(), 1).isEmpty())), worker);
            }
        };
        StaffSystemStatusSource staffStatus = new StaffSystemStatusSource() {
            @Override
            public Summary summary() {
                return staffSystemSummary();
            }

            @Override
            public CompletionStage<Snapshot> inspect() {
                Summary summary = summary();
                return databaseHealth.get().handle((database, failure) ->
                        staffSystemSnapshot(summary, database, failure));
            }

            @Override
            public ConfigurationSummary configuration() {
                return staffConfigurationSummary();
            }
        };
        staffGui = new StaffGuiService(gui, playerViews, staffPlayers,
                this::activeRevision, staffHistory, staffStatus);
        StaffHistoryCommandService historyCommands = new StaffHistoryCommandService(staffHistory, staffPlayers);
        SetupWizardService setupWizard = new SetupWizardService(administration, providers);
        commands = new PhaseSixCommandService(new ContextualHelpService(schema), introspection, administration,
                doctor, why, previews, confirmations, playerViews, setupWizard,
                manualPrestige, gui, playerGui, staffGui, historyCommands, this::activeRevision, worker);
        completion = new CommandCompletionService(setupWizard, confirmations, historyCommands);

        providerLifecycle = providers.addLifecycleListener(ignored -> recomposeForProviderLifecycle());
        startup.ifPresent(this::publishStartup);
        placeholders.start();
    }

    public ProductionMaddPrestigeService service() {
        return service;
    }

    public PhaseSixConfigurationWorkflow configuration() {
        return configuration;
    }

    public Optional<ConfigRevisionId> revision() {
        return activeRevision();
    }

    /** Exact durable revision selected by startup/apply, including while runtime composition is fail-closed. */
    public Optional<ConfigRevisionId> authoritativeRevision() {
        return Optional.ofNullable(authoritativeRevision.get()).map(StoredConfigurationRevision::id);
    }

    public boolean operational() {
        return activeRevision().filter(value -> value.equals(publishedRevision.get())).isPresent();
    }

    public Optional<String> compositionFailure() {
        return compositionFailure.get();
    }

    public PhaseSixCommandService commands() {
        return commands;
    }

    public CommandCompletionService completion() {
        return completion;
    }

    public GuiSessionService gui() {
        return gui;
    }

    public PlayerGuiService playerGui() {
        return playerGui;
    }

    public StaffGuiService staffGui() {
        return staffGui;
    }

    public void beginPlayerConfirmationSession(UUID playerId) {
        registerOnlineStaffPlayer(playerId);
        confirmations.beginPlayerSession(playerId);
        playerGui.invalidatePlayer(playerId);
        staffGui.invalidateStaff(playerId);
    }

    public void endPlayerConfirmationSession(UUID playerId) {
        knownStaffPlayers.updateAndGet(players -> players.stream()
                .map(player -> player.playerId().equals(playerId)
                        ? new StaffPlayerIdentity(player.playerId(), player.name(), false)
                        : player)
                .toList());
        confirmations.endPlayerSession(playerId);
        playerGui.invalidatePlayer(playerId);
        staffGui.invalidateStaff(playerId);
    }

    private Map<UUID, String> visiblePlayerNames() {
        return knownStaffPlayers.get().stream().collect(Collectors.toMap(
                StaffPlayerIdentity::playerId, StaffPlayerIdentity::name));
    }

    private void refreshKnownStaffPlayers() {
        java.util.LinkedHashMap<UUID, StaffPlayerIdentity> known = new java.util.LinkedHashMap<>();
        org.bukkit.OfflinePlayer[] offlinePlayers = plugin.getServer().getOfflinePlayers();
        if (offlinePlayers != null) {
            for (org.bukkit.OfflinePlayer player : offlinePlayers) {
                if (player != null && player.getName() != null && !player.getName().isBlank()) {
                    known.put(player.getUniqueId(), new StaffPlayerIdentity(
                            player.getUniqueId(), player.getName(), player.isOnline()));
                }
            }
        }
        for (UUID playerId : prestiges.knownPlayerIds()) {
            if (known.containsKey(playerId)) {
                continue;
            }
            org.bukkit.OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
            String name = player == null || player.getName() == null || player.getName().isBlank()
                    ? playerId.toString() : player.getName();
            known.put(playerId, new StaffPlayerIdentity(playerId, name,
                    player != null && player.isOnline()));
        }
        java.util.Collection<? extends org.bukkit.entity.Player> online = plugin.getServer().getOnlinePlayers();
        if (online != null) {
            online.forEach(player -> known.put(player.getUniqueId(),
                    new StaffPlayerIdentity(player.getUniqueId(), player.getName(), true)));
        }
        knownStaffPlayers.set(List.copyOf(known.values()));
    }

    private void registerOnlineStaffPlayer(UUID playerId) {
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        StaffPlayerIdentity identity = new StaffPlayerIdentity(playerId, player.getName(), true);
        knownStaffPlayers.updateAndGet(players -> {
            ArrayList<StaffPlayerIdentity> updated = new ArrayList<>(players.stream()
                    .filter(existing -> !existing.playerId().equals(playerId)).toList());
            updated.add(identity);
            return List.copyOf(updated);
        });
    }

    private StaffHistorySource.Page historyPage(
            net.maddkraft.maddprestige.persistence.PrestigeHistoryPage page,
            int offset,
            Map<UUID, String> visibleNames,
            SqliteRecoveryEventRepository recoveryEvents) {
        List<StaffHistorySource.Entry> entries = page.entries().stream()
                .map(entry -> staffHistoryEntry(entry, visibleNames,
                        !recoveryEvents.find(entry.operationId(), 1).isEmpty()))
                .toList();
        return new StaffHistorySource.Page(entries, offset > 0,
                (long) offset + entries.size() < page.totalEntries(), page.totalEntries());
    }

    private StaffHistorySource.Entry staffHistoryEntry(
            PrestigeHistoryRecord entry,
            Map<UUID, String> visibleNames,
            boolean recovered) {
        boolean costRecorded = recordedSnapshot(entry.costsSnapshot());
        boolean rewardRecorded = recordedSnapshot(entry.rewardsSnapshot());
        return new StaffHistorySource.Entry(entry.operationId().value(),
                visibleNames.getOrDefault(entry.playerId(), entry.playerId().toString()),
                entry.currentBefore(), entry.currentAfter(), historyOutcome(entry.result(), recovered),
                costRecorded, rewardRecorded, Optional.empty(), Optional.empty(),
                StaffHistoryPresentation.snapshotAmount(entry.costsSnapshot()),
                StaffHistoryPresentation.snapshotAmount(entry.rewardsSnapshot()),
                entry.occurredAt());
    }

    private static StaffHistorySource.Outcome historyOutcome(String result, boolean recovered) {
        String normalized = result.toUpperCase(Locale.ROOT);
        if (normalized.equals("COMPLETED") || normalized.endsWith("_COMPLETED")) {
            if (recovered) {
                return StaffHistorySource.Outcome.RECOVERED;
            }
            return StaffHistorySource.Outcome.COMPLETED;
        }
        if (normalized.contains("REJECT") || normalized.contains("CANCEL")) {
            return StaffHistorySource.Outcome.REJECTED;
        }
        if (normalized.contains("FAIL") || normalized.contains("ROLLBACK")) {
            return StaffHistorySource.Outcome.FAILED;
        }
        if (normalized.contains("COMMITTED") || normalized.contains("RECONCIL")
                || normalized.contains("ATTENTION")) {
            return StaffHistorySource.Outcome.ATTENTION;
        }
        return StaffHistorySource.Outcome.IN_PROGRESS;
    }

    private static boolean recordedSnapshot(String snapshot) {
        String value = snapshot.trim();
        return !value.isEmpty() && !value.equals("[]") && !value.equals("{}");
    }

    private Summary staffSystemSummary() {
        List<ProviderSnapshot> active = activeProviderSnapshots();
        int available = (int) active.stream()
                .filter(snapshot -> providerHealth(snapshot.health().state()) == Health.HEALTHY)
                .count();
        Health health = activeRevision().isEmpty() ? Health.BLOCKED : Health.HEALTHY;
        for (ProviderSnapshot snapshot : active) {
            health = worst(health, providerHealth(snapshot.health().state()));
        }
        return new Summary(health, activeRevision().isPresent(), available, active.size());
    }

    private ConfigurationSummary staffConfigurationSummary() {
        Optional<ActivePhaseFourConfiguration> active = configuration.active();
        if (active.isEmpty()) {
            return ConfigurationSummary.inactive();
        }
        ActivePhaseFourConfiguration current = active.orElseThrow();
        var phaseThree = current.priorPhases().phaseThree().configuration();
        var phaseFour = current.phaseFour().configuration();
        var prestige = phaseFour.prestige();
        List<RequirementDefinition> requirements = activeRequirements(phaseThree.trees(),
                prestige.requirementTreeId());
        java.util.LinkedHashSet<String> providerNames = new java.util.LinkedHashSet<>();
        requirements.stream().map(RequirementDefinition::providerId).map(ProductionRuntime::providerLabel)
                .sorted().forEach(providerNames::add);
        prestige.costIds().stream().map(phaseThree.costs()::get).filter(Objects::nonNull)
                .map(value -> providerLabel(value.providerId())).sorted().forEach(providerNames::add);
        prestige.rewardIds().stream().map(phaseThree.rewards()::get).filter(Objects::nonNull)
                .map(value -> providerLabel(value.providerId())).sorted().forEach(providerNames::add);
        long requirementScaling = requirements.stream().filter(ProductionRuntime::scaled).count();
        long costScaling = prestige.costIds().stream().filter(phaseFour.valueScaling().costs()::containsKey).count();
        long rewardScaling = prestige.rewardIds().stream()
                .filter(phaseFour.valueScaling().rewards()::containsKey).count();
        int scalingProfiles = Math.toIntExact(requirementScaling + costScaling + rewardScaling);
        String range = prestige.enabled()
                ? prestige.limit().maximum().isPresent()
                        ? "P1 – P" + prestige.limit().maximum().getAsLong()
                        : "P1+"
                : "Disabled";
        boolean published = activeRevision().isPresent();
        boolean usable = operational() && prestige.enabled() && !latestValidation.get().hasErrors();
        return new ConfigurationSummary(published, usable, range, requirements.size(),
                prestige.costIds().size(), prestige.rewardIds().size(), scalingProfiles,
                List.copyOf(providerNames));
    }

    private static List<RequirementDefinition> activeRequirements(
            Map<net.maddkraft.maddprestige.api.id.RequirementId, RequirementNode> trees,
            Optional<net.maddkraft.maddprestige.api.id.RequirementId> rootId) {
        java.util.LinkedHashMap<net.maddkraft.maddprestige.api.id.RequirementId, RequirementDefinition> result =
                new java.util.LinkedHashMap<>();
        rootId.map(trees::get).ifPresent(root -> collectRequirements(root, result));
        return List.copyOf(result.values());
    }

    private static void collectRequirements(
            RequirementNode node,
            Map<net.maddkraft.maddprestige.api.id.RequirementId, RequirementDefinition> destination) {
        if (node instanceof RequirementLeaf leaf) {
            destination.putIfAbsent(leaf.id(), leaf.definition());
        } else if (node instanceof RequirementGroup group) {
            group.children().forEach(child -> collectRequirements(child.node(), destination));
        }
    }

    private static boolean scaled(RequirementDefinition requirement) {
        return requirement.scaling().strategy() != ScalingStrategy.NONE
                || !requirement.scaling().segments().isEmpty();
    }

    private Snapshot staffSystemSnapshot(
            Summary summary,
            DatabaseHealth database,
            Throwable failure) {
        ArrayList<Component> components = new ArrayList<>();
        Optional<ConfigRevisionId> revision = activeRevision();
        components.add(new Component("Configuration",
                revision.isPresent() ? Health.HEALTHY : Health.BLOCKED,
                revision.isPresent() ? "Active" : "Inactive",
                revision.map(value -> "Active revision " + value.value())
                        .orElse("No active configuration")));

        LinkedHashMap<String, ProviderHealthState> providerGroups = new LinkedHashMap<>();
        activeProviderSnapshots().stream()
                .sorted(java.util.Comparator.comparing(snapshot -> providerLabel(snapshot.descriptor().id())))
                .forEach(snapshot -> providerGroups.merge(providerLabel(snapshot.descriptor().id()),
                        snapshot.health().state(), ProductionRuntime::worseProviderState));
        if (providerGroups.isEmpty()) {
            components.add(new Component("Integrations", Health.HEALTHY,
                    "Not configured", "No configured integrations"));
        } else {
            providerGroups.forEach((name, state) -> components.add(new Component(name,
                    providerHealth(state), providerStatus(state), "")));
        }

        Health persistenceHealth;
        String persistenceDetail;
        if (failure != null || database == null || !database.reachable()) {
            persistenceHealth = Health.BLOCKED;
            persistenceDetail = "Persistence validation unavailable";
        } else if (!database.migrationsCurrent()) {
            persistenceHealth = Health.WARNING;
            persistenceDetail = database.backend() + " schema requires attention";
        } else {
            persistenceHealth = Health.HEALTHY;
            persistenceDetail = database.backend() + " schema is current";
        }
        components.add(new Component("Persistence", persistenceHealth,
                switch (persistenceHealth) {
                    case HEALTHY -> "Available";
                    case WARNING -> "Attention";
                    case BLOCKED -> "Unavailable";
                }, persistenceDetail));
        Summary detailed = new Summary(worst(summary.health(), persistenceHealth),
                summary.configurationActive(), summary.availableProviders(), summary.totalProviders());
        return new Snapshot(detailed, components);
    }

    private List<ProviderSnapshot> activeProviderSnapshots() {
        return providers.snapshots().stream()
                .filter(snapshot -> snapshot.activation() == ActivationState.ACTIVE)
                .toList();
    }

    private static Health providerHealth(ProviderHealthState state) {
        return switch (state) {
            case AVAILABLE, ACTIVE -> Health.HEALTHY;
            case INACTIVE, DEGRADED -> Health.WARNING;
            case NOT_INSTALLED, UNSUPPORTED, UNAVAILABLE, UNHEALTHY -> Health.BLOCKED;
        };
    }

    private static ProviderHealthState worseProviderState(
            ProviderHealthState left,
            ProviderHealthState right) {
        Health worst = worst(providerHealth(left), providerHealth(right));
        return providerHealth(left) == worst ? left : right;
    }

    private static String providerStatus(ProviderHealthState state) {
        return switch (state) {
            case AVAILABLE, ACTIVE -> "Available";
            case INACTIVE -> "Inactive";
            case DEGRADED -> "Degraded";
            case UNSUPPORTED -> "Unsupported";
            case NOT_INSTALLED -> "Not installed";
            case UNAVAILABLE -> "Unavailable";
            case UNHEALTHY -> "Unhealthy";
        };
    }

    private static Health worst(Health left, Health right) {
        if (left == Health.BLOCKED || right == Health.BLOCKED) {
            return Health.BLOCKED;
        }
        if (left == Health.WARNING || right == Health.WARNING) {
            return Health.WARNING;
        }
        return Health.HEALTHY;
    }

    private static String providerLabel(ProviderId providerId) {
        String id = providerId.value();
        if (id.startsWith("vault_")) {
            return "Vault";
        }
        if (id.equals("mcmmo")) {
            return "mcMMO";
        }
        if (id.equals("luckperms")) {
            return "LuckPerms";
        }
        if (id.contains("internal")) {
            return "Internal Currency";
        }
        if (id.equals("placeholder_input")) {
            return "PlaceholderAPI";
        }
        if (id.equals("paper_statistics")) {
            return "Minecraft Statistics";
        }
        if (id.equals("paper_world_context")) {
            return "World Context";
        }
        return java.util.Arrays.stream(id.split("[_:.\\-]+"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private PhaseSixOperationalSnapshot operationalDiagnosticSnapshot() {
        var current = configuration.active();
        int schemaVersion = current.map(value -> value.priorPhases().phaseThree().configuration().schemaVersion())
                .orElse(3);
        List<DiagnosticProviderReference> metrics = current.stream()
                .flatMap(value -> value.priorPhases().phaseThree().configuration().requirements().values().stream())
                .map(requirement -> new DiagnosticProviderReference(requirement.providerId(),
                        Optional.of(requirement.metricId()),
                        "requirements." + requirement.id().value() + ".metric"))
                .sorted(java.util.Comparator.comparing(DiagnosticProviderReference::path)).toList();
        List<DiagnosticProviderReference> costs = current.stream()
                .flatMap(value -> value.priorPhases().phaseThree().configuration().costs().values().stream())
                .map(cost -> new DiagnosticProviderReference(cost.providerId(), Optional.empty(),
                        "costs." + cost.id().value() + ".provider"))
                .sorted(java.util.Comparator.comparing(DiagnosticProviderReference::path)).toList();
        List<DiagnosticProviderReference> rewards = current.stream()
                .flatMap(value -> value.priorPhases().phaseThree().configuration().rewards().values().stream())
                .map(reward -> new DiagnosticProviderReference(reward.providerId(), Optional.empty(),
                        "rewards." + reward.id().value() + ".provider"))
                .sorted(java.util.Comparator.comparing(DiagnosticProviderReference::path)).toList();

        LinkedHashMap<String, String> pending = new LinkedHashMap<>();
        LinkedHashMap<String, String> reconciliation = new LinkedHashMap<>();
        operations.findIncomplete(1000).forEach(operation -> {
            var target = operation.state() == net.maddkraft.maddprestige.api.operation.OperationState.NEEDS_RECONCILIATION
                    ? reconciliation : pending;
            target.put(operation.operationId().toString(), operation.state().name());
        });
        DiagnosticSubsystemState placeholder = placeholderDiagnosticState();
        DiagnosticSubsystemState schedulerState = DiagnosticSubsystemState.healthy(
                "The bounded Paper scheduler and immutable presentation cache are composed.");
        DiagnosticSubsystemState flushState = DiagnosticSubsystemState.healthy(
                "Manual-progress drain health is exposed through the provider registry; no failed drain is active.");

        return new PhaseSixOperationalSnapshot(schemaVersion, 3, metrics, costs, rewards,
                Map.copyOf(pending), Map.copyOf(reconciliation),
                Map.of(), Map.of(), Map.of(), Set.of(), List.of(), List.of(), placeholder, schedulerState,
                flushState, Map.of());
    }

    private DiagnosticSubsystemState placeholderDiagnosticState() {
        boolean enabled = Optional.ofNullable(authoritativeRevision.get()).map(StoredConfigurationRevision::compiled)
                .map(value -> value.documents().get("integrations.yml")).filter(Objects::nonNull)
                .map(source -> new PhaseFiveIntegrationCompiler().compile(source).configuration()
                        .placeholderOutputEnabled()).orElse(false);
        if (!enabled) {
            return DiagnosticSubsystemState.healthy("PlaceholderAPI output is disabled and was checked.");
        }
        boolean available = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        return available ? DiagnosticSubsystemState.healthy("PlaceholderAPI output is enabled and bound.")
                : new DiagnosticSubsystemState(
                        net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSeverity.BLOCKED,
                        "PlaceholderAPI output is enabled but PlaceholderAPI is unavailable.");
    }

    private CompletionStage<PlayerProgressSnapshot> progress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            requirePlayerLifecycle(playerId);
            var prestige = prestiges.find(playerId);
            placeholders.refresh(playerId);
            return new PlayerProgressSnapshot(playerId, Optional.empty(),
                    prestige.map(PlayerPrestigeState::currentPrestige).orElse(0L),
                    prestige.map(PlayerPrestigeState::lifetimePrestige).orElse(0L), activeRevision(), Map.of(),
                    clock.instant());
        }, worker);
    }

    private List<StageView> stageCatalog() {
        return List.of();
    }

    private CompletionStage<OperationEvaluation> evaluateRankUp(UUID playerId) {
        return CompletableFuture.completedFuture(new OperationEvaluation(OperationKind.RANK_UP,
                OperationEvaluationStatus.BLOCKED, Optional.empty(), Optional.empty(), activeRevision(),
                List.of(error("rankup.compatibility_only",
                        "Rank-up is a retained API compatibility operation, not V2 Prestige progression")),
                List.of(), Optional.empty(), clock.instant()));
    }

    private CompletionStage<OperationEvaluation> evaluatePrestige(UUID playerId) {
        if (!operational()) {
            return CompletableFuture.completedFuture(unavailableEvaluation(OperationKind.PRESTIGE));
        }
        return CompletableFuture.supplyAsync(() -> authorizePrestige(new PrestigeIntent(
                player(playerId), playerId, "api-evaluate-prestige-" + UUID.randomUUID())), worker)
                .thenApply(value -> prestigeEvaluation(playerId, value));
    }

    private OperationEvaluation prestigeEvaluation(UUID playerId, PrestigeAuthorizationResult authorization) {
        if (authorization.plan().isEmpty()) {
            return new OperationEvaluation(OperationKind.PRESTIGE, OperationEvaluationStatus.BLOCKED,
                    Optional.empty(), Optional.empty(), activeRevision(),
                    blockers(authorization.rejection().stream().toList()), List.of(), Optional.empty(),
                    clock.instant());
        }
        PrestigePlan plan = authorization.plan().orElseThrow();
        return new OperationEvaluation(OperationKind.PRESTIGE,
                plan.executionAllowed() ? OperationEvaluationStatus.ELIGIBLE : OperationEvaluationStatus.BLOCKED,
                Optional.empty(), Optional.empty(),
                Optional.of(plan.configRevision()), blockers(plan.blockers()),
                flatten(OperationKind.PRESTIGE, plan.simulation().requirements().result()),
                Optional.of(simulation(plan)), clock.instant());
    }

    private CompletionStage<List<CurrencyBalanceView>> currencyBalances(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            requirePlayerLifecycle(playerId);
            return configuration.active().map(active ->
                active.phaseFour().configuration().currencies().values().stream()
                        .sorted(java.util.Comparator.comparing(value -> value.id().value()))
                        .map(currency -> new CurrencyBalanceView(currency.id().value(), MetricValue.fromNumber(
                                MetricValueType.CURRENCY_AMOUNT,
                                currencyLedger.balance(playerId, currency.id()).asBigDecimal()), currency.scale(),
                                currency.prestigeScoped())).toList()).orElse(List.of());
        }, worker);
    }

    private CompletionStage<Optional<SeasonView>> activeSeason(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            requirePlayerLifecycle(playerId);
            return seasons.active().seasonId().flatMap(seasons::find).map(record ->
                new SeasonView(record.id().value(), record.configRevision(), MetricValue.fromNumber(
                        MetricValueType.EXACT_DECIMAL,
                        seasons.playerProgress(playerId, record.id()).asBigDecimal()), record.startedAt()));
        }, worker);
    }

    private CompletionStage<OperationResult> rankUp(UUID playerId, UUID requestId) {
        return CompletableFuture.completedFuture(blocked(requestId, OperationKind.RANK_UP,
                List.of("Rank-up is compatibility-only; use numeric Prestige")));
    }

    private CompletionStage<OperationResult> prestige(UUID playerId, UUID requestId) {
        if (!operational()) {
            return CompletableFuture.completedFuture(unavailable(requestId, OperationKind.PRESTIGE));
        }
        return CompletableFuture.supplyAsync(() -> authorizePrestigeOperation(new PrestigeIntent(
                player(playerId), playerId, requestId, "api-prestige-" + requestId)), worker)
                .thenApply(value -> executePrestige(requestId, value))
                .whenComplete((result, failure) -> {
                    if (OperationPlaceholderRefreshPolicy.shouldRefresh(result, failure)) {
                        placeholders.refresh(playerId);
                    }
                });
    }

    private PrestigeAuthorizationResult authorizePrestige(PrestigeIntent intent) {
        Optional<String> initializationFailure = initializePlayerLifecycle(intent.playerId());
        if (initializationFailure.isPresent()) {
            return PrestigeAuthorizationResult.rejected(initializationFailure.orElseThrow());
        }
        return authorizePrestigeRaw(intent);
    }

    private PrestigeAuthorizationResult authorizePrestigeOperation(PrestigeIntent intent) {
        PrestigeAuthorizationResult authorization = authorizePrestigeRaw(intent);
        if (authorization.plan().map(PrestigePlan::executionAllowed).orElse(false)) {
            return authorization;
        }
        Optional<String> initializationFailure = initializePlayerLifecycle(intent.playerId());
        return initializationFailure.isPresent()
                ? PrestigeAuthorizationResult.rejected(initializationFailure.orElseThrow())
                : authorizePrestigeRaw(intent);
    }

    private PrestigeAuthorizationResult authorizePrestigeRaw(PrestigeIntent intent) {
        return prestigeAuthorization.authorize(intent).toCompletableFuture().join();
    }

    private Optional<String> initializePlayerLifecycle(UUID playerId) {
        PlaceholderSnapshotPublisher.InitializationResult result = initializePlayerLifecycleOutcome(playerId);
        return result.dormant()
                ? Optional.of("No canonical configuration is active for player initialization")
                : result.failure();
    }

    private PlaceholderSnapshotPublisher.InitializationResult initializePlayerLifecycleOutcome(UUID playerId) {
        ReentrantLock lock = playerInitializationLocks[Math.floorMod(playerId.hashCode(),
                playerInitializationLocks.length)];
        lock.lock();
        try {
            ActivePhaseFourConfiguration active = configuration.active().orElse(null);
            if (active == null) {
                return PlaceholderSnapshotPublisher.InitializationResult.dormantResult();
            }
            Optional<PlayerPrestigeState> existingPrestige = prestiges.find(playerId);
            if (existingPrestige.isEmpty()) {
                Optional<String> initializationFailure = initializePlayerState(playerId, active);
                if (initializationFailure.isPresent()) {
                    return initializationFailed(initializationFailure.orElseThrow());
                }
                existingPrestige = prestiges.find(playerId);
            }
            return existingPrestige.isPresent() ? PlaceholderSnapshotPublisher.InitializationResult.ready()
                    : initializationFailed("Numeric Prestige state initialization produced no durable row");
        } catch (RuntimeException exception) {
            return initializationFailed("Player lifecycle initialization failed: " + rootMessage(exception));
        } finally {
            lock.unlock();
        }
    }

    private static PlaceholderSnapshotPublisher.InitializationResult initializationFailed(String failure) {
        return PlaceholderSnapshotPublisher.InitializationResult.failed(failure);
    }

    private Optional<String> initializePlayerState(
            UUID playerId,
            ActivePhaseFourConfiguration active) {
        try {
            var phaseThree = active.priorPhases().phaseThree();
            List<RequirementDefinition> definitions = phaseThree.configuration().requirements().values().stream()
                    .filter(definition -> definition.scope() == MeasurementScope.SINCE_PRESTIGE_START)
                    .sorted(java.util.Comparator.comparing(definition -> definition.id().value())).toList();
            LinkedHashMap<ProviderId, List<RequirementDefinition>> grouped = new LinkedHashMap<>();
            definitions.forEach(definition -> grouped.computeIfAbsent(
                    definition.providerId(), ignored -> new java.util.ArrayList<>()).add(definition));
            LinkedHashMap<net.maddkraft.maddprestige.api.id.RequirementId, MetricSample> samples =
                    new LinkedHashMap<>();
            for (var entry : grouped.entrySet()) {
                ProviderId providerId = entry.getKey();
                Long generation = phaseThree.providerGenerations().get(providerId);
                providers.refreshHealth(providerId);
                var snapshot = providers.find(providerId);
                var provider = providers.provider(providerId)
                        .filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast);
                if (generation == null || snapshot.isEmpty() || provider.isEmpty()
                        || snapshot.orElseThrow().generation() != generation
                        || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                        || !healthy(snapshot.orElseThrow().health().state())) {
                    return Optional.of("Player initialization metric provider is unavailable: "
                            + providerId.value());
                }
                LinkedHashMap<RequirementDefinition, MetricQuery> queries = new LinkedHashMap<>();
                entry.getValue().forEach(definition -> queries.put(definition,
                        new MetricQuery(definition.metricId(), definition.scope().readMode(), definition.filters())));
                CompletionStage<Map<MetricQuery, MetricSample>> read = provider.orElseThrow().read(
                        playerId, queries.values().stream().distinct().toList(), generation);
                if (read == null) {
                    return Optional.of("Player initialization metric provider returned no read stage: "
                            + providerId.value());
                }
                Map<MetricQuery, MetricSample> collected = read.toCompletableFuture().join();
                for (var query : queries.entrySet()) {
                    MetricSample sample = collected.get(query.getValue());
                    if (sample == null || sample.status() != MetricSampleStatus.AVAILABLE
                            || sample.providerGeneration() != generation) {
                        return Optional.of("Player initialization metric sample is unavailable: "
                                + query.getKey().id().value());
                    }
                    samples.put(query.getKey().id(), sample);
                }
            }
            var scope = AuthoritativeProgressContextFactory.initialPrestigeScope(playerId);
            var baselines = new BaselineInitializationService(requirementStates, clock).prepareScope(
                    playerId, MeasurementScope.SINCE_PRESTIGE_START, scope, definitions, samples,
                    phaseThree.providerGenerations());
            playerInitialization.initializePrestige(playerId, active.phaseFour().revisionId(), scope,
                    clock.instant(), baselines);
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.of("Player lifecycle initialization failed: " + rootMessage(exception));
        }
    }

    private void requirePlayerLifecycle(UUID playerId) {
        initializePlayerLifecycle(playerId).ifPresent(failure -> {
            throw new IllegalStateException(failure);
        });
    }

    private static ReentrantLock[] initializationLocks() {
        ReentrantLock[] locks = new ReentrantLock[64];
        java.util.Arrays.setAll(locks, ignored -> new ReentrantLock());
        return locks;
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private OperationResult executePrestige(UUID requestId, PrestigeAuthorizationResult authorization) {
        if (authorization.plan().isEmpty()) {
            return blocked(requestId, OperationKind.PRESTIGE, authorization.rejection().stream().toList());
        }
        PrestigePlan plan = authorization.plan().orElseThrow();
        requireRequestId(requestId, plan.requestId());
        var result = prestigeExecutor.execute(plan);
        return new OperationResult(plan.requestId(), operations.find(result.operationId())
                .map(ignored -> result.operationId()), OperationKind.PRESTIGE, prestigeStatus(result.status()),
                result.status() == PrestigeExecutionStatus.COMPLETED ? Optional.empty()
                        : Optional.of(error("prestige." + result.status().name().toLowerCase(Locale.ROOT),
                                result.detail())));
    }

    private Optional<PlayerPrestigeState> prestigeState(UUID playerId) {
        Optional<PlayerPrestigeState> durable = prestiges.find(playerId);
        if (durable.isPresent()) {
            return durable;
        }
        return activeRevision().map(revision -> new PlayerPrestigeState(playerId, 0, 0, 0, revision,
                AuthoritativeProgressContextFactory.initialPrestigeScope(playerId), Optional.empty(), clock.instant(),
                clock.instant()));
    }

    private void initializePlayerAfterPre(PrestigePlan plan) {
        requireInitializedAfterPre(plan.playerId());
    }

    private void requireInitializedAfterPre(UUID playerId) {
        initializePlayerLifecycle(playerId).ifPresent(failure -> {
            throw new IllegalStateException(failure);
        });
    }

    private boolean revalidatePrestige(PrestigePlan plan) {
        Optional<PlayerPrestigeState> durablePrestige = prestiges.find(plan.playerId());
        return operational() && activeRevision().filter(plan.configRevision()::equals).isPresent()
                && prestigeState(plan.playerId()).filter(state ->
                        state.stateRevision() == plan.expectedPrestigeRevision()
                        && state.currentPrestige() == plan.simulation().currentPrestigeBefore()
                        && state.lifetimePrestige() == plan.simulation().lifetimePrestigeBefore()
                        && state.configRevision().equals(plan.simulation().playerPrestigeProvenance())
                        && state.prestigeScope().equals(plan.simulation().prestigeScopeBefore())).isPresent();
    }

    private void publishStartup(StoredConfigurationRevision stored) {
        publish(stored, false, false);
    }

    private void configurationApplied(StoredConfigurationRevision applied) {
        publishedRevision.set(null);
        scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, () -> {
            publish(applied, true, false);
            return null;
        }).toCompletableFuture().join();
    }

    private void publish(
            StoredConfigurationRevision stored,
            boolean notify,
            boolean providerLifecycleRecomposition) {
        authoritativeRevision.set(stored);
        publishedRevision.set(null);
        try {
            PhaseFiveIntegrationCompilation integrations = integrationCompilation(stored.compiled().documents());
            if (integrations.validation().hasErrors()) {
                throw new IllegalStateException("Applied integration configuration failed runtime validation");
            }
            runtimeReconciler.reconcile(integrations.configuration());
            PhaseSixConfigurationCandidate candidate = hydrate(stored);
            latestValidation.set(candidate.validation());
            completion.refresh(providers, productionSchema(), candidate.stages());
            compositionFailure.set(Optional.empty());
            publishedRevision.set(stored.id());
        } catch (RuntimeException | LinkageError failure) {
            compositionFailure.set(Optional.of(safeMessage(failure)));
            if (providerLifecycleRecomposition || failure instanceof RuntimeCompositionException unavailable
                    && unavailable.providerLifecycleRelated()) {
                plugin.getLogger().warning("Canonical configuration remains fail-closed for revision "
                        + stored.id().value() + " while a required provider generation is unavailable");
            } else {
                plugin.getLogger().log(Level.SEVERE, "Canonical configuration is active but runtime composition "
                        + "failed closed for revision " + stored.id().value(), failure);
            }
        }
        if (notify) {
            plugin.getServer().getPluginManager().callEvent(new ConfigAppliedEvent(new ConfigAppliedSnapshot(
                    stored.id(), stored.parent(), stored.compiled().documents().keySet().stream().sorted().toList(),
                    stored.appliedAt().orElse(clock.instant()))));
        }
    }

    private void recomposeForProviderLifecycle() {
        if (!providerRecompositionQueued.compareAndSet(false, true)) {
            return;
        }
        scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD, () -> {
            providerRecompositionQueued.set(false);
            Optional.ofNullable(authoritativeRevision.get()).ifPresent(stored -> publish(stored, false, true));
            return null;
        });
    }

    private PhaseSixConfigurationCandidate hydrate(StoredConfigurationRevision stored) {
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), stored.parent(), stored.compiled().documents(),
                new Actor("SYSTEM", Optional.empty(), "Exact configuration runtime hydration"), clock.instant());
        PhaseSixConfigurationCandidate candidate = configuration.prepare(draft, Optional.empty())
                .toCompletableFuture().join();
        if (!candidate.compiled().contentHash().equals(stored.compiled().contentHash())) {
            throw new IllegalStateException("Runtime hydration changed the authoritative configuration content");
        }
        Set<String> acknowledgements = candidate.validation().findings().stream()
                .filter(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                .map(finding -> finding.code()).collect(Collectors.toUnmodifiableSet());
        if (!candidate.validation().canApply(acknowledgements)) {
            List<String> errors = candidate.validation().findings().stream()
                    .filter(finding -> finding.severity() == ValidationSeverity.ERROR)
                    .map(finding -> finding.code()).toList();
            throw new RuntimeCompositionException(providerLifecycleRelated(errors),
                    "Runtime hydration rejected the authoritative configuration: " + String.join(",", errors));
        }
        configuration.apply(stored.id(), candidate, acknowledgements, new BackupMetadata(
                "verified-history-" + stored.id().value(), stored.compiled().contentHash(), clock.instant(), true));
        return candidate;
    }

    private static PhaseFiveIntegrationCompilation integrationCompilation(Map<String, String> documents) {
        String source = documents.get("integrations.yml");
        if (source == null) {
            throw new IllegalArgumentException("Canonical configuration is missing integrations.yml");
        }
        return new PhaseFiveIntegrationCompiler().compile(source);
    }

    static SchemaRegistry productionSchema() {
        SchemaRegistry schema = PhaseSixSchema.create();
        PhaseFiveIntegrationSchema.extend(schema);
        return schema;
    }

    static boolean providerLifecycleRelated(List<String> errorCodes) {
        if (errorCodes.isEmpty()) {
            return false;
        }
        boolean directProviderFailure = errorCodes.stream().allMatch(code ->
                code.startsWith("phase3.provider.") || code.startsWith("phase4.provider.")
                        || code.startsWith("stage.rank_provider."));
        Set<String> providerDiscoveryFallout = Set.of(
                "requirement.value_type.required", "requirement.reference.unknown", "requirement.group.empty");
        boolean unavailableMetricDiscovery = errorCodes.contains("requirement.value_type.required")
                && errorCodes.contains("requirement.reference.unknown")
                && errorCodes.stream().allMatch(providerDiscoveryFallout::contains);
        return directProviderFailure || unavailableMetricDiscovery;
    }

    private static Actor player(UUID playerId) {
        return new Actor("PLAYER", Optional.of(playerId), playerId.toString());
    }

    private Optional<ConfigRevisionId> activeRevision() {
        return canonical.active().map(value -> value.revisionId());
    }

    private OperationResult unavailable(UUID requestId, OperationKind kind) {
        String code = activeRevision().isEmpty() ? "configuration.dormant" : "runtime.composition_unavailable";
        String detail = compositionFailure().orElse(activeRevision().isEmpty()
                ? "No canonical configuration is active" : "Runtime composition is not current");
        return new OperationResult(requestId, Optional.empty(), kind, OperationStatus.BLOCKED,
                Optional.of(error(code, detail)));
    }

    private OperationEvaluation unavailableEvaluation(OperationKind kind) {
        return new OperationEvaluation(kind, OperationEvaluationStatus.UNAVAILABLE, Optional.empty(),
                Optional.empty(), activeRevision(), List.of(error(activeRevision().isEmpty()
                        ? "configuration.dormant" : "runtime.composition_unavailable", "unavailable")), List.of(),
                Optional.empty(), clock.instant());
    }

    private static List<ServiceError> blockers(List<String> blockers) {
        return java.util.stream.IntStream.range(0, blockers.size()).mapToObj(index -> new ServiceError(
                "operation.blocked", "operation.blocked", Map.of("index", Integer.toString(index))))
                .toList();
    }

    private static List<RequirementProgressView> flatten(OperationKind operation, RequirementEvaluationResult root) {
        java.util.ArrayList<RequirementProgressView> result = new java.util.ArrayList<>();
        flatten(operation, root, result);
        return List.copyOf(result);
    }

    private static OperationSimulationView simulation(PrestigePlan plan) {
        return new OperationSimulationView(
                plan.costs().stream().map(value -> value.definition().id().value()).toList(),
                plan.rewards().stream().map(value -> value.definition().id().value()).toList(),
                plan.rankProjectionRequest().flatMap(ignored -> plan.rankProviderId()));
    }

    private static void flatten(
            OperationKind operation,
            RequirementEvaluationResult current,
            List<RequirementProgressView> result) {
        result.add(new RequirementProgressView(operation, current.id().value(), switch (current.status()) {
            case SATISFIED -> RequirementProgressStatus.SATISFIED;
            case UNSATISFIED -> RequirementProgressStatus.UNSATISFIED;
            case UNAVAILABLE -> RequirementProgressStatus.UNAVAILABLE;
            case INVALID -> RequirementProgressStatus.INVALID;
            case ERROR -> RequirementProgressStatus.ERROR;
        }, current.currentValue(), current.effectiveTarget().map(value -> value.lower())));
        current.children().forEach(child -> flatten(operation, child, result));
    }

    private static OperationResult blocked(UUID requestId, OperationKind kind, List<String> blockers) {
        String detail = blockers.isEmpty() ? "Operation is unavailable" : String.join("; ", blockers);
        return new OperationResult(requestId, Optional.empty(), kind, OperationStatus.BLOCKED,
                Optional.of(error("operation.blocked", detail)));
    }

    private static void requireRequestId(UUID ingress, UUID planned) {
        if (!ingress.equals(planned)) {
            throw new IllegalStateException("Operation authorization changed the ingress request identity");
        }
    }

    private static ServiceError error(String code, String detail) {
        String bounded = detail == null || detail.isBlank() ? "Operation failed safely" : detail;
        if (bounded.length() > 1024) {
            bounded = bounded.substring(0, 1024);
        }
        return new ServiceError(code, code, Map.of());
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return safeMessage(current);
    }

    private static final class RuntimeCompositionException extends IllegalStateException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        private final boolean providerLifecycleRelated;

        private RuntimeCompositionException(boolean providerLifecycleRelated, String message) {
            super(message);
            this.providerLifecycleRelated = providerLifecycleRelated;
        }

        private boolean providerLifecycleRelated() {
            return providerLifecycleRelated;
        }
    }

    private static OperationStatus prestigeStatus(PrestigeExecutionStatus status) {
        return switch (status) {
            case COMPLETED -> OperationStatus.COMPLETED;
            case DUPLICATE -> OperationStatus.CONFLICT;
            case UNAUTHORIZED, STALE_CONFIGURATION, STALE_GENERATION -> OperationStatus.BLOCKED;
            case NEEDS_RECONCILIATION -> OperationStatus.NEEDS_RECONCILIATION;
            case FAILED, COMPENSATED -> OperationStatus.FAILED;
        };
    }

    @Override
    public void close() {
        gui.close();
        confirmations.close();
        service.close();
        publishedRevision.set(null);
        authoritativeRevision.set(null);
        try {
            providerLifecycle.close();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Provider lifecycle recomposition cleanup failed safely",
                    exception);
        }
        placeholders.close();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }
}
