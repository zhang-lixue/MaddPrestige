package net.maddkraft.maddprestige.persistence.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.audit.AuditOutcome;
import net.maddkraft.maddprestige.api.audit.AuditRecord;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionOutcome;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.rank.RankOperationExecutionStatus;
import net.maddkraft.maddprestige.core.rank.RankProjectionOperation;
import net.maddkraft.maddprestige.core.rank.RankProjectionOperationPlanner;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.rank.ReconciliationRateGate;
import net.maddkraft.maddprestige.core.rank.ReconciliationStatus;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.AuditRepository;
import net.maddkraft.maddprestige.persistence.migration.MigrationRunner;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteAuditRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteConfigRevisionRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankProjectionOperationExecutorTest {
    private static final ProviderId PROVIDER_ID = new ProviderId("rank_provider");
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    @TempDir
    Path temporaryDirectory;
    private SqliteOperationRepository operations;
    private SqlitePlayerStageRepository playerStages;
    private ProviderRegistry providers;
    private MutableRankAdapter adapter;
    private ProviderRegistration registration;
    private long generation;
    private AtomicReference<StageConfigurationSnapshot> active;
    private RankProjectionOperationExecutor executor;
    private AuditRepository audit;
    private List<AuditRecord> auditRecords;
    private Clock fixedClock;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        Path database = temporaryDirectory.resolve("rank-operation.db");
        SqliteFoundation sqlite = new SqliteFoundation(database);
        new MigrationRunner(sqlite,
                new FileBackupService(database, temporaryDirectory.resolve("backups"), Clock.systemUTC()),
                Clock.systemUTC()).migrate(SqliteMigrations.phaseTwo());
        ConfigRevisionId revision = new ConfigRevisionId("revision_1");
        new SqliteConfigRevisionRepository(sqlite).insert(revision, RevisionHasher.hashText("revision one"));
        operations = new SqliteOperationRepository(sqlite);
        playerStages = new SqlitePlayerStageRepository(sqlite);
        providers = new ProviderRegistry();
        adapter = new MutableRankAdapter();
        registration = providers.register("test-owner", adapter);
        providers.activate(registration);
        generation = registration.generation();
        active = new AtomicReference<>(new StageConfigurationSnapshot(revision,
                configuration(ReconciliationPolicy.MADD_PRESTIGE_AUTHORITATIVE)));
        SqliteAuditRepository persistedAudit = new SqliteAuditRepository(sqlite);
        auditRecords = new ArrayList<>();
        audit = record -> {
            auditRecords.add(record);
            persistedAudit.append(record);
        };
        fixedClock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
        executor = new RankProjectionOperationExecutor(operations, playerStages, audit,
                providers, () -> Optional.ofNullable(active.get()), Runnable::run,
                fixedClock);
        playerId = UUID.randomUUID();
        PlayerStageState state = playerState(revision);
        playerStages.insert(state);
        adapter.memberships.set(Set.of("first_group"));
    }

    @Test
    @DisplayName("[A06][A07] Pinned projection commits external membership and internal stage exactly once")
    void completesAndRejectsDuplicateIdempotency() {
        RankProjectionOperation operation = operation("click-1");
        var result = executor.execute(operation, adapter).toCompletableFuture().join();
        assertEquals(RankOperationExecutionStatus.COMPLETED, result.status());
        assertEquals(Set.of("second_group"), adapter.memberships.get());
        assertEquals(new StageId("second"), playerStages.find(playerId).orElseThrow().stageId());
        assertEquals(OperationState.COMPLETED, operations.find(operation.plan().id()).orElseThrow().state());
        assertEquals(ActionState.VERIFIED,
                operations.findAction(operation.plan().id(), "rank-projection").orElseThrow().state());

        RankProjectionOperation duplicate = operation("click-1");
        assertEquals(RankOperationExecutionStatus.DUPLICATE,
                executor.execute(duplicate, adapter).toCompletableFuture().join().status());
        assertEquals(1, adapter.projectionCalls.get());
    }

    @Test
    @DisplayName("[A07][A59][A60] Save uncertainty cannot become false success or internal stage commit")
    void uncertainExternalFailureNeedsReconciliation() {
        adapter.failSave = true;
        RankProjectionOperation operation = operation("click-save-failure");
        var result = executor.execute(operation, adapter).toCompletableFuture().join();
        assertEquals(RankOperationExecutionStatus.NEEDS_RECONCILIATION, result.status());
        assertEquals(new StageId("first"), playerStages.find(playerId).orElseThrow().stageId());
        assertEquals(OperationState.NEEDS_RECONCILIATION,
                operations.find(operation.plan().id()).orElseThrow().state());
        assertEquals(ActionState.UNCERTAIN,
                operations.findAction(operation.plan().id(), "rank-projection").orElseThrow().state());
    }

    @Test
    @DisplayName("[A60] Successful external save plus failed verification requires reconciliation")
    void postSaveVerificationUncertaintyDoesNotCommitInternalStage() {
        adapter.failPostSaveVerification = true;
        RankProjectionOperation operation = operation("click-post-save-verification");
        var result = executor.execute(operation, adapter).toCompletableFuture().join();
        assertEquals(1, adapter.successfulSaves.get());
        assertEquals(RankOperationExecutionStatus.NEEDS_RECONCILIATION, result.status());
        assertEquals(new StageId("first"), playerStages.find(playerId).orElseThrow().stageId());
        assertEquals(OperationState.NEEDS_RECONCILIATION,
                operations.find(operation.plan().id()).orElseThrow().state());
        assertEquals(ActionState.UNCERTAIN,
                operations.findAction(operation.plan().id(), "rank-projection").orElseThrow().state());
    }

    @Test
    @DisplayName("[A07] Mid-operation configuration generation change is explicit and reconciliation-required")
    void configChangeDuringProjectionUsesPinnedPlan() {
        adapter.afterProjection = () -> active.set(new StageConfigurationSnapshot(
                new ConfigRevisionId("revision_2"), configuration(ReconciliationPolicy.MADD_PRESTIGE_AUTHORITATIVE)));
        RankProjectionOperation operation = operation("click-config-race");
        var result = executor.execute(operation, adapter).toCompletableFuture().join();
        assertEquals(RankOperationExecutionStatus.NEEDS_RECONCILIATION, result.status());
        assertEquals(new StageId("first"), playerStages.find(playerId).orElseThrow().stageId());
        assertEquals(Set.of("second_group"), adapter.memberships.get());
    }

    @Test
    @DisplayName("[A07] Warn-only, authoritative repair, and repeated reconciliation follow policy exactly")
    void reconciliationPoliciesAreAppliedAndIdempotent() {
        Actor actor = new Actor("system", Optional.empty(), "Reconciler");
        active.set(new StageConfigurationSnapshot(active.get().revisionId(), configuration(ReconciliationPolicy.WARN_ONLY)));
        adapter.memberships.set(Set.of("second_group"));
        var warned = coordinator().reconcile(playerId, active.get(), adapter, generation, actor, "warn-1")
                .toCompletableFuture().join();
        assertEquals(ReconciliationStatus.WARNED, warned.status());
        assertEquals(0, adapter.projectionCalls.get());
        assertEquals(Set.of("second_group"), adapter.memberships.get());

        active.set(new StageConfigurationSnapshot(active.get().revisionId(),
                configuration(ReconciliationPolicy.MADD_PRESTIGE_AUTHORITATIVE)));
        var repaired = coordinator().reconcile(playerId, active.get(), adapter, generation, actor, "repair-1")
                .toCompletableFuture().join();
        assertEquals(ReconciliationStatus.REPAIRED, repaired.status());
        assertEquals(Set.of("first_group"), adapter.memberships.get());
        assertEquals(new StageId("first"), playerStages.find(playerId).orElseThrow().stageId());

        var repeated = coordinator().reconcile(playerId, active.get(), adapter, generation, actor, "repair-2")
                .toCompletableFuture().join();
        assertEquals(ReconciliationStatus.MATCHED, repeated.status());
        assertEquals(1, adapter.projectionCalls.get());
    }

    @Test
    @DisplayName("[A07] Import-once seeds one missing internal record and never overwrites it")
    void importOnceSeedsExactlyOneRecord() {
        UUID importedPlayer = UUID.randomUUID();
        active.set(new StageConfigurationSnapshot(active.get().revisionId(), configuration(ReconciliationPolicy.IMPORT_ONCE)));
        adapter.memberships.set(Set.of("second_group"));
        Actor actor = new Actor("console", Optional.empty(), "Console");
        var imported = coordinator().reconcile(importedPlayer, active.get(), adapter, generation, actor, "import-1")
                .toCompletableFuture().join();
        assertEquals(ReconciliationStatus.IMPORTED, imported.status());
        assertEquals(new StageId("second"), playerStages.find(importedPlayer).orElseThrow().stageId());

        var repeated = coordinator().reconcile(importedPlayer, active.get(), adapter, generation, actor, "import-2")
                .toCompletableFuture().join();
        assertEquals(ReconciliationStatus.MATCHED, repeated.status());
        assertEquals(0, adapter.projectionCalls.get());
    }

    @Test
    @DisplayName("[A07] Import-once rejects an active configuration change during external state read")
    void importOnceRejectsMidReadConfigurationChange() {
        UUID importedPlayer = UUID.randomUUID();
        active.set(new StageConfigurationSnapshot(active.get().revisionId(),
                configuration(ReconciliationPolicy.IMPORT_ONCE)));
        adapter.memberships.set(Set.of("second_group"));
        adapter.afterRead = () -> active.set(new StageConfigurationSnapshot(
                new ConfigRevisionId("revision_2"), configuration(ReconciliationPolicy.IMPORT_ONCE)));
        var result = coordinator().reconcile(importedPlayer, new StageConfigurationSnapshot(
                        new ConfigRevisionId("revision_1"), configuration(ReconciliationPolicy.IMPORT_ONCE)),
                adapter, generation, new Actor("console", Optional.empty(), "Console"), "import-race")
                .toCompletableFuture().join();
        assertEquals(ReconciliationStatus.STALE_GENERATION, result.status());
        assertTrue(playerStages.find(importedPlayer).isEmpty());
    }

    @Test
    @DisplayName("[A07] Import-once rechecks active configuration immediately before the worker insert")
    void importOnceRejectsConfigurationChangeBeforeInsert() {
        UUID importedPlayer = UUID.randomUUID();
        active.set(new StageConfigurationSnapshot(active.get().revisionId(),
                configuration(ReconciliationPolicy.IMPORT_ONCE)));
        adapter.memberships.set(Set.of("second_group"));
        ManualExecutor worker = new ManualExecutor();
        var pending = coordinator(worker).reconcile(importedPlayer, active.get(), adapter, generation,
                new Actor("console", Optional.empty(), "Console"), "import-worker-config-race")
                .toCompletableFuture();
        worker.runNext();
        active.set(new StageConfigurationSnapshot(
                new ConfigRevisionId("revision_2"), configuration(ReconciliationPolicy.IMPORT_ONCE)));
        worker.runNext();
        assertEquals(ReconciliationStatus.STALE_GENERATION, pending.join().status());
        assertTrue(playerStages.find(importedPlayer).isEmpty());
        assertRejectedImportAudited(importedPlayer);
    }

    @Test
    @DisplayName("[A07] Import-once rechecks provider generation immediately before the worker insert")
    void importOnceRejectsProviderChangeBeforeInsert() {
        UUID importedPlayer = UUID.randomUUID();
        active.set(new StageConfigurationSnapshot(active.get().revisionId(),
                configuration(ReconciliationPolicy.IMPORT_ONCE)));
        adapter.memberships.set(Set.of("second_group"));
        ManualExecutor worker = new ManualExecutor();
        var pending = coordinator(worker).reconcile(importedPlayer, active.get(), adapter, generation,
                new Actor("console", Optional.empty(), "Console"), "import-worker-provider-race")
                .toCompletableFuture();
        worker.runNext();
        providers.unregister(registration);
        registration = providers.register("test-owner", adapter);
        providers.activate(registration);
        worker.runNext();
        assertEquals(ReconciliationStatus.STALE_GENERATION, pending.join().status());
        assertTrue(playerStages.find(importedPlayer).isEmpty());
        assertRejectedImportAudited(importedPlayer);
    }

    private RankReconciliationCoordinator coordinator() {
        return coordinator(Runnable::run);
    }

    private RankReconciliationCoordinator coordinator(Executor workerExecutor) {
        return new RankReconciliationCoordinator(playerStages, audit, providers, executor,
                new ReconciliationRateGate(Duration.ofMillis(1), 100),
                () -> Optional.ofNullable(active.get()), workerExecutor, fixedClock);
    }

    private void assertRejectedImportAudited(UUID importedPlayer) {
        assertTrue(auditRecords.stream().anyMatch(record -> record.target().filter(importedPlayer::equals).isPresent()
                && record.outcome() == AuditOutcome.FAILED
                && record.reason().equals(ReconciliationStatus.STALE_GENERATION.name())));
    }

    private RankProjectionOperation operation(String idempotencyKey) {
        return new RankProjectionOperationPlanner().plan("rank-transition",
                new Actor("player", Optional.of(playerId), "Player"), playerStages.find(playerId).orElseThrow(),
                new StageId("second"), active.get(), PROVIDER_ID, generation, idempotencyKey, true);
    }

    private PlayerStageState playerState(ConfigRevisionId revision) {
        return new PlayerStageState(playerId, new StageId("first"), 0, revision, NOW, NOW, NOW,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static StageConfiguration configuration(ReconciliationPolicy policy) {
        StageId first = new StageId("first");
        StageId second = new StageId("second");
        return new StageConfiguration(2, true, Map.of(
                first, new StageDefinition(first, true, "First", Map.of(),
                        StageProjection.group(PROVIDER_ID, "first_group")),
                second, new StageDefinition(second, true, "Second", Map.of(),
                        StageProjection.group(PROVIDER_ID, "second_group"))),
                List.of(first, second), Optional.of(first), policy);
    }

    private static final class MutableRankAdapter implements RankAdapter {
        private final AtomicReference<Set<String>> memberships = new AtomicReference<>(Set.of());
        private final AtomicInteger projectionCalls = new AtomicInteger();
        private final AtomicInteger successfulSaves = new AtomicInteger();
        private boolean failSave;
        private boolean failPostSaveVerification;
        private Runnable afterRead = () -> { };
        private Runnable afterProjection = () -> { };

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(PROVIDER_ID, "test-owner", "1", "1", List.of(),
                    List.of(new CapabilityDescriptor("rank", "rank", "test", Map.of())));
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "available", NOW);
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            return CompletableFuture.completedFuture(Result.success(groupNames));
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<ManagedRankState>> readManagedState(
                UUID target, Set<String> managedGroups) {
            afterRead.run();
            return CompletableFuture.completedFuture(Result.success(new ManagedRankState(target,
                    memberships.get(), List.of())));
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            projectionCalls.incrementAndGet();
            ManagedRankState before = new ManagedRankState(request.playerId(), memberships.get(), List.of());
            Set<String> replacement = request.desiredGroup().map(Set::of).orElse(Set.of());
            memberships.set(replacement);
            afterProjection.run();
            if (failSave) {
                return CompletableFuture.completedFuture(Result.failure(new StructuredError(
                        "save.uncertain", ErrorCategory.UNCERTAIN, "Injected save uncertainty", Map.of())));
            }
            successfulSaves.incrementAndGet();
            if (failPostSaveVerification) {
                return CompletableFuture.completedFuture(Result.failure(new StructuredError(
                        "post.save.verification.uncertain", ErrorCategory.UNCERTAIN,
                        "Injected post-save verification uncertainty", Map.of())));
            }
            ManagedRankState after = new ManagedRankState(request.playerId(), replacement, List.of());
            return CompletableFuture.completedFuture(Result.success(
                    new RankProjectionResult(before, after, RankProjectionOutcome.APPLIED)));
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runNext() {
            Runnable task = tasks.pollFirst();
            if (task == null) {
                throw new AssertionError("Expected one queued worker task");
            }
            task.run();
        }
    }
}
