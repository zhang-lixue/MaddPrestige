package net.maddkraft.maddprestige.core.config.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementChild;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressionConfigurationValidatorTest {
    private static final ProviderId METRICS = new ProviderId("metrics");
    private static final ProviderId COSTS = new ProviderId("costs");
    private static final ProviderId REWARDS = new ProviderId("rewards");
    private static final ProviderId ABSENT = new ProviderId("absent");
    private static final RequirementId REQUIREMENT = new RequirementId("progress");
    private static final RequirementId TREE = new RequirementId("eligibility");
    private static final CostId COST = new CostId("payment");
    private static final RewardId REWARD = new RewardId("grant");
    private static final StageId STAGE = new StageId("stage");

    @Test
    @DisplayName("[A49] Dormant definitions and disabled-stage references do not activate absent providers")
    void ignoresDormantAndDisabledReferences() {
        ProgressionConfiguration definitions = configuration(ABSENT, ABSENT, ABSENT,
                RewardFailurePolicy.REQUIRED);
        StageDefinition dormant = stage(false, true, true, true);

        var report = validate(definitions, stages(dormant), new ProviderRegistry());

        assertFalse(report.report().hasErrors(), report.report().toString());
        assertTrue(report.providerGenerations().isEmpty());
    }

    @Test
    @DisplayName("[A49] Active metric, cost, and REQUIRED reward references each fail closed when absent")
    void requiresEveryConsequentialActiveReference() {
        assertUnavailable(configuration(ABSENT, COSTS, REWARDS, RewardFailurePolicy.OPTIONAL),
                stage(true, true, false, false));
        assertUnavailable(configuration(METRICS, ABSENT, REWARDS, RewardFailurePolicy.OPTIONAL),
                stage(true, false, true, false));
        assertUnavailable(configuration(METRICS, COSTS, ABSENT, RewardFailurePolicy.REQUIRED),
                stage(true, false, false, true));
    }

    @Test
    @DisplayName("[A25][A49] Missing OPTIONAL reward is non-blocking and contributes no generation pin")
    void permitsAbsentOptionalReward() {
        ProgressionConfiguration configuration = configuration(METRICS, COSTS, ABSENT,
                RewardFailurePolicy.OPTIONAL);
        var result = validate(configuration, stages(stage(true, false, false, true)), new ProviderRegistry());

        assertFalse(result.report().hasErrors(), result.report().toString());
        assertTrue(result.providerGenerations().isEmpty());
    }

    @Test
    @DisplayName("[A49] Pins contain exactly reachable required and available optional providers")
    void pinsTheExactRuntimeReferenceSet() {
        ProviderRegistry providers = new ProviderRegistry();
        var metricRegistration = providers.register("owner", new TestMetricProvider());
        var rewardRegistration = providers.register("owner", new TestRewardProvider(false));
        providers.activate(metricRegistration);
        providers.activate(rewardRegistration);
        ProgressionConfiguration configuration = configuration(METRICS, ABSENT, REWARDS,
                RewardFailurePolicy.OPTIONAL);
        StageDefinition enabled = stage(true, true, false, true);
        StageDefinition disabled = new StageDefinition(new StageId("disabled"), false, "Disabled", Map.of(),
                StageProjection.none(), Optional.empty(), List.of(COST), List.of());

        var result = validate(configuration, stages(enabled, disabled), providers);

        assertFalse(result.report().hasErrors(), result.report().toString());
        assertEquals(Map.of(METRICS, metricRegistration.generation(), REWARDS, rewardRegistration.generation()),
                result.providerGenerations());
    }

    @Test
    @DisplayName("[A49] Synchronous optional-provider validation failure becomes a structured finding")
    void normalizesProviderValidationException() {
        ProviderRegistry providers = new ProviderRegistry();
        var registration = providers.register("owner", new TestRewardProvider(true));
        providers.activate(registration);

        var result = validate(configuration(METRICS, COSTS, REWARDS, RewardFailurePolicy.OPTIONAL),
                stages(stage(true, false, false, true)), providers);

        assertTrue(result.report().findings().stream().anyMatch(finding ->
                finding.code().equals("progression.configuration.provider.validation_exception")));
    }

    private static ProgressionProviderValidation validate(
            ProgressionConfiguration configuration,
            StageConfiguration stages,
            ProviderRegistry providers) {
        return new ProgressionConfigurationValidator().validate(configuration, stages, providers);
    }

    private static void assertUnavailable(ProgressionConfiguration configuration, StageDefinition stage) {
        var result = validate(configuration, stages(stage), new ProviderRegistry());
        assertTrue(result.report().findings().stream().anyMatch(finding ->
                finding.code().equals("progression.configuration.provider.unavailable")), result.report().toString());
    }

    private static StageConfiguration stages(StageDefinition... definitions) {
        Map<StageId, StageDefinition> byId = java.util.Arrays.stream(definitions)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(StageDefinition::id, value -> value));
        return new StageConfiguration(3, true, byId,
                java.util.Arrays.stream(definitions).map(StageDefinition::id).toList(), Optional.of(STAGE),
                ReconciliationPolicy.WARN_ONLY);
    }

    private static StageDefinition stage(
            boolean enabled, boolean requirement, boolean cost, boolean reward) {
        return new StageDefinition(STAGE, enabled, "Stage", Map.of(), StageProjection.none(),
                requirement ? Optional.of(TREE) : Optional.empty(), cost ? List.of(COST) : List.of(),
                reward ? List.of(REWARD) : List.of());
    }

    private static ProgressionConfiguration configuration(
            ProviderId metricProvider,
            ProviderId costProvider,
            ProviderId rewardProvider,
            RewardFailurePolicy rewardPolicy) {
        RequirementDefinition definition = RequirementDefinition.create(REQUIREMENT, metricProvider,
                TestMetricProvider.METRIC, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.count(1)), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false);
        var tree = RequirementGroup.all(TREE,
                List.of(RequirementChild.unweighted(new RequirementLeaf(definition))));
        CostDefinition cost = new CostDefinition(COST, costProvider, "debit", MetricValue.decimal("1"),
                Map.of(), "Cost");
        RewardDefinition reward = new RewardDefinition(REWARD, rewardProvider, "grant", MetricValue.decimal("1"),
                Map.of(), "Reward", rewardPolicy, RewardRepeatability.ONCE_PER_OPERATION);
        return new ProgressionConfiguration(3, 16, Map.of(REQUIREMENT, definition), Map.of(TREE, tree),
                Map.of(COST, cost), Map.of(REWARD, reward), CommandActionPolicy.safeDefaults());
    }

    private static ProviderDescriptor descriptor(ProviderId id) {
        return new ProviderDescriptor(id, "owner", "1", "1", List.of(), List.of());
    }

    private static ProviderHealth health() {
        return new ProviderHealth(ProviderHealthState.AVAILABLE, "ready", "ready", Instant.EPOCH);
    }

    private static final class TestMetricProvider implements MetricProvider {
        private static final MetricId METRIC = new MetricId("count");
        private static final MetricDescriptor DESCRIPTOR = new MetricDescriptor(METRICS, METRIC,
                MetricValueType.COUNT, MetricOperator.compatibleWith(MetricValueType.COUNT),
                Set.of(MetricReadMode.CURRENT), false, MetricMonotonicity.MONOTONIC,
                MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Count", "Count", "count", "test");

        @Override
        public Collection<MetricDescriptor> metrics() {
            return List.of(DESCRIPTOR);
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId, List<MetricQuery> queries, long providerGeneration) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public ProviderDescriptor descriptor() {
            return ProgressionConfigurationValidatorTest.descriptor(METRICS);
        }

        @Override
        public ProviderHealth health() {
            return ProgressionConfigurationValidatorTest.health();
        }
    }

    private static final class TestRewardProvider implements RewardProvider {
        private final boolean throwValidation;

        private TestRewardProvider(boolean throwValidation) {
            this.throwValidation = throwValidation;
        }

        @Override
        public ActionCharacteristics characteristics(RewardDefinition definition) {
            return new ActionCharacteristics(true, false, false, false);
        }

        @Override
        public ValidationReport validate(RewardDefinition definition) {
            if (throwValidation) {
                throw new IllegalStateException("broken adapter");
            }
            return ValidationReport.VALID;
        }

        @Override
        public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
            return CompletableFuture.completedFuture(RewardPreflight.ready(proposed));
        }

        @Override
        public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
            return CompletableFuture.completedFuture(ActionExecutionResult.applied());
        }

        @Override
        public ProviderDescriptor descriptor() {
            return ProgressionConfigurationValidatorTest.descriptor(REWARDS);
        }

        @Override
        public ProviderHealth health() {
            return ProgressionConfigurationValidatorTest.health();
        }
    }
}
