package net.maddkraft.maddprestige.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.phase4.PrestigeConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PrestigeLimit;
import net.maddkraft.maddprestige.core.config.phase4.ResetPreservePolicy;
import net.maddkraft.maddprestige.core.config.phase4.ResetComponent;
import net.maddkraft.maddprestige.core.config.phase4.ResetDisposition;
import net.maddkraft.maddprestige.core.currency.CurrencyDefinition;
import net.maddkraft.maddprestige.core.currency.InternalCurrencyCostProvider;
import net.maddkraft.maddprestige.core.currency.InternalCurrencyRewardProvider;
import net.maddkraft.maddprestige.core.event.OperationLifecycleListener;
import net.maddkraft.maddprestige.core.milestone.MilestoneDefinition;
import net.maddkraft.maddprestige.core.milestone.MilestoneRepeatability;
import net.maddkraft.maddprestige.core.milestone.MilestoneTriggerType;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationService;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PrestigeIntent;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;
import net.maddkraft.maddprestige.core.prestige.PrestigeProgressContext;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.LatchKey;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationContext;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluator;
import net.maddkraft.maddprestige.core.requirement.RequirementLatch;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementStateReader;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;
import net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore;
import net.maddkraft.maddprestige.persistence.FileBackupService;
import net.maddkraft.maddprestige.persistence.plan.PrestigeOperationExecutor;
import net.maddkraft.maddprestige.persistence.recovery.PendingOperationRecoveryService;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteConfigRevisionRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteCurrencyAccountRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteCurrencyLedgerStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerInitializationStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePrestigeLifecycleRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteRecoveryEventRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteRequirementStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseFourLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("phase4-lifecycle");
    private static final StageId ORIGIN = new StageId("origin_stage");
    private static final StageId SUMMIT = new StageId("summit_stage");
    private static final CurrencyId CURRENCY = new CurrencyId("generic_credit");
    private static final MilestoneId MILESTONE = new MilestoneId("first_prestige");

    @Test
    @DisplayName("Canonical authorization is required even at the direct Prestige persistence boundary")
    void persistenceBoundaryRejectsTamperedPlanBeforeJournalInsertion() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            Fixture fixture = fixture(database, false, false, Optional.empty(), Map.of());
            PrestigePlan canonical = fixture.plan("persistence-seal");
            PrestigePlan tampered = new PrestigePlan(canonical.operationId(), canonical.playerId(),
                    canonical.expectedStageRevision(), canonical.expectedPrestigeRevision(),
                    canonical.configRevision(), Map.of(new ProviderId("fabricated_generation"), 99L),
                    canonical.simulation(), canonical.costs(), canonical.rewards(), canonical.rankProviderId(),
                    canonical.rankProjectionRequest(), canonical.unavailableProviders(), canonical.blockers(),
                    canonical.executionAllowed(), canonical.operationPlan(), canonical.authorization());

            assertThrows(SecurityException.class, () -> fixture.lifecycle.insertPrepared(tampered));
            assertTrue(fixture.operations.find(canonical.operationId()).isEmpty());
        }
    }

    @Test
    @DisplayName("[A58][A59] Restart recovery verifies committed internal state without duplicate effects")
    void recoversCommittedInternalTransactionExactlyOnce() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            Fixture fixture = fixture(database, true, true, Optional.empty(), Map.of());
            PrestigePlan plan = fixture.plan("recover-1");
            fixture.lifecycle.insertPrepared(plan);
            fixture.operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.EXECUTING);
            fixture.operations.transitionAction(plan.operationId(), "prestige-state-commit", ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());
            fixture.lifecycle.commitInternal(plan, NOW);

            PendingOperationRecoveryService restarted = new PendingOperationRecoveryService(
                    new SqliteOperationRepository(database.foundation()),
                    new SqlitePrestigeLifecycleRepository(database.foundation()),
                    new SqliteRecoveryEventRepository(database.foundation()), CLOCK);
            var outcome = restarted.recover(100).getFirst();

            assertEquals(OperationState.COMPLETED, outcome.state());
            assertEquals(ORIGIN, new SqlitePlayerStageRepository(database.foundation())
                    .find(fixture.playerId).orElseThrow().stageId());
            var prestige = new SqlitePlayerPrestigeRepository(database.foundation())
                    .find(fixture.playerId).orElseThrow();
            assertEquals(1, prestige.currentPrestige());
            assertEquals(1, prestige.lifetimePrestige());
            assertEquals(ExactDecimal.ZERO,
                    new SqliteCurrencyLedgerStore(database.foundation()).balance(fixture.playerId, CURRENCY));
            assertEquals(1, new SqliteCurrencyLedgerStore(database.foundation())
                    .history(fixture.playerId, CURRENCY, 10).size());
            assertTrue(fixture.lifecycle.awarded(fixture.playerId, MILESTONE, "once"));
            assertEquals(1, fixture.lifecycle.history(fixture.playerId, 10).size());
            assertEquals("Prestige reset", new SqlitePlayerStageRepository(database.foundation())
                    .history(fixture.playerId, 10).getFirst().reason());
            assertEquals(Optional.of(fixture.playerId), new SqlitePlayerStageRepository(database.foundation())
                    .history(fixture.playerId, 10).getFirst().actor().uuid());
            assertTrue(restarted.recover(100).isEmpty());
        }
    }

    @Test
    @DisplayName("[A29] Two stale concurrent confirmations cannot exceed a finite cap")
    void staleConcurrentConfirmationFailsCas() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            Fixture fixture = fixture(database, false, false, Optional.empty(), Map.of());
            PrestigePlan first = fixture.plan("concurrent-1");
            PrestigePlan second = fixture.plan("concurrent-2");
            PrestigeOperationExecutor executor = fixture.executor();

            assertEquals(PrestigeExecutionStatus.COMPLETED, executor.execute(first).status());
            assertEquals(PrestigeExecutionStatus.FAILED, executor.execute(second).status());
            assertEquals(1, fixture.prestigeStates.find(fixture.playerId).orElseThrow().currentPrestige());
            assertEquals(1, fixture.lifecycle.history(fixture.playerId, 10).size());
        }
    }

    @Test
    @DisplayName("[A60] Uncertain external reward is recorded and never blindly replayed on recovery")
    void externalUncertaintyIsRetainedWithoutReplay() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId rewardProviderId = new ProviderId("external_reward");
            FakeRewardProvider rewardProvider = new FakeRewardProvider(rewardProviderId);
            var registration = providers.register("maddprestige-testkit", rewardProvider);
            providers.activate(registration);
            RewardDefinition reward = new RewardDefinition(new RewardId("external_bonus"), rewardProviderId,
                    "generic", MetricValue.count(1), Map.of(), "External bonus", RewardFailurePolicy.REQUIRED,
                    RewardRepeatability.ONCE_PER_OPERATION);
            Fixture fixture = fixture(database, false, false, Optional.of(reward),
                    Map.of(rewardProviderId, registration.generation()), providers);
            PrestigePlan plan = fixture.plan("uncertain-1");
            rewardProvider.nextExecution(ActionExecutionResult.uncertain("provider response lost"));

            assertEquals(PrestigeExecutionStatus.NEEDS_RECONCILIATION,
                    fixture.executor().execute(plan).status());
            assertEquals(ActionState.UNCERTAIN, fixture.operations.findAction(plan.operationId(), "reward-0")
                    .orElseThrow().state());
            assertEquals(OperationState.NEEDS_RECONCILIATION,
                    fixture.operations.find(plan.operationId()).orElseThrow().state());
            assertEquals(1, rewardProvider.executionAttempts());
            assertEquals(OperationState.NEEDS_RECONCILIATION.name(),
                    fixture.lifecycle.history(fixture.playerId, 10).getFirst().result());

            PendingOperationRecoveryService recovery = new PendingOperationRecoveryService(fixture.operations,
                    fixture.lifecycle, new SqliteRecoveryEventRepository(database.foundation()), CLOCK);
            assertEquals("retained", recovery.recover(100).getFirst().decision());
            assertEquals(1, rewardProvider.executionAttempts());
            assertEquals(1, fixture.lifecycle.history(fixture.playerId, 10).size());
        }
    }

    @Test
    @DisplayName("[A59] Crash before native reward call recovers the exact sealed action once")
    void recoversPendingNativeRewardExactlyOnce() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId providerId = new ProviderId("native_reward");
            SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
            InternalCurrencyRewardProvider provider = new InternalCurrencyRewardProvider(providerId, "test",
                    PhaseFourLifecycleTest::currencyDefinitions, ledger, CLOCK);
            var registration = providers.register("test", provider);
            providers.activate(registration);
            RewardDefinition reward = internalReward(providerId);
            Fixture fixture = fixture(database, true, false, Optional.empty(), Optional.of(reward),
                    Map.of(providerId, registration.generation()), providers);
            PrestigePlan plan = fixture.plan("native-pending");
            commitWithoutRewardJournal(fixture, plan);

            PendingOperationRecoveryService recovery = new PendingOperationRecoveryService(fixture.operations,
                    fixture.lifecycle, new SqliteRecoveryEventRepository(database.foundation()), CLOCK, providers);
            assertEquals(OperationState.COMPLETED, recovery.recover(100).getFirst().state());
            assertEquals(ExactDecimal.parse("2"), ledger.balance(fixture.playerId, CURRENCY));
            assertEquals(2, ledger.history(fixture.playerId, CURRENCY, 10).size(),
                    "one reset ledger row plus one recovered native reward");
            assertEquals(ActionState.VERIFIED, fixture.operations.findAction(plan.operationId(), "reward-0")
                    .orElseThrow().state());
            assertTrue(recovery.recover(100).isEmpty());
        }
    }

    @Test
    @DisplayName("[A59] Applied native reward with unverified journal replays idempotently without double credit")
    void reconcilesStartedAppliedNativeRewardWithoutDoubleCredit() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId providerId = new ProviderId("native_reward_started");
            SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
            InternalCurrencyRewardProvider provider = new InternalCurrencyRewardProvider(providerId, "test",
                    PhaseFourLifecycleTest::currencyDefinitions, ledger, CLOCK);
            var registration = providers.register("test", provider);
            providers.activate(registration);
            Fixture fixture = fixture(database, true, false, Optional.empty(), Optional.of(internalReward(providerId)),
                    Map.of(providerId, registration.generation()), providers);
            PrestigePlan plan = fixture.plan("native-started");
            commitWithoutRewardJournal(fixture, plan);
            markInternalCommitted(fixture, plan);
            fixture.operations.transitionAction(plan.operationId(), "reward-0", ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());
            assertEquals(net.maddkraft.maddprestige.api.action.ActionExecutionStatus.APPLIED,
                    provider.execute(plan.rewards().getFirst()).toCompletableFuture().join().status());

            PendingOperationRecoveryService recovery = new PendingOperationRecoveryService(fixture.operations,
                    fixture.lifecycle, new SqliteRecoveryEventRepository(database.foundation()), CLOCK, providers);
            assertEquals(OperationState.COMPLETED, recovery.recover(100).getFirst().state());
            assertEquals(ExactDecimal.parse("2"), ledger.balance(fixture.playerId, CURRENCY));
            assertEquals(2, ledger.history(fixture.playerId, CURRENCY, 10).size());
        }
    }

    @Test
    @DisplayName("[A59][A60] Started external reward remains uncertain and is never replayed")
    void startedExternalRewardRemainsUncertain() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId providerId = new ProviderId("external_started");
            FakeRewardProvider provider = new FakeRewardProvider(providerId);
            var registration = providers.register("maddprestige-testkit", provider);
            providers.activate(registration);
            RewardDefinition reward = new RewardDefinition(new RewardId("external_started_reward"), providerId,
                    "generic", MetricValue.count(1), Map.of(), "External", RewardFailurePolicy.REQUIRED,
                    RewardRepeatability.ONCE_PER_OPERATION);
            Fixture fixture = fixture(database, false, false, Optional.empty(), Optional.of(reward),
                    Map.of(providerId, registration.generation()), providers);
            PrestigePlan plan = fixture.plan("external-started");
            commitWithoutRewardJournal(fixture, plan);
            markInternalCommitted(fixture, plan);
            fixture.operations.transitionAction(plan.operationId(), "reward-0", ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());

            PendingOperationRecoveryService recovery = new PendingOperationRecoveryService(fixture.operations,
                    fixture.lifecycle, new SqliteRecoveryEventRepository(database.foundation()), CLOCK, providers);
            assertEquals(OperationState.NEEDS_RECONCILIATION, recovery.recover(100).getFirst().state());
            assertEquals(ActionState.UNCERTAIN, fixture.operations.findAction(plan.operationId(), "reward-0")
                    .orElseThrow().state());
            assertEquals(0, provider.executionAttempts());
        }
    }

    @Test
    @DisplayName("[A59] Known applied native cost is exactly compensated when internal commit is absent")
    void recoversAppliedNativeCostByCompensation() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId providerId = new ProviderId("native_cost");
            SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
            InternalCurrencyCostProvider provider = new InternalCurrencyCostProvider(providerId, "test",
                    PhaseFourLifecycleTest::currencyDefinitions, ledger, CLOCK);
            var registration = providers.register("test", provider);
            providers.activate(registration);
            CostDefinition cost = new CostDefinition(new CostId("native_entry"), providerId,
                    InternalCurrencyCostProvider.TYPE, MetricValue.decimal("1"),
                    Map.of(InternalCurrencyCostProvider.CURRENCY_ID, CURRENCY.value()), "Native entry");
            Fixture fixture = fixture(database, true, false, Optional.of(cost), Optional.empty(),
                    Map.of(providerId, registration.generation()), providers);
            PrestigePlan plan = fixture.plan("native-cost-recovery");
            fixture.lifecycle.insertPrepared(plan);
            fixture.operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.EXECUTING);
            fixture.operations.transitionAction(plan.operationId(), "cost-0", ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());
            assertEquals(net.maddkraft.maddprestige.api.action.ActionExecutionStatus.APPLIED,
                    provider.execute(plan.costs().getFirst()).toCompletableFuture().join().status());
            fixture.operations.transitionAction(plan.operationId(), "cost-0", ActionState.STARTED,
                    ActionState.SUCCEEDED, Optional.empty());
            fixture.operations.transitionAction(plan.operationId(), "cost-0", ActionState.SUCCEEDED,
                    ActionState.VERIFIED, Optional.empty());

            PendingOperationRecoveryService recovery = new PendingOperationRecoveryService(fixture.operations,
                    fixture.lifecycle, new SqliteRecoveryEventRepository(database.foundation()), CLOCK, providers);
            assertEquals(OperationState.COMPENSATED, recovery.recover(100).getFirst().state());
            assertEquals(ExactDecimal.parse("5"), ledger.balance(fixture.playerId, CURRENCY));
            assertEquals(2, ledger.history(fixture.playerId, CURRENCY, 10).size());
            assertEquals(ActionState.COMPENSATED, fixture.operations.findAction(plan.operationId(), "cost-0")
                    .orElseThrow().state());
            assertEquals(ActionState.VERIFIED, fixture.operations.findAction(plan.operationId(), "compensate-cost-0")
                    .orElseThrow().state());
        }
    }

    @Test
    @DisplayName("[A59][A60] Mixed native and uncertain external costs never overclaim terminal compensation")
    void mixedPreCommitCostRecoveryRemainsReconcilableAcrossOrderingAndRestart() throws Exception {
        verifyMixedPreCommitCostRecovery(true);
        verifyMixedPreCommitCostRecovery(false);
    }

    @Test
    @DisplayName("[A16] Successful Prestige atomically establishes the next since-Prestige baseline")
    void prestigeBoundaryPreventsOldProgressFromLeaking() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            new SqliteConfigRevisionRepository(database.foundation()).insert(REVISION,
                    RevisionHasher.hashText("phase four Prestige boundary"));
            UUID playerId = UUID.randomUUID();
            ScopeId oldScope = new ScopeId("prestige-before");
            SqlitePlayerStageRepository stageStates = new SqlitePlayerStageRepository(database.foundation());
            SqlitePlayerPrestigeRepository prestigeStates = new SqlitePlayerPrestigeRepository(database.foundation());
            stageStates.insert(new PlayerStageState(playerId, SUMMIT, 0, REVISION, NOW.minusSeconds(60),
                    NOW.minusSeconds(600), NOW.minusSeconds(60), Optional.empty(), Optional.empty(), Optional.empty()));
            prestigeStates.insert(new PlayerPrestigeState(playerId, 0, 0, 0, REVISION, oldScope, Optional.empty(),
                    NOW.minusSeconds(600), NOW.minusSeconds(60)));

            ProviderRegistry providers = new ProviderRegistry();
            FakeProgressionProvider provider = new FakeProgressionProvider();
            var registration = providers.register("maddprestige-testkit", provider);
            providers.activate(registration);
            MetricId metricId = new MetricId("value");
            provider.set(playerId, metricId, ExactDecimal.parse("150"));
            RequirementId requirementId = new RequirementId("since_prestige_progress");
            RequirementDefinition requirement = RequirementDefinition.create(requirementId, provider.descriptor().id(),
                    metricId, MetricOperator.GREATER_OR_EQUAL,
                    RequirementTarget.single(MetricValue.decimal("50")), MeasurementScope.SINCE_PRESTIGE_START,
                    CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false);
            RequirementLeaf tree = new RequirementLeaf(requirement);
            SqliteRequirementStateRepository requirementStates = new SqliteRequirementStateRepository(
                    database.foundation());
            requirementStates.initializeBaseline(new RequirementBaseline(new BaselineKey(playerId, requirementId,
                    MeasurementScope.SINCE_PRESTIGE_START, oldScope, requirement.semanticFingerprint()),
                    MetricValue.decimal("100"), registration.generation(), NOW.minusSeconds(600)));

            StageDefinition origin = new StageDefinition(ORIGIN, true, "Origin", Map.of(), StageProjection.none());
            StageDefinition summit = new StageDefinition(SUMMIT, true, "Summit", Map.of(), StageProjection.none());
            StageConfiguration stageConfig = new StageConfiguration(2, true,
                    Map.of(ORIGIN, origin, SUMMIT, summit), List.of(ORIGIN, SUMMIT), Optional.of(ORIGIN),
                    ReconciliationPolicy.WARN_ONLY);
            PhaseThreeConfiguration phaseThree = new PhaseThreeConfiguration(3, 16,
                    Map.of(requirementId, requirement), Map.of(requirementId, tree), Map.of(), Map.of(),
                    CommandActionPolicy.safeDefaults());
            PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(SUMMIT), ORIGIN, 1, 1,
                    PrestigeLimit.unlimited(), Duration.ZERO, Optional.of(requirementId), List.of(), List.of(),
                    Optional.empty(), Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
            PhaseFourConfiguration phaseFour = new PhaseFourConfiguration(4, prestige, Map.of(), Map.of(), Map.of(),
                    Map.of(), CompetitionConfiguration.disabled());
            Map<ProviderId, Long> pins = Map.of(provider.descriptor().id(), registration.generation());
            ActiveStageConfiguration prior = new ActiveStageConfiguration(
                    new StageConfigurationSnapshot(REVISION, stageConfig),
                    new PhaseThreeConfigurationSnapshot(REVISION, phaseThree, pins));
            ActivePhaseFourConfiguration active = new ActivePhaseFourConfiguration(prior,
                    new PhaseFourConfigurationSnapshot(REVISION, phaseFour, pins));
            SqlitePrestigeLifecycleRepository lifecycle = new SqlitePrestigeLifecycleRepository(database.foundation());
            PrestigeAuthorizationService authorization = new PrestigeAuthorizationService(() -> Optional.of(active),
                    stageStates::find, prestigeStates::find,
                    (ignored, stage, state, configuration) -> new PrestigeProgressContext(playerId, REVISION,
                            state.currentPrestige(), ExactDecimal.ZERO, new ScopeContext(Map.of(
                                    MeasurementScope.SINCE_PRESTIGE_START, state.prestigeScope()))),
                    requirementStates, providers, new SqliteCurrencyLedgerStore(database.foundation()), lifecycle,
                    ActiveSeasonContext::none, CLOCK);

            PrestigePlan plan = authorization.authorize(new PrestigeIntent(
                    new Actor("player", Optional.of(playerId), "Player"), playerId, "boundary-1"))
                    .toCompletableFuture().join().plan().orElseThrow();
            assertEquals(MetricValue.decimal("150"), plan.simulation().baselineChanges().getFirst().value());
            PrestigeOperationExecutor executor = new PrestigeOperationExecutor(lifecycle,
                    new SqliteOperationRepository(database.foundation()), providers, () -> Optional.of(REVISION),
                    new InMemoryStageTransitionFence(), CLOCK);
            assertEquals(PrestigeExecutionStatus.COMPLETED, executor.execute(plan).status());

            ScopeId newScope = plan.simulation().prestigeScopeAfter();
            assertEquals(MetricValue.decimal("150"), requirementStates.findBaseline(new BaselineKey(playerId,
                    requirementId, MeasurementScope.SINCE_PRESTIGE_START, newScope,
                    requirement.semanticFingerprint())).orElseThrow().value());
            MetricSample after = MetricSample.available(MetricValue.decimal("160"), registration.generation(), NOW,
                    "test");
            RequirementEvaluationContext context = new RequirementEvaluationContext(playerId, REVISION, pins, 1,
                    ExactDecimal.ZERO, new ScopeContext(Map.of(MeasurementScope.SINCE_PRESTIGE_START, newScope)),
                    Map.of(requirementId, after), requirementStates);
            var descriptor = provider.requireMetric(metricId);
            assertFalse(new RequirementEvaluator(Map.of(new MetricBinding(provider.descriptor().id(), metricId),
                    descriptor)).evaluate(tree, context).satisfied(),
                    "Only 10 units earned after the new 150 baseline; the prior 100 baseline must not leak");
        }
    }

    @Test
    @DisplayName("[A27] PRESERVE keeps scoped requirement state and Prestige currency exactly unchanged")
    void preservePolicySimulationMatchesPersistedState() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            EnumMap<ResetComponent, ResetDisposition> dispositions = new EnumMap<>(
                    ResetPreservePolicy.safeDefaults().dispositions());
            dispositions.put(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS, ResetDisposition.PRESERVE);
            dispositions.put(ResetComponent.BASELINES, ResetDisposition.PRESERVE);
            dispositions.put(ResetComponent.LATCHED_COMPLETIONS, ResetDisposition.PRESERVE);
            dispositions.put(ResetComponent.PRESTIGE_SCOPED_CURRENCY, ResetDisposition.PRESERVE);
            ResetPreservePolicy policy = new ResetPreservePolicy(dispositions);
            Fixture fixture = fixture(database, true, false, Optional.empty(), Optional.empty(), Map.of(),
                    new ProviderRegistry(), policy, StageProjection.none());
            PrestigePlan plan = fixture.plan("preserve-scoped-state");

            assertEquals(plan.simulation().prestigeScopeBefore(), plan.simulation().prestigeScopeAfter());
            assertTrue(plan.simulation().baselineChanges().isEmpty());
            assertTrue(plan.simulation().scopedRequirementState().priorScopedStateRemainsEffective());
            assertTrue(plan.simulation().currencyChanges().isEmpty());
            assertEquals(PrestigeExecutionStatus.COMPLETED, fixture.executor().execute(plan).status());
            assertEquals(plan.simulation().prestigeScopeBefore(), fixture.prestigeStates.find(fixture.playerId)
                    .orElseThrow().prestigeScope());
            SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
            assertEquals(ExactDecimal.parse("5"), ledger.balance(fixture.playerId, CURRENCY));
            assertTrue(ledger.history(fixture.playerId, CURRENCY, 10).isEmpty());
        }
    }

    @Test
    @DisplayName("[A27] Every supported reset/preserve matrix outcome equals persisted state")
    void supportedResetPolicyMatrixMatchesPersistence() throws Exception {
        for (ResetDisposition scoped : ResetDisposition.values()) {
            for (ResetDisposition currency : ResetDisposition.values()) {
                try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
                    EnumMap<ResetComponent, ResetDisposition> dispositions = new EnumMap<>(
                            ResetPreservePolicy.safeDefaults().dispositions());
                    dispositions.put(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS, scoped);
                    dispositions.put(ResetComponent.BASELINES, scoped);
                    dispositions.put(ResetComponent.LATCHED_COMPLETIONS, scoped);
                    dispositions.put(ResetComponent.PRESTIGE_SCOPED_CURRENCY, currency);
                    ResetPreservePolicy policy = new ResetPreservePolicy(dispositions);
                    Fixture fixture = fixture(database, true, true, Optional.empty(), Optional.empty(), Map.of(),
                            new ProviderRegistry(), policy, StageProjection.none());
                    PrestigePlan plan = fixture.plan("matrix-" + scoped + "-" + currency);
                    Map<ResetComponent, ResetDisposition> simulated = plan.simulation().componentConsequences()
                            .stream().collect(java.util.stream.Collectors.toMap(
                                    value -> value.component(), value -> value.disposition()));

                    assertEquals(policy.dispositions(), simulated);
                    assertEquals(scoped == ResetDisposition.RESET,
                            plan.simulation().scopedRequirementState().newScopeEstablished());
                    assertEquals(PrestigeExecutionStatus.COMPLETED, fixture.executor().execute(plan).status());
                    assertEquals(ORIGIN, fixture.stageStates.find(fixture.playerId).orElseThrow().stageId());
                    assertEquals(plan.simulation().prestigeScopeAfter(), fixture.prestigeStates
                            .find(fixture.playerId).orElseThrow().prestigeScope());
                    assertEquals(1, fixture.prestigeStates.find(fixture.playerId).orElseThrow().lifetimePrestige());
                    SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
                    assertEquals(currency == ResetDisposition.RESET ? ExactDecimal.ZERO : ExactDecimal.parse("5"),
                            ledger.balance(fixture.playerId, CURRENCY));
                    assertEquals(currency == ResetDisposition.RESET ? 1 : 0,
                            ledger.history(fixture.playerId, CURRENCY, 10).size());
                    assertTrue(fixture.lifecycle.awarded(fixture.playerId, MILESTONE, "once"));
                    assertEquals(1, fixture.stageStates.history(fixture.playerId, 10).size());
                }
            }
        }
    }

    @Test
    @DisplayName("Post-cost active revision recheck blocks projection:none internal commit and compensates")
    void postCostConfigRecheckBlocksInternalCommit() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId providerId = new ProviderId("revision_race_cost");
            FakeCostProvider provider = new FakeCostProvider(providerId);
            var registration = providers.register("maddprestige-testkit", provider);
            providers.activate(registration);
            CostDefinition cost = genericCost(providerId);
            Fixture fixture = fixture(database, false, false, Optional.of(cost), Optional.empty(),
                    Map.of(providerId, registration.generation()), providers);
            provider.balance(fixture.playerId, "5");
            PrestigePlan plan = fixture.plan("revision-race-none");
            provider.onNextExecution(() -> fixture.activeRevision.set(new ConfigRevisionId("phase4-new-revision")));

            assertEquals(PrestigeExecutionStatus.COMPENSATED, fixture.executor().execute(plan).status());
            assertEquals(SUMMIT, fixture.stageStates.find(fixture.playerId).orElseThrow().stageId());
            assertEquals(0, fixture.prestigeStates.find(fixture.playerId).orElseThrow().currentPrestige());
            assertEquals(new java.math.BigDecimal("5"), provider.balance(fixture.playerId));
        }
    }

    @Test
    @DisplayName("Post-cost full recheck blocks stale projected reset before RankAdapter mutation")
    void postCostConfigRecheckRunsBeforeRankProjection() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId costId = new ProviderId("projection_race_cost");
            ProviderId rankId = new ProviderId("projection_race_rank");
            FakeCostProvider costProvider = new FakeCostProvider(costId);
            CountingRankAdapter rankProvider = new CountingRankAdapter(rankId);
            var costRegistration = providers.register("maddprestige-testkit", costProvider);
            var rankRegistration = providers.register("maddprestige-testkit", rankProvider);
            providers.activate(costRegistration);
            providers.activate(rankRegistration);
            Fixture fixture = fixture(database, false, false, Optional.of(genericCost(costId)), Optional.empty(),
                    Map.of(costId, costRegistration.generation(), rankId, rankRegistration.generation()), providers,
                    ResetPreservePolicy.safeDefaults(), StageProjection.group(rankId, "origin"));
            costProvider.balance(fixture.playerId, "5");
            PrestigePlan plan = fixture.plan("revision-race-projected");
            costProvider.onNextExecution(() -> fixture.activeRevision.set(new ConfigRevisionId("phase4-r2")));

            assertEquals(PrestigeExecutionStatus.COMPENSATED, fixture.executor().execute(plan).status());
            assertEquals(0, rankProvider.projectionAttempts.get());
            assertEquals(SUMMIT, fixture.stageStates.find(fixture.playerId).orElseThrow().stageId());
        }
    }

    @Test
    @DisplayName("Post-cost provider health recheck prevents every later authoritative mutation")
    void postCostProviderHealthRecheckBlocksCommit() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId providerId = new ProviderId("health_race_cost");
            FakeCostProvider provider = new FakeCostProvider(providerId);
            var registration = providers.register("maddprestige-testkit", provider);
            providers.activate(registration);
            Fixture fixture = fixture(database, false, false, Optional.of(genericCost(providerId)), Optional.empty(),
                    Map.of(providerId, registration.generation()), providers);
            provider.balance(fixture.playerId, "5");
            PrestigePlan plan = fixture.plan("health-race");
            provider.onNextExecution(() -> provider.healthSimulator().transition(ProviderHealthState.UNAVAILABLE,
                    "test.unhealthy", "health changed during cost"));

            assertEquals(PrestigeExecutionStatus.COMPENSATED, fixture.executor().execute(plan).status());
            assertEquals(0, fixture.prestigeStates.find(fixture.playerId).orElseThrow().currentPrestige());
        }
    }

    @Test
    @DisplayName("Prestige PRE cancellation creates no journal, cost, projection, or state mutation")
    void prestigePreCancellationHasZeroEffects() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            Fixture fixture = fixture(database, false, false, Optional.empty(), Map.of());
            PrestigePlan plan = fixture.plan("prestige-pre-cancel");
            OperationLifecycleListener events = new OperationLifecycleListener() {
                @Override
                public boolean beforePrestige(PrestigePlan ignored) {
                    return false;
                }
            };

            assertEquals(PrestigeExecutionStatus.UNAUTHORIZED, fixture.executor(events).execute(plan).status());
            assertTrue(fixture.operations.find(plan.operationId()).isEmpty());
            assertEquals(SUMMIT, fixture.stageStates.find(fixture.playerId).orElseThrow().stageId());
            assertEquals(0, fixture.prestigeStates.find(fixture.playerId).orElseThrow().currentPrestige());
        }
    }

    @Test
    @DisplayName("[OR8B-10] Prestige post-PRE revalidation precedes unknown-player materialization and effects")
    void prestigePostPreInvalidationLeavesUnknownPlayerUnmaterialized() throws Exception {
        try (DisposableSqliteFixture authorizationDatabase = DisposableSqliteFixture.create();
                DisposableSqliteFixture executionDatabase = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId costId = new ProviderId("pre_stale_prestige_cost");
            ProviderId rewardId = new ProviderId("pre_stale_prestige_reward");
            ProviderId rankId = new ProviderId("pre_stale_prestige_rank");
            FakeCostProvider cost = new FakeCostProvider(costId);
            FakeRewardProvider reward = new FakeRewardProvider(rewardId);
            CountingRankAdapter rank = new CountingRankAdapter(rankId);
            var costRegistration = providers.register("maddprestige-testkit", cost);
            var rewardRegistration = providers.register("maddprestige-testkit", reward);
            var rankRegistration = providers.register("maddprestige-testkit", rank);
            providers.activate(costRegistration);
            providers.activate(rewardRegistration);
            providers.activate(rankRegistration);
            RewardDefinition rewardDefinition = new RewardDefinition(new RewardId("pre_stale_reward"), rewardId,
                    "generic", MetricValue.count(1), Map.of(), "Pre stale reward", RewardFailurePolicy.REQUIRED,
                    RewardRepeatability.ONCE_PER_OPERATION);
            Fixture authorized = fixture(authorizationDatabase, false, false, Optional.of(genericCost(costId)),
                    Optional.of(rewardDefinition), Map.of(
                            costId, costRegistration.generation(),
                            rewardId, rewardRegistration.generation(),
                            rankId, rankRegistration.generation()), providers, ResetPreservePolicy.safeDefaults(),
                    StageProjection.group(rankId, "origin"));
            cost.balance(authorized.playerId, "5");
            PrestigePlan plan = authorized.plan("prestige-pre-config-stale");

            new SqliteConfigRevisionRepository(executionDatabase.foundation()).insert(REVISION,
                    RevisionHasher.hashText("phase four lifecycle"));
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(executionDatabase.foundation());
            SqlitePlayerPrestigeRepository prestiges = new SqlitePlayerPrestigeRepository(
                    executionDatabase.foundation());
            SqliteOperationRepository operations = new SqliteOperationRepository(executionDatabase.foundation());
            SqlitePrestigeLifecycleRepository lifecycle = new SqlitePrestigeLifecycleRepository(
                    executionDatabase.foundation());
            AtomicReference<ConfigRevisionId> active = new AtomicReference<>(REVISION);
            OperationLifecycleListener events = new OperationLifecycleListener() {
                @Override
                public boolean beforePrestige(PrestigePlan ignored) {
                    active.set(new ConfigRevisionId("phase4_after_pre"));
                    return true;
                }
            };
            PrestigeOperationExecutor executor = new PrestigeOperationExecutor(lifecycle, operations, providers,
                    () -> Optional.of(active.get()), new InMemoryStageTransitionFence(), CLOCK, events,
                    ignored -> true, ignored -> new SqlitePlayerInitializationStore(executionDatabase.foundation())
                            .initialize(plan.playerId(), plan.simulation().sourceStage(), REVISION,
                                    plan.simulation().prestigeScopeBefore(), NOW));

            assertEquals(PrestigeExecutionStatus.STALE_GENERATION, executor.execute(plan).status());
            assertTrue(operations.find(plan.operationId()).isEmpty());
            assertTrue(stages.find(plan.playerId()).isEmpty());
            assertTrue(prestiges.find(plan.playerId()).isEmpty());
            assertEquals(0L, rowCount(executionDatabase, "mp_operations"));
            assertEquals(0L, rowCount(executionDatabase, "mp_operation_actions"));
            assertEquals(0L, rowCount(executionDatabase, "mp_stage_transition_leases"));
            assertEquals(new java.math.BigDecimal("5"), cost.balance(plan.playerId()));
            assertEquals(0, reward.executionAttempts());
            assertEquals(0, rank.projectionAttempts.get());
        }
    }

    @Test
    @DisplayName("Prestige POST observes the durable terminal operation before execute returns")
    void prestigePostObservesDurableTerminalState() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            Fixture fixture = fixture(database, false, false, Optional.empty(), Map.of());
            PrestigePlan plan = fixture.plan("prestige-post-terminal");
            AtomicBoolean observed = new AtomicBoolean();
            OperationLifecycleListener events = new OperationLifecycleListener() {
                @Override
                public void afterPrestige(
                        PrestigePlan ignored,
                        net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult result) {
                    assertEquals(OperationState.COMPLETED,
                            fixture.operations.find(plan.operationId()).orElseThrow().state());
                    observed.set(true);
                }
            };

            assertEquals(PrestigeExecutionStatus.COMPLETED, fixture.executor(events).execute(plan).status());
            assertTrue(observed.get());
        }
    }

    @Test
    @DisplayName("[A69] Prestige sourced from a removed stage performs no cost, projection, commit, or reward")
    void configurationFenceRechecksPrestigeBeforeEveryEffect() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId costId = new ProviderId("fenced_prestige_cost");
            ProviderId rankId = new ProviderId("fenced_prestige_rank");
            ProviderId rewardId = new ProviderId("fenced_prestige_reward");
            FakeCostProvider cost = new FakeCostProvider(costId);
            CountingRankAdapter rank = new CountingRankAdapter(rankId);
            FakeRewardProvider reward = new FakeRewardProvider(rewardId);
            var costRegistration = providers.register("maddprestige-testkit", cost);
            var rankRegistration = providers.register("maddprestige-testkit", rank);
            var rewardRegistration = providers.register("maddprestige-testkit", reward);
            providers.activate(costRegistration);
            providers.activate(rankRegistration);
            providers.activate(rewardRegistration);
            RewardDefinition rewardDefinition = new RewardDefinition(new RewardId("fenced_reward"), rewardId,
                    "generic", MetricValue.count(1), Map.of(), "Fenced reward", RewardFailurePolicy.REQUIRED,
                    RewardRepeatability.ONCE_PER_OPERATION);
            Fixture fixture = fixture(database, false, false, Optional.of(genericCost(costId)),
                    Optional.of(rewardDefinition), Map.of(
                            costId, costRegistration.generation(),
                            rankId, rankRegistration.generation(),
                            rewardId, rewardRegistration.generation()),
                    providers, ResetPreservePolicy.safeDefaults(), StageProjection.group(rankId, "origin"));
            cost.balance(fixture.playerId, "5");
            PrestigePlan authorizedBeforeRemap = fixture.plan("fenced-prestige");
            ConfigRevisionId removalRevision = new ConfigRevisionId("prestige_removal_revision");
            var removalHash = RevisionHasher.hashText("remove prestige reset stage");
            new SqliteConfigRevisionRepository(database.foundation()).insert(removalRevision, removalHash);
            database.prepareConfigurationTransition(removalRevision, Optional.of(REVISION), removalHash, NOW);
            SqliteStageReferenceMigrationStore fence = new SqliteStageReferenceMigrationStore(database.foundation());
            var remap = fence.capture(Optional.of(new StageRemapPlan("remove_source_summit",
                    Map.of(SUMMIT, new StageId("replacement_summit"))))).remap().orElseThrow();
            fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                    Map.of(SUMMIT, ConfigurationStageReservationKind.REMOVED,
                            ORIGIN, ConfigurationStageReservationKind.DISABLED), Optional.of(remap),
                    new Actor("console", Optional.empty(), "Owner"), "pause before activation", NOW);

            assertEquals(PrestigeExecutionStatus.UNAUTHORIZED,
                    fixture.executor(fence).execute(authorizedBeforeRemap).status());
            assertEquals(OperationState.FAILED,
                    fixture.operations.find(authorizedBeforeRemap.operationId()).orElseThrow().state());
            assertEquals(new java.math.BigDecimal("5"), cost.balance(fixture.playerId));
            assertEquals(0, rank.projectionAttempts.get());
            assertEquals(0, reward.executionAttempts());
            assertEquals(new StageId("replacement_summit"),
                    fixture.stageStates.find(fixture.playerId).orElseThrow().stageId());
            assertEquals(0, fixture.prestigeStates.find(fixture.playerId).orElseThrow().currentPrestige());
        }
    }

    @Test
    @DisplayName("[A69] A Prestige source-stage lease wins before remap and releases after terminal completion")
    void prestigeSourceOperationWinsThenRemapRetriesSafely() throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId costId = new ProviderId("operation_wins_prestige_cost");
            FakeCostProvider cost = new FakeCostProvider(costId);
            var registration = providers.register("maddprestige-testkit", cost);
            providers.activate(registration);
            Fixture fixture = fixture(database, false, false, Optional.of(genericCost(costId)), Optional.empty(),
                    Map.of(costId, registration.generation()), providers);
            cost.balance(fixture.playerId, "5");
            PrestigePlan plan = fixture.plan("prestige-operation-wins");
            UUID waitingPlayer = UUID.randomUUID();
            fixture.stageStates.insert(new PlayerStageState(waitingPlayer, SUMMIT, 0, REVISION, NOW, NOW, NOW,
                    Optional.empty(), Optional.empty(), Optional.empty()));
            ConfigRevisionId removalRevision = new ConfigRevisionId("prestige_operation_wins_removal");
            var removalHash = RevisionHasher.hashText("prestige source removal after operation");
            new SqliteConfigRevisionRepository(database.foundation()).insert(removalRevision, removalHash);
            database.prepareConfigurationTransition(removalRevision, Optional.of(REVISION), removalHash, NOW);
            SqliteStageReferenceMigrationStore fence = new SqliteStageReferenceMigrationStore(database.foundation());
            StageRemapPlan remapPlan = new StageRemapPlan("remove_summit_after_prestige",
                    Map.of(SUMMIT, new StageId("replacement_summit")));
            var staleSnapshot = fence.capture(Optional.of(remapPlan)).remap().orElseThrow();
            CountDownLatch costStarted = new CountDownLatch(1);
            CountDownLatch allowCompletion = new CountDownLatch(1);
            cost.onNextExecution(() -> {
                costStarted.countDown();
                await(allowCompletion);
            });
            CompletableFuture<net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult> executing =
                    CompletableFuture.supplyAsync(() -> fixture.executor(fence).execute(plan));
            assertTrue(costStarted.await(10, TimeUnit.SECONDS));

            assertThrows(net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException.class,
                    () -> fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                            Map.of(ORIGIN, ConfigurationStageReservationKind.REMOVED), Optional.empty(),
                            new Actor("console", Optional.empty(), "Owner"),
                            "Prestige zero-reference reset target lease must win", NOW));
            assertThrows(net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException.class,
                    () -> fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                            Map.of(SUMMIT, ConfigurationStageReservationKind.REMOVED), Optional.of(staleSnapshot),
                            new Actor("console", Optional.empty(), "Owner"),
                            "Prestige source lease must win", NOW));
            assertEquals(SUMMIT, fixture.stageStates.find(waitingPlayer).orElseThrow().stageId());
            allowCompletion.countDown();
            assertEquals(PrestigeExecutionStatus.COMPLETED, executing.get(10, TimeUnit.SECONDS).status());
            assertEquals(ORIGIN, fixture.stageStates.find(fixture.playerId).orElseThrow().stageId());
            assertEquals(new java.math.BigDecimal("4"), cost.balance(fixture.playerId));
            assertTrue(fence.leases(10).isEmpty());

            var freshSnapshot = fence.capture(Optional.of(remapPlan)).remap().orElseThrow();
            fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                    Map.of(SUMMIT, ConfigurationStageReservationKind.REMOVED), Optional.of(freshSnapshot),
                    new Actor("console", Optional.empty(), "Owner"),
                    "retry after Prestige completion", NOW.plusSeconds(1));
            assertEquals(new StageId("replacement_summit"),
                    fixture.stageStates.find(waitingPlayer).orElseThrow().stageId());
        }
    }

    private static Fixture fixture(
            DisposableSqliteFixture database,
            boolean currency,
            boolean milestone,
            Optional<RewardDefinition> reward,
            Map<ProviderId, Long> pins) {
        return fixture(database, currency, milestone, reward, pins, new ProviderRegistry());
    }

    private static Fixture fixture(
            DisposableSqliteFixture database,
            boolean currency,
            boolean milestone,
            Optional<RewardDefinition> reward,
            Map<ProviderId, Long> pins,
            ProviderRegistry providers) {
        return fixture(database, currency, milestone, Optional.empty(), reward, pins, providers,
                ResetPreservePolicy.safeDefaults(), StageProjection.none());
    }

    private static Fixture fixture(
            DisposableSqliteFixture database,
            boolean currency,
            boolean milestone,
            Optional<CostDefinition> cost,
            Optional<RewardDefinition> reward,
            Map<ProviderId, Long> pins,
            ProviderRegistry providers) {
        return fixture(database, currency, milestone, cost, reward, pins, providers,
                ResetPreservePolicy.safeDefaults(), StageProjection.none());
    }

    private static Fixture fixture(
            DisposableSqliteFixture database,
            boolean currency,
            boolean milestone,
            Optional<CostDefinition> cost,
            Optional<RewardDefinition> reward,
            Map<ProviderId, Long> pins,
            ProviderRegistry providers,
            ResetPreservePolicy resetPolicy,
            StageProjection resetProjection) {
        return fixture(database, currency, milestone, cost.stream().toList(), reward, pins, providers, resetPolicy,
                resetProjection);
    }

    private static Fixture fixture(
            DisposableSqliteFixture database,
            boolean currency,
            boolean milestone,
            List<CostDefinition> costs,
            Optional<RewardDefinition> reward,
            Map<ProviderId, Long> pins,
            ProviderRegistry providers,
            ResetPreservePolicy resetPolicy,
            StageProjection resetProjection) {
        new SqliteConfigRevisionRepository(database.foundation()).insert(REVISION,
                RevisionHasher.hashText("phase four lifecycle"));
        UUID playerId = UUID.randomUUID();
        SqlitePlayerStageRepository stageStates = new SqlitePlayerStageRepository(database.foundation());
        SqlitePlayerPrestigeRepository prestigeStates = new SqlitePlayerPrestigeRepository(database.foundation());
        stageStates.insert(new PlayerStageState(playerId, SUMMIT, 0, REVISION, NOW.minusSeconds(60),
                NOW.minusSeconds(600), NOW.minusSeconds(60), Optional.empty(), Optional.empty(), Optional.empty()));
        prestigeStates.insert(new PlayerPrestigeState(playerId, 0, 0, 0, REVISION,
                new ScopeId("prestige-initial"), Optional.empty(), NOW.minusSeconds(600), NOW.minusSeconds(60)));
        if (currency) {
            new SqliteCurrencyAccountRepository(database.foundation()).set(playerId, CURRENCY,
                    ExactDecimal.parse("5"));
        }
        RewardId rewardId = reward.map(RewardDefinition::id).orElse(null);
        StageDefinition origin = new StageDefinition(ORIGIN, true, "Origin", Map.of(), resetProjection);
        StageDefinition summit = new StageDefinition(SUMMIT, true, "Summit", Map.of(), StageProjection.none());
        StageConfiguration stageConfig = new StageConfiguration(2, true, Map.of(ORIGIN, origin, SUMMIT, summit),
                List.of(ORIGIN, SUMMIT), Optional.of(ORIGIN), ReconciliationPolicy.WARN_ONLY);
        LinkedHashMap<CostId, CostDefinition> costDefinitions = new LinkedHashMap<>();
        costs.forEach(value -> costDefinitions.put(value.id(), value));
        PhaseThreeConfiguration phaseThree = new PhaseThreeConfiguration(3, 16, Map.of(), Map.of(),
                costDefinitions,
                reward.map(value -> Map.of(value.id(), value)).orElseGet(Map::of),
                CommandActionPolicy.safeDefaults());
        PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(SUMMIT), ORIGIN, 1, 1,
                PrestigeLimit.finite(1), Duration.ZERO, Optional.empty(),
                costs.stream().map(CostDefinition::id).toList(),
                rewardId == null ? List.of() : List.of(rewardId), Optional.empty(), Optional.empty(),
                resetPolicy, false);
        Map<CurrencyId, CurrencyDefinition> currencies = currency ? Map.of(CURRENCY,
                new CurrencyDefinition(CURRENCY, "Credits", Optional.of("¤"), 2, RoundingMode.UNNECESSARY, 12,
                        ExactDecimal.parse("9999999999.99"), true)) : Map.of();
        Map<MilestoneId, MilestoneDefinition> milestones = milestone ? Map.of(MILESTONE,
                new MilestoneDefinition(MILESTONE, "First", true, MilestoneTriggerType.CURRENT_PRESTIGE,
                        MetricValue.count(1), Optional.empty(), Optional.empty(), MilestoneRepeatability.ONCE,
                        List.of())) : Map.of();
        PhaseFourConfiguration phaseFour = new PhaseFourConfiguration(4, prestige, currencies, Map.of(), milestones,
                Map.of(), CompetitionConfiguration.disabled());
        ActiveStageConfiguration prior = new ActiveStageConfiguration(
                new StageConfigurationSnapshot(REVISION, stageConfig),
                new PhaseThreeConfigurationSnapshot(REVISION, phaseThree, pins));
        ActivePhaseFourConfiguration active = new ActivePhaseFourConfiguration(prior,
                new PhaseFourConfigurationSnapshot(REVISION, phaseFour, pins));
        RequirementStateReader requirementStates = emptyRequirementState();
        SqlitePrestigeLifecycleRepository lifecycle = new SqlitePrestigeLifecycleRepository(database.foundation());
        SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
        PrestigeAuthorizationService authorization = new PrestigeAuthorizationService(() -> Optional.of(active),
                stageStates::find, prestigeStates::find,
                (ignored, stage, state, configuration) -> new PrestigeProgressContext(playerId, REVISION,
                        state.currentPrestige(), ExactDecimal.ZERO, new ScopeContext(Map.of(
                                MeasurementScope.SINCE_PRESTIGE_START, state.prestigeScope()))),
                requirementStates, providers, ledger, lifecycle, ActiveSeasonContext::none, CLOCK);
        AtomicReference<ConfigRevisionId> activeRevision = new AtomicReference<>(REVISION);
        return new Fixture(playerId, stageStates, prestigeStates, lifecycle,
                new SqliteOperationRepository(database.foundation()), providers, authorization, activeRevision);
    }

    private static void verifyMixedPreCommitCostRecovery(boolean nativeFirst) throws Exception {
        try (DisposableSqliteFixture database = DisposableSqliteFixture.create()) {
            ProviderRegistry providers = new ProviderRegistry();
            ProviderId nativeId = new ProviderId("mixed_native_" + nativeFirst);
            ProviderId externalId = new ProviderId("mixed_external_" + nativeFirst);
            SqliteCurrencyLedgerStore ledger = new SqliteCurrencyLedgerStore(database.foundation());
            InternalCurrencyCostProvider nativeProvider = new InternalCurrencyCostProvider(nativeId, "test",
                    PhaseFourLifecycleTest::currencyDefinitions, ledger, CLOCK);
            FakeCostProvider externalProvider = new FakeCostProvider(externalId);
            var nativeRegistration = providers.register("test", nativeProvider);
            var externalRegistration = providers.register("maddprestige-testkit", externalProvider);
            providers.activate(nativeRegistration);
            providers.activate(externalRegistration);
            CostDefinition nativeCost = new CostDefinition(new CostId("mixed_native_cost"), nativeId,
                    InternalCurrencyCostProvider.TYPE, MetricValue.decimal("1"),
                    Map.of(InternalCurrencyCostProvider.CURRENCY_ID, CURRENCY.value()), "Native cost");
            CostDefinition externalCost = genericCost(externalId);
            List<CostDefinition> costs = nativeFirst
                    ? List.of(nativeCost, externalCost) : List.of(externalCost, nativeCost);
            Fixture fixture = fixture(database, true, false, costs, Optional.empty(),
                    Map.of(nativeId, nativeRegistration.generation(),
                            externalId, externalRegistration.generation()),
                    providers, ResetPreservePolicy.safeDefaults(), StageProjection.none());
            externalProvider.balance(fixture.playerId, "10");
            PrestigePlan plan = fixture.plan("mixed-recovery-" + nativeFirst);
            int nativeIndex = nativeFirst ? 0 : 1;
            int externalIndex = nativeFirst ? 1 : 0;
            String nativeAction = "cost-" + nativeIndex;
            String externalAction = "cost-" + externalIndex;
            fixture.lifecycle.insertPrepared(plan);
            fixture.operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.EXECUTING);
            fixture.operations.transitionAction(plan.operationId(), nativeAction, ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());
            assertEquals(net.maddkraft.maddprestige.api.action.ActionExecutionStatus.APPLIED,
                    nativeProvider.execute(plan.costs().get(nativeIndex)).toCompletableFuture().join().status());
            fixture.operations.transitionAction(plan.operationId(), nativeAction, ActionState.STARTED,
                    ActionState.SUCCEEDED, Optional.empty());
            fixture.operations.transitionAction(plan.operationId(), nativeAction, ActionState.SUCCEEDED,
                    ActionState.VERIFIED, Optional.empty());
            fixture.operations.transitionAction(plan.operationId(), externalAction, ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());

            PendingOperationRecoveryService recovery = new PendingOperationRecoveryService(fixture.operations,
                    fixture.lifecycle, new SqliteRecoveryEventRepository(database.foundation()), CLOCK, providers);
            var first = recovery.recover(100).getFirst();
            assertEquals(OperationState.NEEDS_RECONCILIATION, first.state());
            assertEquals("native-costs-compensated-external-cost-unresolved", first.decision());
            assertEquals(ExactDecimal.parse("5"), ledger.balance(fixture.playerId, CURRENCY));
            assertEquals(2, ledger.history(fixture.playerId, CURRENCY, 10).size());
            assertEquals(ActionState.COMPENSATED, fixture.operations.findAction(plan.operationId(), nativeAction)
                    .orElseThrow().state());
            assertEquals(ActionState.UNCERTAIN, fixture.operations.findAction(plan.operationId(), externalAction)
                    .orElseThrow().state());
            assertEquals(ActionState.VERIFIED,
                    fixture.operations.findAction(plan.operationId(), "compensate-" + nativeAction)
                            .orElseThrow().state());

            assertEquals("retained", recovery.recover(100).getFirst().decision());
            assertEquals(ExactDecimal.parse("5"), ledger.balance(fixture.playerId, CURRENCY));
            assertEquals(2, ledger.history(fixture.playerId, CURRENCY, 10).size(),
                    "restart recovery must not duplicate native compensation");
        }
    }

    private static RewardDefinition internalReward(ProviderId providerId) {
        return new RewardDefinition(new RewardId("native_bonus"), providerId, InternalCurrencyCostProvider.TYPE,
                MetricValue.decimal("2"), Map.of(InternalCurrencyCostProvider.CURRENCY_ID, CURRENCY.value()),
                "Native bonus", RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
    }

    private static CostDefinition genericCost(ProviderId providerId) {
        return new CostDefinition(new CostId("generic_cost"), providerId, "generic", MetricValue.decimal("1"),
                Map.of(), "Generic cost");
    }

    private static Map<CurrencyId, CurrencyDefinition> currencyDefinitions() {
        return Map.of(CURRENCY, new CurrencyDefinition(CURRENCY, "Credits", Optional.of("¤"), 2,
                RoundingMode.UNNECESSARY, 12, ExactDecimal.parse("9999999999.99"), true));
    }

    private static void commitWithoutRewardJournal(Fixture fixture, PrestigePlan plan) {
        fixture.lifecycle.insertPrepared(plan);
        fixture.operations.transition(plan.operationId(), OperationState.PREPARED, OperationState.EXECUTING);
        fixture.operations.transitionAction(plan.operationId(), "prestige-state-commit", ActionState.PENDING,
                ActionState.STARTED, Optional.empty());
        fixture.lifecycle.commitInternal(plan, NOW);
    }

    private static void markInternalCommitted(Fixture fixture, PrestigePlan plan) {
        fixture.operations.transitionAction(plan.operationId(), "prestige-state-commit", ActionState.STARTED,
                ActionState.SUCCEEDED, Optional.empty());
        fixture.operations.transitionAction(plan.operationId(), "prestige-state-commit", ActionState.SUCCEEDED,
                ActionState.VERIFIED, Optional.empty());
        plan.simulation().currencyChanges().forEach(currency -> {
            String actionId = "currency-reset-" + currency.currencyId().value();
            fixture.operations.transitionAction(plan.operationId(), actionId, ActionState.PENDING,
                    ActionState.STARTED, Optional.empty());
            fixture.operations.transitionAction(plan.operationId(), actionId, ActionState.STARTED,
                    ActionState.SUCCEEDED, Optional.empty());
            fixture.operations.transitionAction(plan.operationId(), actionId, ActionState.SUCCEEDED,
                    ActionState.VERIFIED, Optional.empty());
        });
        fixture.operations.transition(plan.operationId(), OperationState.EXECUTING, OperationState.STATE_COMMITTED);
    }

    private static RequirementStateReader emptyRequirementState() {
        return new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                return Optional.empty();
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                return Optional.empty();
            }
        };
    }

    private static long rowCount(DisposableSqliteFixture fixture, String table) {
        try (var connection = fixture.foundation().open();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.getLong(1);
        } catch (java.sql.SQLException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for deterministic Prestige race release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private record Fixture(
            UUID playerId,
            SqlitePlayerStageRepository stageStates,
            SqlitePlayerPrestigeRepository prestigeStates,
            SqlitePrestigeLifecycleRepository lifecycle,
            SqliteOperationRepository operations,
            ProviderRegistry providers,
            PrestigeAuthorizationService authorization,
            AtomicReference<ConfigRevisionId> activeRevision) {
        private PrestigePlan plan(String idempotency) {
            return authorization.authorize(new PrestigeIntent(new Actor("player", Optional.of(playerId), "Player"),
                    playerId, idempotency)).toCompletableFuture().join().plan().orElseThrow();
        }

        private PrestigeOperationExecutor executor() {
            return executor(new InMemoryStageTransitionFence());
        }

        private PrestigeOperationExecutor executor(OperationLifecycleListener events) {
            return new PrestigeOperationExecutor(lifecycle, operations, providers,
                    () -> Optional.of(activeRevision.get()), new InMemoryStageTransitionFence(), CLOCK, events,
                    ignored -> true);
        }

        private PrestigeOperationExecutor executor(
                net.maddkraft.maddprestige.core.stage.StageTransitionFence transitionFence) {
            return new PrestigeOperationExecutor(lifecycle, operations, providers,
                    () -> Optional.of(activeRevision.get()), transitionFence, CLOCK);
        }
    }

    private static final class CountingRankAdapter extends FakeProvider implements RankAdapter {
        private final AtomicInteger projectionAttempts = new AtomicInteger();

        private CountingRankAdapter(ProviderId id) {
            super(id, "rank", List.of(new CapabilityDescriptor("rank", "rank", "Test rank projection", Map.of())));
        }

        @Override
        public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            return CompletableFuture.completedFuture(Result.success(groupNames));
        }

        @Override
        public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
            return CompletableFuture.completedFuture(Result.success(new ManagedRankState(playerId, Set.of(),
                    List.of())));
        }

        @Override
        public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            projectionAttempts.incrementAndGet();
            ManagedRankState before = new ManagedRankState(request.playerId(), Set.of(), List.of());
            ManagedRankState after = new ManagedRankState(request.playerId(),
                    request.desiredGroup().map(Set::of).orElseGet(Set::of), List.of());
            return CompletableFuture.completedFuture(Result.success(new RankProjectionResult(before, after,
                    net.maddkraft.maddprestige.api.rank.RankProjectionOutcome.APPLIED)));
        }
    }
}
