package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
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
import net.maddkraft.maddprestige.api.rank.RankAdapter;
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
import net.maddkraft.maddprestige.core.admin.diagnostic.RankTargetDiagnosticProbe;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.admin.ui.CanonicalGuiActionExecutor;
import net.maddkraft.maddprestige.core.admin.ui.CanonicalGuiMutationExecutor;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationService;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.plan.RankUpIntent;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
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
import net.maddkraft.maddprestige.core.rank.RankOperationExecutionStatus;
import net.maddkraft.maddprestige.core.rank.RankProjectionOperationPlanner;
import net.maddkraft.maddprestige.core.schema.PhaseSixSchema;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompilation;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationCompiler;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationSchema;
import net.maddkraft.maddprestige.persistence.admin.AtomicConfigurationFileStore;
import net.maddkraft.maddprestige.persistence.admin.SqliteConfigurationHistoryStore;
import net.maddkraft.maddprestige.persistence.admin.SqlitePrestigeAdministrationStore;
import net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore;
import net.maddkraft.maddprestige.persistence.plan.PrestigeOperationExecutor;
import net.maddkraft.maddprestige.persistence.plan.RankUpOperationExecutor;
import net.maddkraft.maddprestige.persistence.plan.RepositoryStageTransitionCommitter;
import net.maddkraft.maddprestige.persistence.rank.RankProjectionOperationExecutor;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteAuditRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteCurrencyLedgerStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteDatabaseValidator;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerInitializationStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePrestigeLifecycleRepository;
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
    private final SqlitePlayerStageRepository stages;
    private final SqlitePlayerPrestigeRepository prestiges;
    private final SqliteSeasonStore seasons;
    private final SqliteCurrencyLedgerStore currencyLedger;
    private final SqliteOperationRepository operations;
    private final SqlitePlayerInitializationStore playerInitialization;
    private final SqliteRequirementStateRepository requirementStates;
    private final SqliteStageReferenceMigrationStore transitions;
    private final RankUpAuthorizationService rankAuthorization;
    private final PrestigeAuthorizationService prestigeAuthorization;
    private final RankUpOperationExecutor rankExecutor;
    private final PrestigeOperationExecutor prestigeExecutor;
    private final RankProjectionOperationExecutor initialRankProjectionExecutor;
    private final RankProjectionOperationPlanner initialRankProjectionPlanner = new RankProjectionOperationPlanner();
    private final ReentrantLock[] playerInitializationLocks = initializationLocks();
    private final ExecutorService worker;
    private final PaperOperationLifecycle lifecycleEvents;
    private final PlaceholderSnapshotPublisher placeholders;
    private final ProductionMaddPrestigeService service;
    private final PhaseSixCommandService commands;
    private final CommandCompletionService completion;
    private final GuiSessionService gui;
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
        stages = new SqlitePlayerStageRepository(foundation);
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
        rankAuthorization = new RankUpAuthorizationService(
                () -> configuration.active().map(value -> value.priorPhases()), this::stageState,
                progressContexts::rankUp, requirementStates, providers, clock);
        prestigeAuthorization = new PrestigeAuthorizationService(configuration::active, this::stageState,
                this::prestigeState, progressContexts::prestige, requirementStates, providers, currencyLedger,
                prestigeLifecycle, seasons::active, clock);
        rankExecutor = new RankUpOperationExecutor(operations, providers, this::activeRevision,
                new RepositoryStageTransitionCommitter(stages, clock), transitions, worker, lifecycleEvents,
                this::revalidateRank, this::initializePlayerAfterPre);
        prestigeExecutor = new PrestigeOperationExecutor(prestigeLifecycle, operations, providers,
                this::activeRevision, transitions, clock, lifecycleEvents, this::revalidatePrestige,
                this::initializePlayerAfterPre);
        initialRankProjectionExecutor = new RankProjectionOperationExecutor(operations, stages,
                new SqliteAuditRepository(foundation), providers,
                () -> configuration.active().map(value -> value.priorPhases().stages()), transitions,
                worker, clock);
        placeholders = new PlaceholderSnapshotPublisher(plugin, placeholderCache, stages, prestiges,
                this::initializePlayerLifecycle);
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
                intent -> CompletableFuture.supplyAsync(() -> authorizeRank(intent), worker),
                intent -> CompletableFuture.supplyAsync(() -> authorizePrestige(intent), worker));
        OperationConfirmationService confirmations = new OperationConfirmationService(previews,
                rankExecutor::execute, plan -> CompletableFuture.supplyAsync(() -> prestigeExecutor.execute(plan),
                        worker), this::activeRevision, Duration.ofMinutes(2), clock);
        var databaseProbe = new DatabaseDiagnosticProbe(() -> CompletableFuture.supplyAsync(() -> {
            var validation = SqliteDatabaseValidator.validate(
                    foundation.databaseFile(), SqliteMigrations.phaseEightC());
            return new DatabaseHealth(true, validation.schemaVersion() == 11, "SQLite",
                    "validated schema " + validation.schemaVersion());
        }, worker));
        var operationalProbe = new PhaseSixOperationalDiagnosticProbe(providers,
                () -> CompletableFuture.supplyAsync(this::operationalDiagnosticSnapshot, worker));
        var rankProbe = new RankTargetDiagnosticProbe(() -> configuration.active()
                .map(value -> value.priorPhases().stages().configuration()), providers);
        var historyProbe = new ConfigurationHistoryDiagnosticProbe(history, worker);
        DoctorService doctor = new DoctorService(providers, canonical::active,
                List.of(databaseProbe, operationalProbe, rankProbe, historyProbe), clock);
        WhyService why = new WhyService(
                intent -> CompletableFuture.supplyAsync(() -> authorizeRank(intent), worker),
                intent -> CompletableFuture.supplyAsync(() -> authorizePrestige(intent), worker));
        PlayerProgressViewService playerViews = new PlayerProgressViewService(previews);
        ManualPrestigeAdministrationService manualPrestige = new ManualPrestigeAdministrationService(
                new SqlitePrestigeAdministrationStore(foundation, clock),
                () -> activeRevision().orElseThrow(), worker, this::initializePlayerLifecycle);
        CanonicalGuiMutationExecutor mutations = new CanonicalGuiMutationExecutor(administration);
        CanonicalGuiActionExecutor guiActions = new CanonicalGuiActionExecutor(playerViews, previews, confirmations,
                doctor, administration, mutations);
        gui = new GuiSessionService(this::activeRevision, guiActions, mutations, Duration.ofMinutes(5), clock);
        commands = new PhaseSixCommandService(new ContextualHelpService(schema), introspection, administration,
                doctor, why, previews, confirmations, playerViews, new SetupWizardService(administration, providers),
                manualPrestige, gui, this::activeRevision, worker);
        completion = new CommandCompletionService();

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
        configuration.unresolvedStageRemaps(1000).forEach(remap -> reconciliation.put(
                remap.operationId().toString(), remap.status()));

        Set<String> configuredStages = current.map(value -> value.priorPhases().stages().configuration()
                .stages().keySet().stream().map(stage -> stage.value()).collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
        DiagnosticSubsystemState placeholder = placeholderDiagnosticState();
        DiagnosticSubsystemState schedulerState = DiagnosticSubsystemState.healthy(
                "The bounded Paper scheduler and immutable presentation cache are composed.");
        DiagnosticSubsystemState flushState = DiagnosticSubsystemState.healthy(
                "Manual-progress drain health is exposed through the provider registry; no failed drain is active.");

        return new PhaseSixOperationalSnapshot(schemaVersion, 3, metrics, costs, rewards,
                Map.copyOf(pending), Map.copyOf(reconciliation),
                PhaseSixOperationalSnapshot.diagnoseLeases(transitions.leases(1000)),
                PhaseSixOperationalSnapshot.diagnoseTransitions(configuration.stageTransitions(1000)),
                currentPlayerStages(), configuredStages, List.of(), List.of(), placeholder, schedulerState,
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

    private Map<UUID, String> currentPlayerStages() {
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        String sql = "SELECT player_uuid, stage_id FROM mp_player_stage_state ORDER BY player_uuid LIMIT 10001";
        try (var connection = foundation.open(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                if (result.size() == 10_000) {
                    throw new IllegalStateException("Doctor player-stage scan exceeded its bounded 10,000-row limit");
                }
                result.put(UUID.fromString(rows.getString(1)), rows.getString(2));
            }
            return Map.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Doctor could not inspect player-stage integrity", exception);
        }
    }

    private CompletionStage<PlayerProgressSnapshot> progress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            requirePlayerLifecycle(playerId);
            var stage = stages.find(playerId);
            var prestige = prestiges.find(playerId);
            placeholders.refresh(playerId);
            return new PlayerProgressSnapshot(playerId, stage.map(PlayerStageState::stageId),
                    prestige.map(PlayerPrestigeState::currentPrestige).orElse(0L),
                    prestige.map(PlayerPrestigeState::lifetimePrestige).orElse(0L), activeRevision(), Map.of(),
                    clock.instant());
        }, worker);
    }

    private List<StageView> stageCatalog() {
        return configuration.active().map(active -> {
            var configured = active.priorPhases().stages().configuration();
            List<net.maddkraft.maddprestige.api.id.StageId> order = configured.order();
            return configured.stages().values().stream()
                    .sorted(java.util.Comparator.comparingInt(
                            (net.maddkraft.maddprestige.core.stage.StageDefinition value) -> {
                        int position = order.indexOf(value.id());
                        return position >= 0 ? position : order.size();
                    }).thenComparing(value -> value.id().value()))
                    .map(stage -> {
                        int position = order.indexOf(stage.id());
                        int ordinal = position >= 0 ? position : order.size();
                        return new StageView(stage.id(), stage.enabled(), ordinal,
                                stage.requirementTreeId().map(value -> value.value()));
                    }).toList();
        }).orElse(List.of());
    }

    private CompletionStage<OperationEvaluation> evaluateRankUp(UUID playerId) {
        if (!operational()) {
            return CompletableFuture.completedFuture(unavailableEvaluation(OperationKind.RANK_UP));
        }
        return CompletableFuture.supplyAsync(() -> authorizeRank(new RankUpIntent(player(playerId),
                playerId, Optional.empty(), "api-evaluate-rankup-" + UUID.randomUUID())), worker)
                .thenApply(value -> rankEvaluation(playerId, value));
    }

    private CompletionStage<OperationEvaluation> evaluatePrestige(UUID playerId) {
        if (!operational()) {
            return CompletableFuture.completedFuture(unavailableEvaluation(OperationKind.PRESTIGE));
        }
        return CompletableFuture.supplyAsync(() -> authorizePrestige(new PrestigeIntent(
                player(playerId), playerId, "api-evaluate-prestige-" + UUID.randomUUID())), worker)
                .thenApply(value -> prestigeEvaluation(playerId, value));
    }

    private OperationEvaluation rankEvaluation(UUID playerId, RankUpAuthorizationResult authorization) {
        if (authorization.plan().isEmpty()) {
            return new OperationEvaluation(OperationKind.RANK_UP, OperationEvaluationStatus.BLOCKED,
                    stageState(playerId).map(PlayerStageState::stageId), Optional.empty(),
                    activeRevision(), blockers(authorization.blockers()), List.of(), Optional.empty(),
                    clock.instant());
        }
        RankUpPlan plan = authorization.plan().orElseThrow();
        return new OperationEvaluation(OperationKind.RANK_UP,
                plan.executionAllowed() ? OperationEvaluationStatus.ELIGIBLE : OperationEvaluationStatus.BLOCKED,
                Optional.of(plan.sourceStage()), Optional.of(plan.targetStage()), Optional.of(plan.configRevision()),
                blockers(plan.blockers()), flatten(OperationKind.RANK_UP, plan.requirements()),
                Optional.of(simulation(plan)),
                clock.instant());
    }

    private OperationEvaluation prestigeEvaluation(UUID playerId, PrestigeAuthorizationResult authorization) {
        if (authorization.plan().isEmpty()) {
            return new OperationEvaluation(OperationKind.PRESTIGE, OperationEvaluationStatus.BLOCKED,
                    stageState(playerId).map(PlayerStageState::stageId), Optional.empty(), activeRevision(),
                    blockers(authorization.rejection().stream().toList()), List.of(), Optional.empty(),
                    clock.instant());
        }
        PrestigePlan plan = authorization.plan().orElseThrow();
        return new OperationEvaluation(OperationKind.PRESTIGE,
                plan.executionAllowed() ? OperationEvaluationStatus.ELIGIBLE : OperationEvaluationStatus.BLOCKED,
                Optional.of(plan.simulation().sourceStage()), Optional.of(plan.simulation().resetStage()),
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
        if (!operational()) {
            return CompletableFuture.completedFuture(unavailable(requestId, OperationKind.RANK_UP));
        }
        return CompletableFuture.supplyAsync(() -> authorizeRankOperation(new RankUpIntent(
                player(playerId), playerId, requestId, Optional.empty(), "api-rankup-" + requestId)), worker)
                .thenCompose(value -> executeRank(requestId, value))
                .whenComplete((result, failure) -> {
                    if (OperationPlaceholderRefreshPolicy.shouldRefresh(result, failure)) {
                        placeholders.refresh(playerId);
                    }
                });
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

    private RankUpAuthorizationResult authorizeRank(RankUpIntent intent) {
        Optional<String> initializationFailure = initializePlayerLifecycle(intent.playerId());
        if (initializationFailure.isPresent()) {
            return RankUpAuthorizationResult.rejected(initializationFailure.orElseThrow());
        }
        return authorizeRankRaw(intent);
    }

    private PrestigeAuthorizationResult authorizePrestige(PrestigeIntent intent) {
        Optional<String> initializationFailure = initializePlayerLifecycle(intent.playerId());
        if (initializationFailure.isPresent()) {
            return PrestigeAuthorizationResult.rejected(initializationFailure.orElseThrow());
        }
        return authorizePrestigeRaw(intent);
    }

    private RankUpAuthorizationResult authorizeRankOperation(RankUpIntent intent) {
        RankUpAuthorizationResult authorization = authorizeRankRaw(intent);
        if (authorization.plan().map(RankUpPlan::executionAllowed).orElse(false)) {
            return authorization;
        }
        Optional<String> initializationFailure = initializePlayerLifecycle(intent.playerId());
        return initializationFailure.isPresent()
                ? RankUpAuthorizationResult.rejected(initializationFailure.orElseThrow())
                : authorizeRankRaw(intent);
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

    private RankUpAuthorizationResult authorizeRankRaw(RankUpIntent intent) {
        return rankAuthorization.authorize(intent).toCompletableFuture().join();
    }

    private PrestigeAuthorizationResult authorizePrestigeRaw(PrestigeIntent intent) {
        return prestigeAuthorization.authorize(intent).toCompletableFuture().join();
    }

    private Optional<String> initializePlayerLifecycle(UUID playerId) {
        ReentrantLock lock = playerInitializationLocks[Math.floorMod(playerId.hashCode(),
                playerInitializationLocks.length)];
        lock.lock();
        try {
            ActivePhaseFourConfiguration active = configuration.active().orElse(null);
            if (active == null) {
                return Optional.of("No canonical configuration is active for player initialization");
            }
            var stageConfiguration = active.priorPhases().stages();
            var initialStage = initialStage(stageConfiguration.configuration());
            if (initialStage.isEmpty()) {
                return Optional.of("The canonical stage ladder has no initial stage for player initialization");
            }
            Optional<PlayerStageState> existingStage = stages.find(playerId);
            Optional<PlayerPrestigeState> existingPrestige = prestiges.find(playerId);
            if (existingStage.isPresent() != existingPrestige.isPresent()) {
                return Optional.of("Player lifecycle state is incomplete and requires operator recovery");
            }
            if (existingStage.isEmpty()) {
                Optional<String> initializationFailure = initializePlayerState(playerId, active,
                        initialStage.orElseThrow());
                if (initializationFailure.isPresent()) {
                    return initializationFailure;
                }
                existingStage = stages.find(playerId);
                existingPrestige = prestiges.find(playerId);
            }
            PlayerStageState stage = existingStage.orElseThrow();
            PlayerPrestigeState prestige = existingPrestige.orElseThrow();
            var priorProjection = operations.findByIdempotency("player-initial-rank-projection", playerId,
                    "player-initial-rank-projection:" + playerId);
            if (priorProjection.isPresent()) {
                return priorProjection.orElseThrow().state() == OperationState.COMPLETED
                        ? Optional.empty()
                        : Optional.of("Player initial rank projection requires recovery: "
                                + priorProjection.orElseThrow().state().name());
            }
            if (!initialProjectionPending(stage, prestige, initialStage.orElseThrow())) {
                return Optional.empty();
            }
            var target = stageConfiguration.configuration().stages().get(initialStage.orElseThrow());
            if (target == null) {
                return Optional.of("The canonical initial stage definition is unavailable");
            }
            if (target.projection().policy()
                    == net.maddkraft.maddprestige.core.rank.ProjectionPolicy.NONE) {
                return Optional.empty();
            }
            ProviderId providerId = target.projection().providerId().orElseThrow();
            Long generation = active.phaseFour().providerGenerations().get(providerId);
            providers.refreshHealth(providerId);
            var snapshot = providers.find(providerId);
            var adapter = providers.provider(providerId).filter(RankAdapter.class::isInstance)
                    .map(RankAdapter.class::cast);
            if (generation == null || snapshot.isEmpty() || adapter.isEmpty()
                    || snapshot.orElseThrow().generation() != generation
                    || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                    || !healthy(snapshot.orElseThrow().health().state())) {
                return Optional.of("Player initialization rank provider is unavailable: " + providerId.value());
            }
            var operation = initialRankProjectionPlanner.plan("player-initial-rank-projection",
                    new Actor("SYSTEM", Optional.empty(), "Initial managed-rank projection"), stage,
                    initialStage.orElseThrow(), stageConfiguration, providerId, generation,
                    "player-initial-rank-projection:" + playerId, false);
            var execution = initialRankProjectionExecutor.execute(operation, adapter.orElseThrow())
                    .toCompletableFuture().join();
            if (execution.status() != RankOperationExecutionStatus.COMPLETED) {
                return Optional.of("Player initial rank projection did not complete safely: "
                        + execution.status().name() + ": " + execution.detail());
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.of("Player lifecycle initialization failed: " + rootMessage(exception));
        } finally {
            lock.unlock();
        }
    }

    private Optional<String> initializePlayerState(
            UUID playerId,
            ActivePhaseFourConfiguration active,
            net.maddkraft.maddprestige.api.id.StageId initialStage) {
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
            playerInitialization.initialize(playerId, initialStage,
                    active.phaseFour().revisionId(), scope, clock.instant(), baselines);
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.of("Player lifecycle initialization failed: " + rootMessage(exception));
        }
    }

    private static boolean initialProjectionPending(
            PlayerStageState stage,
            PlayerPrestigeState prestige,
            net.maddkraft.maddprestige.api.id.StageId initialStage) {
        return stage.stageId().equals(initialStage)
                && stage.stateRevision() == 0
                && stage.lastProviderGeneration().isEmpty()
                && stage.lastReconciledAt().isEmpty()
                && prestige.currentPrestige() == 0
                && prestige.lifetimePrestige() == 0
                && prestige.stateRevision() == 0;
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

    private CompletionStage<OperationResult> executeRank(
            UUID requestId,
            RankUpAuthorizationResult authorization) {
        if (authorization.plan().isEmpty()) {
            return CompletableFuture.completedFuture(blocked(requestId, OperationKind.RANK_UP,
                    authorization.blockers()));
        }
        RankUpPlan plan = authorization.plan().orElseThrow();
        requireRequestId(requestId, plan.requestId());
        return rankExecutor.execute(plan).thenApply(result -> new OperationResult(
                plan.requestId(), operations.find(result.operationId()).map(ignored -> result.operationId()),
                OperationKind.RANK_UP, rankStatus(result.status()),
                result.status() == RankUpExecutionStatus.COMPLETED ? Optional.empty()
                        : Optional.of(error("rankup." + result.status().name().toLowerCase(Locale.ROOT),
                                result.detail()))));
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

    private Optional<PlayerStageState> stageState(UUID playerId) {
        Optional<PlayerStageState> durable = stages.find(playerId);
        if (durable.isPresent()) {
            return durable;
        }
        return configuration.active().flatMap(active -> initialStage(active.priorPhases().stages().configuration()))
                .map(stage -> new PlayerStageState(playerId, stage, 0, activeRevision().orElseThrow(),
                        clock.instant(), clock.instant(), clock.instant(), Optional.empty(), Optional.empty(),
                        Optional.empty()));
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

    private void initializePlayerAfterPre(RankUpPlan plan) {
        requireInitializedAfterPre(plan.playerId());
    }

    private void initializePlayerAfterPre(PrestigePlan plan) {
        requireInitializedAfterPre(plan.playerId());
    }

    private void requireInitializedAfterPre(UUID playerId) {
        initializePlayerLifecycle(playerId).ifPresent(failure -> {
            throw new IllegalStateException(failure);
        });
    }

    private boolean revalidateRank(RankUpPlan plan) {
        Optional<PlayerStageState> durableStage = stages.find(plan.playerId());
        Optional<PlayerPrestigeState> durablePrestige = prestiges.find(plan.playerId());
        if (durableStage.isPresent() != durablePrestige.isPresent()) {
            return false;
        }
        return operational() && activeRevision().filter(plan.configRevision()::equals).isPresent()
                && stageState(plan.playerId()).filter(state ->
                        state.stateRevision() == plan.expectedStateRevision()
                        && state.stageId().equals(plan.sourceStage())
                        && state.configRevision().equals(plan.expectedPlayerConfigRevision())).isPresent();
    }

    private boolean revalidatePrestige(PrestigePlan plan) {
        Optional<PlayerStageState> durableStage = stages.find(plan.playerId());
        Optional<PlayerPrestigeState> durablePrestige = prestiges.find(plan.playerId());
        if (durableStage.isPresent() != durablePrestige.isPresent()) {
            return false;
        }
        return operational() && activeRevision().filter(plan.configRevision()::equals).isPresent()
                && stageState(plan.playerId()).filter(state -> state.stateRevision() == plan.expectedStageRevision()
                        && state.stageId().equals(plan.simulation().sourceStage())
                        && state.configRevision().equals(plan.simulation().playerStageProvenance())).isPresent()
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

    private static Optional<net.maddkraft.maddprestige.api.id.StageId> initialStage(
            net.maddkraft.maddprestige.core.stage.StageConfiguration stages) {
        return stages.baselineStage().isPresent() ? stages.baselineStage() : stages.order().stream().findFirst();
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

    private static OperationSimulationView simulation(RankUpPlan plan) {
        return new OperationSimulationView(
                plan.costs().stream().map(value -> value.definition().id().value()).toList(),
                plan.rewards().stream().map(value -> value.definition().id().value()).toList(),
                plan.rankProjectionRequest().flatMap(ignored ->
                        plan.externalRankProjection().flatMap(value -> value.providerId())));
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

    private static OperationStatus rankStatus(RankUpExecutionStatus status) {
        return switch (status) {
            case COMPLETED -> OperationStatus.COMPLETED;
            case DUPLICATE -> OperationStatus.CONFLICT;
            case BLOCKED, STALE_GENERATION -> OperationStatus.BLOCKED;
            case NEEDS_RECONCILIATION -> OperationStatus.NEEDS_RECONCILIATION;
            case FAILED, COMPENSATED -> OperationStatus.FAILED;
        };
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
