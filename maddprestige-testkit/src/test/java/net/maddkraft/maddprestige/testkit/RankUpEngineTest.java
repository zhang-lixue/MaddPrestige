package net.maddkraft.maddprestige.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.operation.ActionState;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionOutcome;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.ErrorCategory;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot;
import net.maddkraft.maddprestige.core.event.OperationLifecycleListener;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationService;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.plan.RankUpIntent;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.plan.RankUpPlanner;
import net.maddkraft.maddprestige.core.plan.RankUpPlanningRequest;
import net.maddkraft.maddprestige.core.plan.RankUpProgressContext;
import net.maddkraft.maddprestige.core.plan.RankUpProgressContextSource;
import net.maddkraft.maddprestige.core.plan.RankUpSimulationService;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ProjectionPolicy;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
import net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.LatchKey;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementBaseline;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementLatch;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementStateReader;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.requirement.ScopeContext;
import net.maddkraft.maddprestige.core.requirement.TargetRounding;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;
import net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore;
import net.maddkraft.maddprestige.persistence.plan.RankUpOperationExecutor;
import net.maddkraft.maddprestige.persistence.plan.RepositoryStageTransitionCommitter;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteConfigRevisionRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqliteOperationRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerInitializationStore;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankUpEngineTest {
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("revision_3");
    private static final ConfigRevisionId SOURCE_REVISION = new ConfigRevisionId("revision_1");
    private static final ProviderId COST_PROVIDER_ID = new ProviderId("cost_provider");
    private static final ProviderId REWARD_PROVIDER_ID = new ProviderId("reward_provider");
    private static final ProviderId RANK_PROVIDER_ID = new ProviderId("rank_provider");
    private static final StageId FIRST = new StageId("first");
    private static final StageId SECOND = new StageId("second");
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final RequirementStateReader EMPTY_REQUIREMENT_STATE = new RequirementStateReader() {
        @Override
        public Optional<net.maddkraft.maddprestige.core.requirement.RequirementBaseline> findBaseline(
                net.maddkraft.maddprestige.core.requirement.BaselineKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<net.maddkraft.maddprestige.core.requirement.RequirementLatch> findLatch(
                net.maddkraft.maddprestige.core.requirement.LatchKey key) {
            return Optional.empty();
        }
    };

    private ProviderRegistry providers;
    private FakeCostProvider costProvider;
    private FakeRewardProvider rewardProvider;
    private ProviderRegistration costRegistration;
    private ProviderRegistration rewardRegistration;
    private UUID player;

    @BeforeEach
    void providers() {
        providers = new ProviderRegistry();
        costProvider = new FakeCostProvider(COST_PROVIDER_ID);
        rewardProvider = new FakeRewardProvider(REWARD_PROVIDER_ID);
        costRegistration = providers.register("maddprestige-testkit", costProvider);
        rewardRegistration = providers.register("maddprestige-testkit", rewardProvider);
        providers.activate(costRegistration);
        providers.activate(rewardRegistration);
        player = UUID.randomUUID();
        costProvider.balance(player, "100");
    }

    @Test
    @DisplayName("[A09-A26] Canonical simulation resolves exact config and caller-composed authorization is rejected")
    void canonicalSimulationAndAuthorizationSeal() {
        List<CostDefinition> configuredCosts = List.of(cost("payment", "25"));
        List<RewardDefinition> configuredRewards = List.of(reward());
        AuthorizationFixture fixture = authorization(configuredCosts, configuredRewards, StageProjection.none());
        var simulated = new RankUpSimulationService(fixture.service()).simulate(intent(SECOND))
                .toCompletableFuture().join();
        RankUpPlan plan = simulated.plan().orElseThrow();
        assertTrue(plan.executionAllowed(), plan.blockers().toString());
        assertTrue(plan.authorization().matches(plan), "untouched executable plan must carry valid authority");
        assertEquals(configuredCosts, plan.costs().stream().map(value -> value.definition()).toList());
        assertEquals(configuredRewards, plan.rewards().stream().map(value -> value.definition()).toList());
        assertEquals(new BigDecimal("100"), costProvider.balance(player));
        assertEquals(0, rewardProvider.executionCount());

        RankUpPlanningRequest fabricated = new RankUpPlanningRequest(actor(), player, fixture.state(),
                fixture.target(), REVISION, fixture.snapshot().phaseThree().providerGenerations(),
                plan.requirements(), List.of(), List.of(reward("substitute")), "attacker");
        assertThrows(CompletionException.class,
                () -> new RankUpPlanner(providers).plan(fabricated).toCompletableFuture().join());
        for (RankUpPlanningRequest substituted : List.of(
                new RankUpPlanningRequest(actor(), player, fixture.state(), fixture.target(), REVISION,
                        fixture.snapshot().phaseThree().providerGenerations(), plan.requirements(), List.of(),
                        configuredRewards, "omitted-cost"),
                new RankUpPlanningRequest(actor(), player, fixture.state(), fixture.target(), REVISION,
                        fixture.snapshot().phaseThree().providerGenerations(), plan.requirements(),
                        List.of(cost("substitute", "1")), configuredRewards, "substituted-cost"),
                new RankUpPlanningRequest(actor(), player, fixture.state(), fixture.target(), REVISION,
                        fixture.snapshot().phaseThree().providerGenerations(), plan.requirements(), configuredCosts,
                        List.of(), "omitted-required-reward"),
                new RankUpPlanningRequest(actor(), player, fixture.state(), fixture.target(), REVISION,
                        fixture.snapshot().phaseThree().providerGenerations(), plan.requirements(), configuredCosts,
                        List.of(reward("substitute")), "substituted-reward"))) {
            assertThrows(CompletionException.class,
                    () -> new RankUpPlanner(providers).plan(substituted).toCompletableFuture().join());
        }
    }

    @Test
    @DisplayName("[8B] PRE cancellation happens before journal insertion and every consequential effect")
    void preEventCancellationHasZeroEffects() throws Exception {
        MutableRankAdapter rank = new MutableRankAdapter();
        providers.activate(providers.register("maddprestige-testkit", rank));
        RankUpPlan plan = plan(List.of(cost("pre-cancel", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                    () -> Optional.of(REVISION), new RepositoryStageTransitionCommitter(stages, CLOCK),
                    new InMemoryStageTransitionFence(), Runnable::run,
                    new OperationLifecycleListener() {
                        @Override
                        public boolean beforeRankUp(RankUpPlan ignored) {
                            return false;
                        }
                    }, ignored -> true, ignored -> new SqlitePlayerInitializationStore(fixture.foundation())
                            .initialize(player, FIRST, REVISION, new ScopeId("unknown_player_prestige"), NOW));

            assertEquals(RankUpExecutionStatus.BLOCKED,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertTrue(operations.find(plan.operationId()).isEmpty());
            assertTrue(stages.find(player).isEmpty());
            assertTrue(new SqlitePlayerPrestigeRepository(fixture.foundation()).find(player).isEmpty());
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, rewardProvider.executionCount());
            assertEquals(0, rank.projectionCalls.get());
        }
    }

    @Test
    @DisplayName("[OR8B-02] PRE listener failure leaves an unknown player and every effect store empty")
    void preEventFailureHasZeroEffectsForUnknownPlayer() throws Exception {
        MutableRankAdapter rank = new MutableRankAdapter();
        providers.activate(providers.register("maddprestige-testkit", rank));
        RankUpPlan plan = plan(List.of(cost("pre-failure", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                    () -> Optional.of(REVISION), new RepositoryStageTransitionCommitter(stages, CLOCK),
                    new InMemoryStageTransitionFence(), Runnable::run, new OperationLifecycleListener() {
                        @Override
                        public boolean beforeRankUp(RankUpPlan ignored) {
                            throw new IllegalStateException("fixture listener failure");
                        }
                    }, ignored -> true, ignored -> new SqlitePlayerInitializationStore(fixture.foundation())
                            .initialize(player, FIRST, REVISION, new ScopeId("unknown_player_prestige"), NOW));

            assertEquals(RankUpExecutionStatus.BLOCKED,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertTrue(operations.find(plan.operationId()).isEmpty());
            assertTrue(stages.find(player).isEmpty());
            assertTrue(new SqlitePlayerPrestigeRepository(fixture.foundation()).find(player).isEmpty());
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, rewardProvider.executionCount());
            assertEquals(0, rank.projectionCalls.get());
        }
    }

    @Test
    @DisplayName("[OR8B-10] PRE configuration invalidation precedes unknown-player materialization")
    void postPreConfigurationRevalidationLeavesUnknownPlayerUnmaterialized() throws Exception {
        MutableRankAdapter rank = new MutableRankAdapter();
        providers.activate(providers.register("maddprestige-testkit", rank));
        RankUpPlan plan = plan(List.of(cost("pre-config-stale", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        AtomicReference<ConfigRevisionId> active = new AtomicReference<>(REVISION);
        OperationLifecycleListener events = new OperationLifecycleListener() {
            @Override
            public boolean beforeRankUp(RankUpPlan ignored) {
                active.set(new ConfigRevisionId("revision_after_pre"));
                return true;
            }
        };

        assertPostPreStaleHasZeroEffects(plan, rank, events, () -> Optional.of(active.get()), ignored -> true);
    }

    @Test
    @DisplayName("[OR8B-10] PRE provider invalidation precedes unknown-player materialization")
    void postPreProviderRevalidationLeavesUnknownPlayerUnmaterialized() throws Exception {
        MutableRankAdapter rank = new MutableRankAdapter();
        ProviderRegistration registration = providers.register("maddprestige-testkit", rank);
        providers.activate(registration);
        RankUpPlan plan = plan(List.of(cost("pre-provider-stale", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        OperationLifecycleListener events = new OperationLifecycleListener() {
            @Override
            public boolean beforeRankUp(RankUpPlan ignored) {
                providers.unregister(registration);
                return true;
            }
        };

        assertPostPreStaleHasZeroEffects(plan, rank, events, () -> Optional.of(REVISION), ignored -> true);
    }

    @Test
    @DisplayName("[OR8B-10] PRE player-state invalidation precedes unknown-player materialization")
    void postPrePlayerStateRevalidationLeavesUnknownPlayerUnmaterialized() throws Exception {
        MutableRankAdapter rank = new MutableRankAdapter();
        providers.activate(providers.register("maddprestige-testkit", rank));
        RankUpPlan plan = plan(List.of(cost("pre-state-stale", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        AtomicBoolean exactVirtualState = new AtomicBoolean(true);
        OperationLifecycleListener events = new OperationLifecycleListener() {
            @Override
            public boolean beforeRankUp(RankUpPlan ignored) {
                exactVirtualState.set(false);
                return true;
            }
        };

        assertPostPreStaleHasZeroEffects(plan, rank, events, () -> Optional.of(REVISION),
                ignored -> exactVirtualState.get());
    }

    @Test
    @DisplayName("[8B] POST observes the durable terminal journal before the caller future completes")
    void postEventFollowsDurableTerminalCommit() throws Exception {
        RankUpPlan plan = plan(List.of(), List.of(), StageProjection.none());
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization(List.of(), List.of(), StageProjection.none()).state());
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            AtomicInteger postCalls = new AtomicInteger();
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                    () -> Optional.of(REVISION), new RepositoryStageTransitionCommitter(stages, CLOCK),
                    new InMemoryStageTransitionFence(), Runnable::run,
                    new OperationLifecycleListener() {
                        @Override
                        public void afterRankUp(
                                RankUpPlan ignored,
                                net.maddkraft.maddprestige.core.plan.RankUpExecutionResult result) {
                            assertEquals(OperationState.COMPLETED,
                                    operations.find(result.operationId()).orElseThrow().state());
                            postCalls.incrementAndGet();
                        }
                    });

            assertEquals(RankUpExecutionStatus.COMPLETED,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertEquals(1, postCalls.get());
        }
    }

    @Test
    @DisplayName("[A25] A requirement-blocked plan cannot reuse its authorization to escalate execution")
    void rejectsBlockedPlanEscalationBeforePersistenceOrMutation() throws Exception {
        FakeProgressionProvider progression = registerProgressionProvider(MetricValue.decimal("0"));
        RequirementLeaf requirement = liveRequirement(progression, "seal_blocked", "10");
        AuthorizationFixture authorization = authorizationForRequirement(requirement, defaultProgressContext(),
                EMPTY_REQUIREMENT_STATE);
        RankUpPlan blocked = authorizedPlan(authorization);
        assertFalse(blocked.executionAllowed());
        assertTrue(blocked.costs().isEmpty());
        assertTrue(blocked.rewards().isEmpty());
        assertTrue(blocked.rankProjectionRequest().isEmpty());

        RankUpPlan escalated = reconstruct(blocked, blocked.requirementEvaluation(),
                blocked.externalRankProjection(), List.of(), true);
        assertFalse(blocked.authorization().matches(escalated));

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization.state());
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            var result = executor(operations, new RepositoryStageTransitionCommitter(stages, CLOCK))
                    .execute(escalated).toCompletableFuture().join();

            assertEquals(RankUpExecutionStatus.BLOCKED, result.status());
            assertEquals(authorization.state(), stages.find(player).orElseThrow());
            assertTrue(operations.find(escalated.operationId()).isEmpty(),
                    "authorization rejection must precede journal insertion");
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, rewardProvider.executionCount());
        }
    }

    @Test
    @DisplayName("[A25] Canonical blocked plans carry denied, non-reusable execution authority")
    void blockedPlansAreIntrinsicallyNonAuthoritative() {
        FakeProgressionProvider progression = registerProgressionProvider(MetricValue.decimal("0"));
        RequirementLeaf requirement = liveRequirement(progression, "seal_denied", "10");
        RankUpPlan blocked = authorizedPlan(authorizationForRequirement(requirement, defaultProgressContext(),
                EMPTY_REQUIREMENT_STATE));
        RankUpPlan escalated = reconstruct(blocked, blocked.requirementEvaluation(),
                blocked.externalRankProjection(), List.of(), true);

        assertFalse(blocked.executionAllowed());
        assertFalse(blocked.authorization().matches(blocked),
                "a canonical blocked plan must not carry issued authority even for itself");
        assertFalse(blocked.authorization().matches(escalated),
                "denied authority must not authorize an executable reconstruction");
    }

    @Test
    @DisplayName("[A05][A25] External projection metadata cannot be changed before journal/provider mutation")
    void rejectsExternalProjectionTamperingAtAuthorizationBoundary() throws Exception {
        MutableRankAdapter adapter = registerRankAdapter();
        RankUpPlan original = plan(List.of(), List.of(), StageProjection.group(RANK_PROVIDER_ID, "new"));
        RankUpPlan tampered = reconstruct(original, original.requirementEvaluation(),
                Optional.of(StageProjection.none()), original.blockers(), original.executionAllowed());
        assertEquals(original.rankProjectionRequest(), tampered.rankProjectionRequest());
        assertFalse(original.authorization().matches(tampered));
        AtomicInteger commits = new AtomicInteger();

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            var result = executor(operations, ignored -> {
                commits.incrementAndGet();
                return CompletableFuture.completedFuture(ActionExecutionResult.applied());
            }).execute(tampered).toCompletableFuture().join();

            assertEquals(RankUpExecutionStatus.BLOCKED, result.status());
            assertTrue(operations.find(tampered.operationId()).isEmpty(),
                    "projection tampering must be rejected before journal insertion");
            assertEquals(0, adapter.projectionCalls.get());
            assertEquals(0, commits.get());
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, rewardProvider.executionCount());
        }
    }

    @Test
    @DisplayName("[A69] A preauthorized rank-up sourced from a removed stage is blocked before every effect")
    void configurationFenceRechecksPreviouslyAuthorizedPlanBeforeEffects() throws Exception {
        MutableRankAdapter adapter = registerRankAdapter();
        RankUpPlan authorizedBeforeRemap = plan(List.of(cost("fenced", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        AtomicInteger commits = new AtomicInteger();

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            ConfigRevisionId removalRevision = new ConfigRevisionId("removal_revision");
            var removalHash = RevisionHasher.hashText("remove second");
            new SqliteConfigRevisionRepository(fixture.foundation()).insert(removalRevision, removalHash);
            fixture.prepareConfigurationTransition(removalRevision, Optional.of(REVISION), removalHash, NOW);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization(List.of(), List.of(), StageProjection.none()).state());
            SqliteStageReferenceMigrationStore fence = new SqliteStageReferenceMigrationStore(fixture.foundation());
            var remap = fence.capture(Optional.of(new StageRemapPlan("remove_source_first",
                    Map.of(FIRST, new StageId("third"))))).remap().orElseThrow();
            fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                    Map.of(FIRST, ConfigurationStageReservationKind.REMOVED), Optional.of(remap), actor(),
                    "pause before configuration activation", NOW);
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                    () -> Optional.of(REVISION), ignored -> {
                        commits.incrementAndGet();
                        return CompletableFuture.completedFuture(ActionExecutionResult.applied());
                    }, fence, Runnable::run);

            var result = executor.execute(authorizedBeforeRemap).toCompletableFuture().join();

            assertEquals(RankUpExecutionStatus.BLOCKED, result.status());
            assertEquals(OperationState.FAILED,
                    operations.find(authorizedBeforeRemap.operationId()).orElseThrow().state(),
                    "journal-first ownership must fail terminally before consequential effects");
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, adapter.projectionCalls.get());
            assertEquals(0, commits.get());
            assertEquals(0, rewardProvider.executionCount());
            assertEquals(new StageId("third"), stages.find(player).orElseThrow().stageId());
        }
    }

    @Test
    @DisplayName("[A69] Zero-reference source and target reservations block rank-up before every effect")
    void zeroReferenceConfigurationAuthorityBlocksRankUpSourceAndTargetBeforeEffects() throws Exception {
        MutableRankAdapter adapter = registerRankAdapter();
        for (StageId reserved : List.of(FIRST, SECOND)) {
            RankUpPlan plan = plan(List.of(cost("zero-ref-" + reserved.value(), "25")), List.of(reward()),
                    StageProjection.group(RANK_PROVIDER_ID, "new"));
            AtomicInteger commits = new AtomicInteger();
            try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
                prepareRevision(fixture);
                ConfigRevisionId removalRevision = new ConfigRevisionId("rank_up_zero_ref_" + reserved.value());
                var removalHash = RevisionHasher.hashText("reserve " + reserved.value());
                new SqliteConfigRevisionRepository(fixture.foundation()).insert(removalRevision, removalHash);
                fixture.prepareConfigurationTransition(removalRevision, Optional.of(REVISION), removalHash, NOW);
                SqliteStageReferenceMigrationStore fence = new SqliteStageReferenceMigrationStore(
                        fixture.foundation());
                fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                        Map.of(reserved, ConfigurationStageReservationKind.REMOVED), Optional.empty(), actor(),
                        "zero-reference rank-up stage", NOW);
                SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
                RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                        () -> Optional.of(REVISION), ignored -> {
                            commits.incrementAndGet();
                            return CompletableFuture.completedFuture(ActionExecutionResult.applied());
                        }, fence, Runnable::run);

                var result = executor.execute(plan).toCompletableFuture().join();

                assertEquals(RankUpExecutionStatus.BLOCKED, result.status());
                assertEquals(OperationState.FAILED, operations.find(plan.operationId()).orElseThrow().state());
                assertEquals(new BigDecimal("100"), costProvider.balance(player));
                assertEquals(0, adapter.projectionCalls.get());
                assertEquals(0, commits.get());
                assertEquals(0, rewardProvider.executionCount());
            }
        }
    }

    @Test
    @DisplayName("[A69] A source-stage rank-up lease wins before remap and releases only after coherent completion")
    void sourceStageOperationWinsThenRemapRetriesAgainstFreshSnapshot() throws Exception {
        RankUpPlan plan = plan(List.of(cost("operation-wins", "25")), List.of(), StageProjection.none());
        CountDownLatch costStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        costProvider.onNextExecution(() -> {
            costStarted.countDown();
            await(allowCompletion);
        });

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            ConfigRevisionId removalRevision = new ConfigRevisionId("operation_wins_removal");
            var removalHash = RevisionHasher.hashText("operation wins before source removal");
            new SqliteConfigRevisionRepository(fixture.foundation()).insert(removalRevision, removalHash);
            fixture.prepareConfigurationTransition(removalRevision, Optional.of(REVISION), removalHash, NOW);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization(List.of(), List.of(), StageProjection.none()).state());
            UUID waitingPlayer = UUID.randomUUID();
            stages.insert(new PlayerStageState(waitingPlayer, FIRST, 0, REVISION, NOW, NOW, NOW,
                    Optional.empty(), Optional.empty(), Optional.empty()));
            SqliteStageReferenceMigrationStore fence = new SqliteStageReferenceMigrationStore(fixture.foundation());
            StageRemapPlan remapPlan = new StageRemapPlan("remove_first_after_operation",
                    Map.of(FIRST, new StageId("third")));
            var staleSnapshot = fence.capture(Optional.of(remapPlan)).remap().orElseThrow();
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                    () -> Optional.of(REVISION), new RepositoryStageTransitionCommitter(stages, CLOCK),
                    fence, Runnable::run);
            CompletableFuture<net.maddkraft.maddprestige.core.plan.RankUpExecutionResult> executing =
                    CompletableFuture.supplyAsync(() -> executor.execute(plan).toCompletableFuture().join());
            assertTrue(costStarted.await(10, TimeUnit.SECONDS));

            assertThrows(net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException.class,
                    () -> fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                            Map.of(FIRST, ConfigurationStageReservationKind.REMOVED), Optional.of(staleSnapshot),
                            actor(), "operation source lease must win", NOW));
            assertEquals(FIRST, stages.find(waitingPlayer).orElseThrow().stageId());
            allowCompletion.countDown();
            assertEquals(RankUpExecutionStatus.COMPLETED, executing.get(10, TimeUnit.SECONDS).status());
            assertEquals(SECOND, stages.find(player).orElseThrow().stageId());
            assertEquals(new BigDecimal("75"), costProvider.balance(player));
            assertTrue(fence.leases(10).isEmpty());

            var freshSnapshot = fence.capture(Optional.of(remapPlan)).remap().orElseThrow();
            fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                    Map.of(FIRST, ConfigurationStageReservationKind.REMOVED), Optional.of(freshSnapshot), actor(),
                    "retry after operation completion", NOW.plusSeconds(1));
            assertEquals(new StageId("third"), stages.find(waitingPlayer).orElseThrow().stageId());
        }
    }

    @Test
    @DisplayName("[A69] Rank-up target lease wins before zero-reference removal authority")
    void zeroReferenceTargetOperationWinsBeforeConfigurationReservation() throws Exception {
        RankUpPlan plan = plan(List.of(cost("target-operation-wins", "25")), List.of(), StageProjection.none());
        CountDownLatch costStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        costProvider.onNextExecution(() -> {
            costStarted.countDown();
            await(allowCompletion);
        });
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            ConfigRevisionId removalRevision = new ConfigRevisionId("rank_up_target_operation_wins");
            var removalHash = RevisionHasher.hashText("remove zero-reference rank-up target");
            new SqliteConfigRevisionRepository(fixture.foundation()).insert(removalRevision, removalHash);
            fixture.prepareConfigurationTransition(removalRevision, Optional.of(REVISION), removalHash, NOW);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization(List.of(), List.of(), StageProjection.none()).state());
            SqliteStageReferenceMigrationStore fence = new SqliteStageReferenceMigrationStore(fixture.foundation());
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers,
                    () -> Optional.of(REVISION), new RepositoryStageTransitionCommitter(stages, CLOCK),
                    fence, Runnable::run);
            CompletableFuture<net.maddkraft.maddprestige.core.plan.RankUpExecutionResult> executing =
                    CompletableFuture.supplyAsync(() -> executor.execute(plan).toCompletableFuture().join());
            assertTrue(costStarted.await(10, TimeUnit.SECONDS));

            assertThrows(net.maddkraft.maddprestige.core.stage.StageTransitionBlockedException.class,
                    () -> fence.beginTransition(removalRevision, Optional.of(REVISION), removalHash,
                            Map.of(SECOND, ConfigurationStageReservationKind.REMOVED), Optional.empty(), actor(),
                            "rank-up target operation owns transition", NOW));

            allowCompletion.countDown();
            assertEquals(RankUpExecutionStatus.COMPLETED, executing.get(10, TimeUnit.SECONDS).status());
            assertEquals(SECOND, stages.find(player).orElseThrow().stageId());
            assertEquals(new BigDecimal("75"), costProvider.balance(player));
            assertTrue(fence.leases(10).isEmpty());
        }
    }

    @Test
    @DisplayName("[A09-A25] Complete requirement outcome is sealed, not only its provenance")
    void rejectsRequirementOutcomeSubstitutionAtAuthorizationBoundary() throws Exception {
        FakeProgressionProvider progression = registerProgressionProvider(MetricValue.decimal("10"));
        RequirementLeaf requirement = liveRequirement(progression, "seal_outcome", "10");
        AuthorizationFixture authorization = authorizationForRequirement(requirement, defaultProgressContext(),
                EMPTY_REQUIREMENT_STATE);
        RankUpPlan executable = authorizedPlan(authorization);
        progression.set(player, new MetricId("value"), MetricValue.decimal("0"));
        RankUpPlan laterBlocked = authorizedPlan(authorization);
        assertEquals(executable.requirementEvaluation().binding(), laterBlocked.requirementEvaluation().binding(),
                "the regression requires equal provenance and a different evaluated outcome");
        assertFalse(laterBlocked.executionAllowed());

        RankUpPlan tampered = reconstruct(executable, laterBlocked.requirementEvaluation(),
                executable.externalRankProjection(), executable.blockers(), executable.executionAllowed());
        assertFalse(executable.authorization().matches(tampered));
        AtomicInteger commits = new AtomicInteger();

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            var result = executor(operations, ignored -> {
                commits.incrementAndGet();
                return CompletableFuture.completedFuture(ActionExecutionResult.applied());
            }).execute(tampered).toCompletableFuture().join();

            assertEquals(RankUpExecutionStatus.BLOCKED, result.status());
            assertTrue(operations.find(tampered.operationId()).isEmpty());
            assertEquals(0, commits.get());
        }
    }

    @Test
    @DisplayName("[A09-A26] Public rank-up intent cannot carry requirement or lifecycle authority")
    void rankUpIntentIsStrictlyIntentOnly() {
        assertEquals(List.of("actor", "playerId", "requestId", "intendedTarget", "idempotencyKey"),
                Arrays.stream(RankUpIntent.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(5, RankUpIntent.class.getRecordComponents().length);
        assertFalse(Arrays.stream(RankUpIntent.class.getRecordComponents()).anyMatch(component ->
                component.getType().equals(RequirementStateReader.class)
                        || component.getType().equals(ScopeContext.class)
                        || component.getType().equals(ExactDecimal.class)
                        || component.getType().equals(long.class)));
    }

    @Test
    @DisplayName("[A09-A26] Trusted context owns scope, scaling, catch-up, and simulation inputs")
    void trustedProgressContextGovernsAuthorizationAndSimulation() {
        registerProgressionProvider(MetricValue.decimal("10"));
        RequirementDefinition definition = RequirementDefinition.create(new RequirementId("trusted_context"),
                new ProviderId("fake_progression"), new MetricId("value"), MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.decimal("10")), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.linear("1", TargetRounding.EXACT),
                new CatchUpProfile(true, ExactDecimal.ZERO, ExactDecimal.parse("0.5"),
                        ExactDecimal.parse("0.5"), Optional.empty(), TargetRounding.EXACT,
                        ExactDecimal.parse("1")), Map.of(), Map.of(), false);
        RequirementLeaf requirement = new RequirementLeaf(definition);
        ScopeId trustedScope = new ScopeId("trusted_absolute");
        AtomicInteger contextLoads = new AtomicInteger();
        RankUpProgressContextSource trusted = (requestedPlayer, state, active) -> {
            contextLoads.incrementAndGet();
            return new RankUpProgressContext(requestedPlayer, active.stages().revisionId(), 1,
                    ExactDecimal.parse("1"), new ScopeContext(Map.of(MeasurementScope.ABSOLUTE, trustedScope)));
        };
        AuthorizationFixture fixture = authorizationForRequirement(requirement, trusted, EMPTY_REQUIREMENT_STATE);

        RankUpPlan simulated = new RankUpSimulationService(fixture.service()).simulate(intent(SECOND))
                .toCompletableFuture().join().plan().orElseThrow();
        assertTrue(simulated.executionAllowed(), simulated.blockers().toString());
        assertEquals(trustedScope, simulated.requirementEvaluation().binding().scopeInstances()
                .get(MeasurementScope.ABSOLUTE));
        assertEquals(1, contextLoads.get(), "simulation must traverse the injected trusted source exactly once");

        RankUpProgressContextSource unfavorableScaling = (requestedPlayer, state, active) ->
                new RankUpProgressContext(requestedPlayer, active.stages().revisionId(), 2,
                        ExactDecimal.parse("1"), new ScopeContext(Map.of(
                                MeasurementScope.ABSOLUTE, trustedScope)));
        RankUpPlan scalingBlocked = service(fixture.snapshot(), fixture.state(), unfavorableScaling,
                EMPTY_REQUIREMENT_STATE).authorize(intent(SECOND)).toCompletableFuture().join().plan().orElseThrow();
        assertFalse(scalingBlocked.executionAllowed(), "trusted scaling index must govern the target");

        RankUpProgressContextSource unfavorableCatchUp = (requestedPlayer, state, active) ->
                new RankUpProgressContext(requestedPlayer, active.stages().revisionId(), 1,
                        ExactDecimal.ZERO, new ScopeContext(Map.of(MeasurementScope.ABSOLUTE, trustedScope)));
        RankUpPlan catchUpBlocked = service(fixture.snapshot(), fixture.state(), unfavorableCatchUp,
                EMPTY_REQUIREMENT_STATE).authorize(intent(SECOND)).toCompletableFuture().join().plan().orElseThrow();
        assertFalse(catchUpBlocked.executionAllowed(), "trusted catch-up position must govern the target");
    }

    @Test
    @DisplayName("[A14-A17] Only the injected state reader can provide canonical latch and baseline state")
    void trustedRequirementStateIsNotCallerSupplied() {
        FakeProgressionProvider progression = registerProgressionProvider(MetricValue.decimal("0"));
        ScopeId absoluteScope = new ScopeId("trusted_latch_scope");
        RankUpProgressContextSource absoluteContext = (requestedPlayer, state, active) ->
                new RankUpProgressContext(requestedPlayer, active.stages().revisionId(), 0, ExactDecimal.ZERO,
                        new ScopeContext(Map.of(MeasurementScope.ABSOLUTE, absoluteScope)));
        RequirementLeaf latched = new RequirementLeaf(RequirementDefinition.create(
                new RequirementId("trusted_latch"), progression.descriptor().id(), new MetricId("value"),
                MetricOperator.GREATER_OR_EQUAL, RequirementTarget.single(MetricValue.decimal("10")),
                MeasurementScope.ABSOLUTE, CompletionMode.LATCHED, ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of(), Map.of(), false));
        RequirementStateReader trustedLatch = new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                return Optional.empty();
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                return Optional.of(new RequirementLatch(key, NOW));
            }
        };
        RankUpPlan latchPlan = authorizedPlan(authorizationForRequirement(latched, absoluteContext, trustedLatch));
        assertTrue(latchPlan.executionAllowed(), latchPlan.blockers().toString());

        progression.set(player, new MetricId("value"), MetricValue.decimal("15"));
        ScopeId stageScope = new ScopeId("trusted_stage_scope");
        RankUpProgressContextSource stageContext = (requestedPlayer, state, active) ->
                new RankUpProgressContext(requestedPlayer, active.stages().revisionId(), 0, ExactDecimal.ZERO,
                        new ScopeContext(Map.of(MeasurementScope.SINCE_STAGE_START, stageScope)));
        RequirementLeaf scoped = new RequirementLeaf(RequirementDefinition.create(
                new RequirementId("trusted_baseline"), progression.descriptor().id(), new MetricId("value"),
                MetricOperator.GREATER_OR_EQUAL, RequirementTarget.single(MetricValue.decimal("5")),
                MeasurementScope.SINCE_STAGE_START, CompletionMode.LIVE, ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of(), Map.of(), false));
        RequirementStateReader trustedBaseline = new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                return Optional.of(new RequirementBaseline(key, MetricValue.decimal("10"),
                        providers.find(progression.descriptor().id()).orElseThrow().generation(), NOW));
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                return Optional.empty();
            }
        };
        RankUpPlan baselinePlan = authorizedPlan(
                authorizationForRequirement(scoped, stageContext, trustedBaseline));
        assertTrue(baselinePlan.executionAllowed(), baselinePlan.blockers().toString());
        assertFalse(Arrays.stream(RankUpIntent.class.getRecordComponents()).anyMatch(component ->
                component.getType().equals(RequirementStateReader.class)),
                "a caller has no channel to replace the injected state reader");
    }

    @Test
    @DisplayName("[A23-A26] Aggregate costs, provider failure, optional rewards, and zero mutation remain fail-safe")
    void preflightSemanticsRemainFailSafe() {
        RankUpPlan unaffordable = plan(List.of(cost("first", "60"), cost("second", "60")), List.of(),
                StageProjection.none());
        assertFalse(unaffordable.executionAllowed());
        assertEquals(new BigDecimal("100"), costProvider.balance(player));

        costProvider.healthSimulator().transition(ProviderHealthState.UNAVAILABLE, "outage", "outage");
        RankUpPlan unavailable = plan(List.of(cost("payment", "25")), List.of(), StageProjection.none());
        assertFalse(unavailable.executionAllowed());
        assertTrue(unavailable.unavailableProviders().contains(COST_PROVIDER_ID));
        costProvider.healthSimulator().transition(ProviderHealthState.AVAILABLE, "restored", "restored");

        rewardProvider.healthSimulator().transition(ProviderHealthState.UNAVAILABLE, "outage", "outage");
        RewardDefinition optional = new RewardDefinition(new RewardId("optional"), REWARD_PROVIDER_ID, "grant",
                MetricValue.decimal("1"), Map.of(), "Optional", RewardFailurePolicy.OPTIONAL,
                RewardRepeatability.ONCE_PER_OPERATION);
        RankUpPlan optionalPlan = plan(List.of(cost("payment", "25")), List.of(optional), StageProjection.none());
        assertTrue(optionalPlan.executionAllowed(), optionalPlan.blockers().toString());
        assertTrue(optionalPlan.rewards().isEmpty());
        assertEquals(new BigDecimal("100"), costProvider.balance(player));
    }

    @Test
    @DisplayName("[A23-A25] Synchronous and exceptional preflight failures obey required/optional semantics")
    void normalizesBothPreflightFailureShapes() {
        costProvider.throwNextPreflight(new IllegalStateException("synchronous cost failure"));
        RankUpPlan synchronousCost = plan(List.of(cost("sync", "25")), List.of(), StageProjection.none());
        assertFalse(synchronousCost.executionAllowed());

        costProvider.failNextPreflight(new IllegalStateException("asynchronous cost failure"));
        RankUpPlan asynchronousCost = plan(List.of(cost("async", "25")), List.of(), StageProjection.none());
        assertFalse(asynchronousCost.executionAllowed());

        RewardDefinition optional = new RewardDefinition(new RewardId("optional"), REWARD_PROVIDER_ID, "grant",
                MetricValue.decimal("1"), Map.of(), "Optional", RewardFailurePolicy.OPTIONAL,
                RewardRepeatability.ONCE_PER_OPERATION);
        rewardProvider.throwNextPreflight(new IllegalStateException("synchronous optional failure"));
        RankUpPlan synchronousOptional = plan(List.of(), List.of(optional), StageProjection.none());
        assertTrue(synchronousOptional.executionAllowed(), synchronousOptional.blockers().toString());
        assertTrue(synchronousOptional.rewards().isEmpty());

        rewardProvider.failNextPreflight(new IllegalStateException("asynchronous optional failure"));
        RankUpPlan asynchronousOptional = plan(List.of(), List.of(optional), StageProjection.none());
        assertTrue(asynchronousOptional.executionAllowed(), asynchronousOptional.blockers().toString());
        assertTrue(asynchronousOptional.rewards().isEmpty());

        assertEquals(new BigDecimal("100"), costProvider.balance(player));
        assertEquals(0, rewardProvider.executionCount());
    }

    @Test
    @DisplayName("[A09-A15] Illegal skip, disabled target, and stale provider pin are rejected")
    void rejectsCanonicalBindingMismatches() {
        AuthorizationFixture fixture = authorization(List.of(cost("payment", "25")), List.of(),
                StageProjection.none());
        assertTrue(fixture.service().authorize(intent(new StageId("third"))).toCompletableFuture().join()
                .plan().isEmpty());
        assertTrue(authorization(List.of(), List.of(), StageProjection.none(), state(REVISION), false, false)
                .service().authorize(intent(SECOND)).toCompletableFuture().join().plan().isEmpty(),
                "disabled canonical target must be rejected");
        assertTrue(authorization(List.of(), List.of(), StageProjection.none(), state(REVISION), true, true)
                .service().authorize(intent(new StageId("third"))).toCompletableFuture().join().plan().isEmpty(),
                "an existing later stage must not be skipped to");

        PlayerStageState staleState = new PlayerStageState(player, FIRST, 0,
                new ConfigRevisionId("old_revision"), NOW, NOW, NOW, Optional.empty(), Optional.empty(),
                Optional.empty());
        RankUpPlan newerRevisionPlan = authorization(List.of(), List.of(), StageProjection.none(), staleState)
                .service().authorize(intent(SECOND)).toCompletableFuture().join().plan().orElseThrow();
        assertEquals(REVISION, newerRevisionPlan.configRevision(), "active revision governs the new operation");
        assertEquals(staleState.configRevision(), newerRevisionPlan.expectedPlayerConfigRevision(),
                "the exact source-state provenance remains bound independently");

        providers.unregister(costRegistration);
        costRegistration = providers.register("maddprestige-testkit", costProvider);
        providers.activate(costRegistration);
        assertTrue(fixture.service().authorize(intent(SECOND)).toCompletableFuture().join().plan().isEmpty(),
                "old active snapshot pins must not follow replacement generations");
    }

    @Test
    @DisplayName("[A04][A25] Historical player revision remains bound while a safe newer revision governs rank-up")
    void safelyAdvancesPlayerWrittenByAnOlderConfigurationRevision() throws Exception {
        AuthorizationFixture authorization = authorization(List.of(), List.of(), StageProjection.none(),
                state(SOURCE_REVISION));
        RankUpPlan plan = authorizedPlan(authorization);
        assertEquals(SOURCE_REVISION, plan.expectedPlayerConfigRevision());
        assertEquals(REVISION, plan.configRevision());

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            SqliteConfigRevisionRepository revisions = new SqliteConfigRevisionRepository(fixture.foundation());
            revisions.insert(SOURCE_REVISION, RevisionHasher.hashText("r1"));
            revisions.insert(REVISION, RevisionHasher.hashText("r3"));
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization.state());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(
                    new SqliteOperationRepository(fixture.foundation()), providers, () -> Optional.of(REVISION),
                    new RepositoryStageTransitionCommitter(stages, CLOCK),
                    new net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore(
                            fixture.foundation()),
                    Runnable::run);

            assertEquals(RankUpExecutionStatus.COMPLETED,
                    executor.execute(plan).toCompletableFuture().join().status());
            PlayerStageState advanced = stages.find(player).orElseThrow();
            assertEquals(SECOND, advanced.stageId());
            assertEquals(REVISION, advanced.configRevision(),
                    "successful advancement records the active operation revision");
            SqlitePlayerStageRepository restartedStages = new SqlitePlayerStageRepository(fixture.foundation());
            assertEquals(1, restartedStages.history(player, 10).size());
            assertEquals(SECOND, restartedStages.history(player, 10).getFirst().stageId());
            assertEquals("Normal rank-up", restartedStages.history(player, 10).getFirst().reason());
        }

        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            SqliteConfigRevisionRepository revisions = new SqliteConfigRevisionRepository(fixture.foundation());
            revisions.insert(SOURCE_REVISION, RevisionHasher.hashText("r1-concurrent"));
            revisions.insert(REVISION, RevisionHasher.hashText("r3-concurrent"));
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization.state());
            PlayerStageState concurrent = authorization.state().advanceTo(FIRST, SOURCE_REVISION,
                    Optional.empty(), NOW.plusSeconds(1));
            stages.update(concurrent, authorization.state().stateRevision());

            var outcome = new RepositoryStageTransitionCommitter(stages, CLOCK).commit(plan)
                    .toCompletableFuture().join();
            assertEquals(ActionExecutionStatus.FAILED, outcome.status());
            assertEquals(1, stages.find(player).orElseThrow().stateRevision());
            assertEquals(FIRST, stages.find(player).orElseThrow().stageId());
            assertTrue(stages.history(player, 10).isEmpty(), "failed stage CAS must not orphan history");
        }
    }

    @Test
    @DisplayName("[A04][A69] Unknown, disabled, or terminal current source stages fail closed")
    void requiresValidCurrentSourceStageAndLegalImmediateTarget() {
        StageDefinition first = new StageDefinition(FIRST, true, "First", Map.of(), StageProjection.none());
        StageDefinition disabledFirst = new StageDefinition(FIRST, false, "First", Map.of(), StageProjection.none());
        StageDefinition second = new StageDefinition(SECOND, true, "Second", Map.of(), StageProjection.none());
        PlayerStageState oldState = state(SOURCE_REVISION);

        StageConfiguration unknown = new StageConfiguration(3, true, Map.of(SECOND, second), List.of(SECOND),
                Optional.of(SECOND), ReconciliationPolicy.WARN_ONLY);
        assertTrue(serviceForStages(unknown, oldState).authorize(intent(SECOND)).toCompletableFuture().join()
                .plan().isEmpty(), "removed/unknown source stage must reject");

        StageConfiguration disabled = new StageConfiguration(3, true,
                Map.of(FIRST, disabledFirst, SECOND, second), List.of(FIRST, SECOND), Optional.of(SECOND),
                ReconciliationPolicy.WARN_ONLY);
        assertTrue(serviceForStages(disabled, oldState).authorize(intent(SECOND)).toCompletableFuture().join()
                .plan().isEmpty(), "disabled source stage must reject");

        StageConfiguration terminal = new StageConfiguration(3, true, Map.of(FIRST, first), List.of(FIRST),
                Optional.of(FIRST), ReconciliationPolicy.WARN_ONLY);
        assertTrue(serviceForStages(terminal, oldState).authorize(intent(SECOND)).toCompletableFuture().join()
                .plan().isEmpty(), "a source with no legal immediate next stage must reject");
    }

    @Test
    @DisplayName("[A05][A26][A67] Rank projection health and contract participate in planning and simulation")
    void rankProjectionHealthIsRequiredWithoutBreakingOptionalRoles() {
        MutableRankAdapter adapter = registerRankAdapter();
        StageProjection projection = StageProjection.group(RANK_PROVIDER_ID, "new");
        long generation = providers.find(RANK_PROVIDER_ID).orElseThrow().generation();
        assertTrue(simulatedPlan(List.of(), List.of(), projection).executionAllowed());

        for (ProviderHealthState health : List.of(ProviderHealthState.UNAVAILABLE,
                ProviderHealthState.UNHEALTHY, ProviderHealthState.UNSUPPORTED)) {
            adapter.healthSimulator().transition(health, "rank." + health.name().toLowerCase(), "unusable");
            assertEquals(generation, providers.find(RANK_PROVIDER_ID).orElseThrow().generation(),
                    "health transition must not masquerade as a generation replacement");
            RankUpPlan blocked = simulatedPlan(List.of(), List.of(), projection);
            assertFalse(blocked.executionAllowed(), health.name());
            assertTrue(blocked.unavailableProviders().contains(RANK_PROVIDER_ID), health.name());
        }

        adapter.healthSimulator().transition(ProviderHealthState.AVAILABLE, "rank.restored", "restored");
        assertTrue(simulatedPlan(List.of(), List.of(), projection).executionAllowed(),
                "a newly planned operation may proceed after provider restoration");
        adapter.healthSimulator().transition(ProviderHealthState.UNAVAILABLE, "rank.outage", "outage");
        assertTrue(simulatedPlan(List.of(), List.of(), StageProjection.none()).executionAllowed(),
                "projection:none must not require a rank adapter");

        adapter.healthSimulator().transition(ProviderHealthState.AVAILABLE, "rank.restored", "restored");
        providers.unregister(adapter.registration);
        FakeProvider wrongContract = new FakeProvider(RANK_PROVIDER_ID, "rank",
                List.of(new CapabilityDescriptor("rank", "rank", "wrong contract", Map.of())));
        providers.activate(providers.register("maddprestige-testkit", wrongContract));
        RankUpPlan wrongContractPlan = plan(List.of(), List.of(), projection);
        assertFalse(wrongContractPlan.executionAllowed());
        assertTrue(wrongContractPlan.unavailableProviders().contains(RANK_PROVIDER_ID));

        rewardProvider.healthSimulator().transition(ProviderHealthState.UNAVAILABLE, "optional.outage", "outage");
        RewardDefinition optional = new RewardDefinition(new RewardId("health_optional"), REWARD_PROVIDER_ID,
                "grant", MetricValue.decimal("1"), Map.of(), "Optional", RewardFailurePolicy.OPTIONAL,
                RewardRepeatability.ONCE_PER_OPERATION);
        RankUpPlan optionalPlan = plan(List.of(), List.of(optional), StageProjection.none());
        assertTrue(optionalPlan.executionAllowed(), optionalPlan.blockers().toString());
        assertTrue(optionalPlan.rewards().isEmpty());
    }

    @Test
    @DisplayName("[A25] Concrete Phase 2 repository commit runs cost -> internal commit -> reward and suppresses duplicate")
    void concreteCommitAndDuplicateSuppression() throws Exception {
        AuthorizationFixture authorization = authorization(List.of(cost("payment", "25")), List.of(reward()),
                StageProjection.none());
        RankUpPlan plan = authorizedPlan(authorization);
        assertTrue(plan.authorization().matches(plan), "normal canonical execution must retain a valid seal");
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            new SqliteConfigRevisionRepository(fixture.foundation()).insert(REVISION, RevisionHasher.hashText("r3"));
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            stages.insert(authorization.state());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(
                    new SqliteOperationRepository(fixture.foundation()), providers, () -> Optional.of(REVISION),
                    new RepositoryStageTransitionCommitter(stages, CLOCK),
                    new net.maddkraft.maddprestige.persistence.admin.SqliteStageReferenceMigrationStore(
                            fixture.foundation()),
                    Runnable::run);
            assertEquals(RankUpExecutionStatus.COMPLETED,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertEquals(SECOND, stages.find(player).orElseThrow().stageId());
            assertEquals(1, stages.history(player, 10).size());
            assertEquals(new BigDecimal("75"), costProvider.balance(player));
            assertEquals(1, rewardProvider.executionCount());
            assertEquals(RankUpExecutionStatus.DUPLICATE,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertEquals(1, rewardProvider.executionCount());
        }
    }

    @Test
    @DisplayName("[A25][A59-A60] Compensation success retains original evidence and journals its own action")
    void journalsSuccessfulCompensationSeparately() throws Exception {
        RankUpPlan plan = plan(List.of(cost("payment", "25")), List.of(), StageProjection.none());
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = executor(operations,
                    ignored -> CompletableFuture.completedFuture(ActionExecutionResult.failed("known conflict")));
            assertEquals(RankUpExecutionStatus.COMPENSATED,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertEquals(ActionState.VERIFIED, operations.findAction(plan.operationId(), "cost-0")
                    .orElseThrow().state(), "original successful debit evidence is retained");
            var compensation = operations.findAction(plan.operationId(), RankUpPlan.compensationActionId("cost-0"))
                    .orElseThrow();
            assertEquals(ActionState.VERIFIED, compensation.state());
            assertTrue(compensation.failureReason().orElseThrow().contains("result=APPLIED"));
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
        }
    }

    @Test
    @DisplayName("[A59-A60] Failed, uncertain, and exceptional compensation identify the exact persisted action")
    void journalsEveryCompensationFailureShape() throws Exception {
        for (CompensationCase value : List.of(
                new CompensationCase(ActionState.FAILED,
                        () -> costProvider.nextCompensation(ActionExecutionResult.failed("refund denied"))),
                new CompensationCase(ActionState.UNCERTAIN,
                        () -> costProvider.nextCompensation(ActionExecutionResult.uncertain("refund timed out"))),
                new CompensationCase(ActionState.UNCERTAIN,
                        () -> costProvider.failNextCompensation(new IllegalStateException("transport failed"))))) {
            costProvider.balance(player, "100");
            value.prepare().run();
            RankUpPlan plan = plan(List.of(cost("payment-" + UUID.randomUUID(), "25")), List.of(),
                    StageProjection.none());
            try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
                prepareRevision(fixture);
                SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
                assertEquals(RankUpExecutionStatus.NEEDS_RECONCILIATION,
                        executor(operations, ignored -> CompletableFuture.completedFuture(
                                ActionExecutionResult.failed("known conflict")))
                                .execute(plan).toCompletableFuture().join().status());
                var reloaded = new SqliteOperationRepository(fixture.foundation()).findAction(plan.operationId(),
                        RankUpPlan.compensationActionId("cost-0")).orElseThrow();
                assertEquals(value.expected(), reloaded.state());
                assertTrue(reloaded.failureReason().orElseThrow().contains("reconciliation=true"));
            }
        }
    }

    @Test
    @DisplayName("[A59] Reverse compensation order records an earlier success before a later exact failure")
    void secondCompensationFailureKeepsEarlierEvidence() throws Exception {
        costProvider.nextCompensation(ActionExecutionResult.applied());
        costProvider.nextCompensation(ActionExecutionResult.failed("second refund failed"));
        RankUpPlan plan = plan(List.of(cost("first", "10"), cost("second", "10")), List.of(),
                StageProjection.none());
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            assertEquals(RankUpExecutionStatus.NEEDS_RECONCILIATION,
                    executor(operations, ignored -> CompletableFuture.completedFuture(
                            ActionExecutionResult.failed("known conflict")))
                            .execute(plan).toCompletableFuture().join().status());
            assertEquals(ActionState.VERIFIED, operations.findAction(plan.operationId(),
                    RankUpPlan.compensationActionId("cost-1")).orElseThrow().state());
            assertEquals(ActionState.FAILED, operations.findAction(plan.operationId(),
                    RankUpPlan.compensationActionId("cost-0")).orElseThrow().state());
        }
    }

    @Test
    @DisplayName("[A05-A07][A25] Journaled Phase 3 projection preserves unrelated membership and never replays")
    void projectedRankUpSuccessPreservesMembershipAndDoesNotReplay() throws Exception {
        MutableRankAdapter adapter = registerRankAdapter();
        RankUpPlan plan = plan(List.of(cost("payment", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        AtomicInteger commits = new AtomicInteger();
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = executor(operations, ignored -> {
                commits.incrementAndGet();
                return CompletableFuture.completedFuture(ActionExecutionResult.applied());
            });
            assertEquals(RankUpExecutionStatus.COMPLETED,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertEquals(Set.of("staff", "supporter", "new"), adapter.memberships);
            assertEquals(ActionState.VERIFIED,
                    operations.findAction(plan.operationId(), "rank-projection").orElseThrow().state());
            assertEquals(RankUpExecutionStatus.DUPLICATE,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertEquals(1, adapter.projectionCalls.get());
            assertEquals(1, commits.get());
        }
    }

    @Test
    @DisplayName("[A05][A25] Missing external target is revalidated before any cost/stage/reward mutation")
    void missingProjectionTargetMutatesNothing() throws Exception {
        MutableRankAdapter adapter = registerRankAdapter();
        RankUpPlan plan = plan(List.of(cost("payment", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        adapter.availableGroups = Set.of("old");
        AtomicInteger commits = new AtomicInteger();
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            var result = executor(new SqliteOperationRepository(fixture.foundation()), ignored -> {
                commits.incrementAndGet();
                return CompletableFuture.completedFuture(ActionExecutionResult.applied());
            }).execute(plan).toCompletableFuture().join();
            assertEquals(RankUpExecutionStatus.FAILED, result.status());
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, adapter.projectionCalls.get());
            assertEquals(0, commits.get());
            assertEquals(0, rewardProvider.executionCount());
        }
    }

    @Test
    @DisplayName("[A06-A07][A60] Known/uncertain projection and internal-commit divergence are persisted honestly")
    void projectionAndInternalDivergenceNeedCorrectRecovery() throws Exception {
        MutableRankAdapter known = registerRankAdapter();
        known.nextProjection = Result.failure(new StructuredError("rank.failed", ErrorCategory.FAILED,
                "known external rejection", Map.of()));
        RankUpPlan knownPlan = plan(List.of(cost("known", "25")), List.of(),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            assertEquals(RankUpExecutionStatus.COMPENSATED,
                    executor(new SqliteOperationRepository(fixture.foundation()), ignored ->
                            CompletableFuture.completedFuture(ActionExecutionResult.applied()))
                            .execute(knownPlan).toCompletableFuture().join().status());
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
        }

        providers = new ProviderRegistry();
        providers.activate(providers.register("maddprestige-testkit", costProvider));
        providers.activate(providers.register("maddprestige-testkit", rewardProvider));
        MutableRankAdapter uncertain = registerRankAdapter();
        uncertain.nextProjection = Result.failure(new StructuredError("rank.timeout", ErrorCategory.UNCERTAIN,
                "save outcome unknown", Map.of()));
        RankUpPlan uncertainPlan = plan(List.of(cost("uncertain", "25")), List.of(),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            assertEquals(RankUpExecutionStatus.NEEDS_RECONCILIATION,
                    executor(new SqliteOperationRepository(fixture.foundation()), ignored ->
                            CompletableFuture.completedFuture(ActionExecutionResult.applied()))
                            .execute(uncertainPlan).toCompletableFuture().join().status());
        }

        providers = new ProviderRegistry();
        providers.activate(providers.register("maddprestige-testkit", costProvider));
        providers.activate(providers.register("maddprestige-testkit", rewardProvider));
        MutableRankAdapter projected = registerRankAdapter();
        RankUpPlan internalFailure = plan(List.of(cost("internal", "25")), List.of(),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            assertEquals(RankUpExecutionStatus.NEEDS_RECONCILIATION,
                    executor(new SqliteOperationRepository(fixture.foundation()), ignored ->
                            CompletableFuture.completedFuture(ActionExecutionResult.failed("CAS conflict")))
                            .execute(internalFailure).toCompletableFuture().join().status());
            assertEquals(1, projected.projectionCalls.get());
        }

        providers = new ProviderRegistry();
        providers.activate(providers.register("maddprestige-testkit", costProvider));
        providers.activate(providers.register("maddprestige-testkit", rewardProvider));
        MutableRankAdapter uncertainInternal = registerRankAdapter();
        RankUpPlan internalUncertainty = plan(List.of(cost("internal-uncertain", "25")), List.of(),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            assertEquals(RankUpExecutionStatus.NEEDS_RECONCILIATION,
                    executor(new SqliteOperationRepository(fixture.foundation()), ignored ->
                            CompletableFuture.completedFuture(ActionExecutionResult.uncertain("CAS outcome unknown")))
                            .execute(internalUncertainty).toCompletableFuture().join().status());
            assertEquals(1, uncertainInternal.projectionCalls.get());
        }
    }

    @Test
    @DisplayName("[A05][A25] Rank provider replacement after target validation is caught before projection")
    void detectsGenerationChangeImmediatelyBeforeProjection() throws Exception {
        MutableRankAdapter adapter = registerRankAdapter();
        RankUpPlan plan = plan(List.of(cost("generation-race", "25")), List.of(reward()),
                StageProjection.group(RANK_PROVIDER_ID, "new"));
        adapter.afterTargetValidation = () -> {
            providers.unregister(adapter.registration);
            providers.activate(providers.register("maddprestige-testkit", new MutableRankAdapter()));
        };
        AtomicInteger commits = new AtomicInteger();
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            var result = executor(new SqliteOperationRepository(fixture.foundation()), ignored -> {
                commits.incrementAndGet();
                return CompletableFuture.completedFuture(ActionExecutionResult.applied());
            }).execute(plan).toCompletableFuture().join();

            assertEquals(RankUpExecutionStatus.COMPENSATED, result.status());
            assertEquals(0, adapter.projectionCalls.get());
            assertEquals(0, commits.get());
            assertEquals(0, rewardProvider.executionCount());
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
        }
    }

    private RankUpPlan plan(
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            StageProjection projection) {
        return authorizedPlan(authorization(costs, rewards, projection));
    }

    private void assertPostPreStaleHasZeroEffects(
            RankUpPlan plan,
            MutableRankAdapter rank,
            OperationLifecycleListener events,
            java.util.function.Supplier<Optional<ConfigRevisionId>> active,
            java.util.function.Predicate<RankUpPlan> stateRevalidator) throws Exception {
        try (DisposableSqliteFixture fixture = DisposableSqliteFixture.create()) {
            prepareRevision(fixture);
            SqlitePlayerStageRepository stages = new SqlitePlayerStageRepository(fixture.foundation());
            SqlitePlayerPrestigeRepository prestiges = new SqlitePlayerPrestigeRepository(fixture.foundation());
            SqliteOperationRepository operations = new SqliteOperationRepository(fixture.foundation());
            RankUpOperationExecutor executor = new RankUpOperationExecutor(operations, providers, active,
                    new RepositoryStageTransitionCommitter(stages, CLOCK), new InMemoryStageTransitionFence(),
                    Runnable::run, events, stateRevalidator,
                    ignored -> new SqlitePlayerInitializationStore(fixture.foundation()).initialize(
                            player, FIRST, REVISION, new ScopeId("unknown_player_prestige"), NOW));

            assertEquals(RankUpExecutionStatus.STALE_GENERATION,
                    executor.execute(plan).toCompletableFuture().join().status());
            assertTrue(operations.find(plan.operationId()).isEmpty());
            assertTrue(stages.find(player).isEmpty());
            assertTrue(prestiges.find(player).isEmpty());
            assertEquals(0L, rowCount(fixture, "mp_operations"));
            assertEquals(0L, rowCount(fixture, "mp_operation_actions"));
            assertEquals(0L, rowCount(fixture, "mp_stage_transition_leases"));
            assertEquals(new BigDecimal("100"), costProvider.balance(player));
            assertEquals(0, rewardProvider.executionCount());
            assertEquals(0, rank.projectionCalls.get());
        }
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

    private RankUpPlan simulatedPlan(
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            StageProjection projection) {
        AuthorizationFixture fixture = authorization(costs, rewards, projection);
        return new RankUpSimulationService(fixture.service()).simulate(intent(SECOND))
                .toCompletableFuture().join().plan().orElseThrow();
    }

    private static RankUpPlan reconstruct(
            RankUpPlan original,
            BoundRequirementEvaluation requirementEvaluation,
            Optional<StageProjection> externalRankProjection,
            List<String> blockers,
            boolean executionAllowed) {
        return new RankUpPlan(original.operationId(), original.playerId(), original.sourceStage(),
                original.targetStage(), original.expectedStateRevision(), original.expectedPlayerConfigRevision(),
                original.configRevision(), original.providerGenerations(), requirementEvaluation, original.costs(),
                original.rewards(), externalRankProjection, original.rankProjectionRequest(),
                original.unavailableProviders(), blockers, executionAllowed, original.operationPlan(),
                original.authorization());
    }

    private static RequirementLeaf liveRequirement(
            FakeProgressionProvider progression,
            String id,
            String target) {
        return new RequirementLeaf(RequirementDefinition.create(new RequirementId(id), progression.descriptor().id(),
                new MetricId("value"), MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.decimal(target)), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false));
    }

    private AuthorizationFixture authorizationForRequirement(
            RequirementLeaf requirement,
            RankUpProgressContextSource progressContexts,
            RequirementStateReader requirementStates) {
        StageDefinition first = new StageDefinition(FIRST, true, "First", Map.of(), StageProjection.none());
        StageDefinition second = new StageDefinition(SECOND, true, "Second", Map.of(), StageProjection.none(),
                Optional.of(requirement.id()), List.of(), List.of());
        StageConfiguration stages = new StageConfiguration(3, true, Map.of(FIRST, first, SECOND, second),
                List.of(FIRST, SECOND), Optional.of(FIRST), ReconciliationPolicy.WARN_ONLY);
        PhaseThreeConfiguration phaseThree = new PhaseThreeConfiguration(3, 16,
                Map.of(requirement.id(), requirement.definition()), Map.of(requirement.id(), requirement),
                Map.of(), Map.of(), CommandActionPolicy.safeDefaults());
        ProviderId providerId = requirement.definition().providerId();
        Map<ProviderId, Long> generations = Map.of(providerId,
                providers.find(providerId).orElseThrow().generation());
        ActiveStageConfiguration active = new ActiveStageConfiguration(
                new StageConfigurationSnapshot(REVISION, stages),
                new PhaseThreeConfigurationSnapshot(REVISION, phaseThree, generations));
        PlayerStageState state = state(REVISION);
        return new AuthorizationFixture(active, service(active, state, progressContexts, requirementStates),
                state, second);
    }

    private RankUpAuthorizationService serviceForStages(
            StageConfiguration stages,
            PlayerStageState state) {
        ActiveStageConfiguration active = new ActiveStageConfiguration(
                new StageConfigurationSnapshot(REVISION, stages),
                new PhaseThreeConfigurationSnapshot(REVISION, PhaseThreeConfiguration.empty(), Map.of()));
        return service(active, state, defaultProgressContext(), EMPTY_REQUIREMENT_STATE);
    }

    private FakeProgressionProvider registerProgressionProvider(MetricValue value) {
        FakeProgressionProvider provider = new FakeProgressionProvider();
        provider.set(player, new MetricId("value"), value);
        providers.activate(providers.register("maddprestige-testkit", provider));
        return provider;
    }

    private RankUpPlan authorizedPlan(AuthorizationFixture fixture) {
        var result = fixture.service().authorize(intent(SECOND)).toCompletableFuture().join();
        return result.plan().orElseThrow(() -> new AssertionError(result.blockers()));
    }

    private AuthorizationFixture authorization(
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            StageProjection projection) {
        return authorization(costs, rewards, projection, state(REVISION));
    }

    private AuthorizationFixture authorization(
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            StageProjection projection,
            PlayerStageState state) {
        return authorization(costs, rewards, projection, state, true, false);
    }

    private AuthorizationFixture authorization(
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            StageProjection projection,
            PlayerStageState state,
            boolean targetEnabled,
            boolean includeThird) {
        StageProjection firstProjection = projection.policy() == ProjectionPolicy.GROUP
                ? StageProjection.group(RANK_PROVIDER_ID, "old") : StageProjection.none();
        StageDefinition first = new StageDefinition(FIRST, true, "First", Map.of(), firstProjection);
        StageDefinition second = new StageDefinition(SECOND, targetEnabled, "Second", Map.of(), projection,
                Optional.empty(), costs.stream().map(CostDefinition::id).toList(),
                rewards.stream().map(RewardDefinition::id).toList());
        StageId thirdId = new StageId("third");
        StageDefinition third = new StageDefinition(thirdId, true, "Third", Map.of(), StageProjection.none());
        Map<StageId, StageDefinition> stageMap = includeThird
                ? Map.of(FIRST, first, SECOND, second, thirdId, third) : Map.of(FIRST, first, SECOND, second);
        List<StageId> order = includeThird ? List.of(FIRST, SECOND, thirdId) : List.of(FIRST, SECOND);
        StageConfiguration stageConfiguration = new StageConfiguration(3, true,
                stageMap, order, Optional.of(FIRST),
                ReconciliationPolicy.WARN_ONLY);
        Map<CostId, CostDefinition> costMap = new LinkedHashMap<>();
        costs.forEach(value -> costMap.put(value.id(), value));
        Map<RewardId, RewardDefinition> rewardMap = new LinkedHashMap<>();
        rewards.forEach(value -> rewardMap.put(value.id(), value));
        PhaseThreeConfiguration phaseThree = new PhaseThreeConfiguration(3, 16, Map.of(), Map.of(), costMap,
                rewardMap, CommandActionPolicy.safeDefaults());
        LinkedHashMap<ProviderId, Long> generations = new LinkedHashMap<>();
        if (!costs.isEmpty()) {
            generations.put(COST_PROVIDER_ID, providers.find(COST_PROVIDER_ID).orElseThrow().generation());
        }
        if (!rewards.isEmpty()) {
            generations.put(REWARD_PROVIDER_ID, providers.find(REWARD_PROVIDER_ID).orElseThrow().generation());
        }
        if (projection.policy() == ProjectionPolicy.GROUP) {
            generations.put(RANK_PROVIDER_ID, providers.find(RANK_PROVIDER_ID).orElseThrow().generation());
        }
        ActiveStageConfiguration active = new ActiveStageConfiguration(
                new StageConfigurationSnapshot(REVISION, stageConfiguration),
                new PhaseThreeConfigurationSnapshot(REVISION, phaseThree, generations));
        RankUpAuthorizationService service = service(active, state, defaultProgressContext(),
                EMPTY_REQUIREMENT_STATE);
        return new AuthorizationFixture(active, service, state, second);
    }

    private RankUpIntent intent(StageId target) {
        return new RankUpIntent(actor(), player, Optional.of(target), "rank-up-click");
    }

    private RankUpAuthorizationService service(
            ActiveStageConfiguration active,
            PlayerStageState state,
            RankUpProgressContextSource progressContexts,
            RequirementStateReader requirementStates) {
        return new RankUpAuthorizationService(() -> Optional.of(active), ignored -> Optional.of(state),
                progressContexts, requirementStates, providers, CLOCK);
    }

    private RankUpProgressContextSource defaultProgressContext() {
        return (requestedPlayer, state, active) -> new RankUpProgressContext(requestedPlayer,
                active.stages().revisionId(), 0, ExactDecimal.ZERO, new ScopeContext(Map.of()));
    }

    private RankUpOperationExecutor executor(
            SqliteOperationRepository operations,
            net.maddkraft.maddprestige.core.plan.StageTransitionCommitter committer) {
        return new RankUpOperationExecutor(operations, providers, () -> Optional.of(REVISION), committer,
                new InMemoryStageTransitionFence(), Runnable::run);
    }

    private static void prepareRevision(DisposableSqliteFixture fixture) {
        new SqliteConfigRevisionRepository(fixture.foundation()).insert(REVISION, RevisionHasher.hashText("r3"));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for deterministic race release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private MutableRankAdapter registerRankAdapter() {
        MutableRankAdapter adapter = new MutableRankAdapter();
        ProviderRegistration registration = providers.register("maddprestige-testkit", adapter);
        adapter.registration = registration;
        providers.activate(registration);
        return adapter;
    }

    private PlayerStageState state(ConfigRevisionId revision) {
        return new PlayerStageState(player, FIRST, 0, revision, NOW, NOW, NOW, Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    private static Actor actor() {
        return new Actor("console", Optional.empty(), "Console");
    }

    private static CostDefinition cost(String id, String amount) {
        return new CostDefinition(new CostId(id), COST_PROVIDER_ID, "debit",
                MetricValue.parse(net.maddkraft.maddprestige.api.metric.MetricValueType.CURRENCY_AMOUNT, amount),
                Map.of(), id);
    }

    private static RewardDefinition reward() {
        return reward("grant");
    }

    private static RewardDefinition reward(String id) {
        return new RewardDefinition(new RewardId(id), REWARD_PROVIDER_ID, "grant", MetricValue.decimal("1"),
                Map.of(), id, RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
    }

    private record AuthorizationFixture(
            ActiveStageConfiguration snapshot,
            RankUpAuthorizationService service,
            PlayerStageState state,
            StageDefinition target) {
    }

    private record CompensationCase(ActionState expected, Runnable prepare) {
    }

    private final class MutableRankAdapter extends FakeProvider implements RankAdapter {
        private Set<String> availableGroups = Set.of("old", "new");
        private Set<String> memberships = Set.of("old", "staff", "supporter");
        private Result<RankProjectionResult> nextProjection;
        private Runnable afterTargetValidation = () -> { };
        private ProviderRegistration registration;
        private final AtomicInteger projectionCalls = new AtomicInteger();

        private MutableRankAdapter() {
            super(RANK_PROVIDER_ID, "rank", List.of(new CapabilityDescriptor("rank", "rank", "rank", Map.of())));
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<Set<String>>> validateTargets(Set<String> groups) {
            Result<Set<String>> result = Result.success(availableGroups);
            afterTargetValidation.run();
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<ManagedRankState>> readManagedState(
                UUID playerId,
                Set<String> managedGroups) {
            return CompletableFuture.completedFuture(Result.success(
                    new ManagedRankState(playerId, memberships, List.of())));
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<RankProjectionResult>> project(
                RankProjectionRequest request) {
            projectionCalls.incrementAndGet();
            if (nextProjection != null) {
                Result<RankProjectionResult> result = nextProjection;
                nextProjection = null;
                return CompletableFuture.completedFuture(result);
            }
            ManagedRankState before = new ManagedRankState(request.playerId(), memberships, List.of());
            Set<String> replacement = new java.util.HashSet<>(memberships);
            replacement.removeAll(request.managedGroups());
            request.desiredGroup().ifPresent(replacement::add);
            memberships = Set.copyOf(replacement);
            ManagedRankState after = new ManagedRankState(request.playerId(), memberships, List.of());
            return CompletableFuture.completedFuture(Result.success(
                    new RankProjectionResult(before, after, RankProjectionOutcome.APPLIED)));
        }
    }
}
