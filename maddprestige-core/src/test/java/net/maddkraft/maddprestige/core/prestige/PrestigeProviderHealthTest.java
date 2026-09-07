package net.maddkraft.maddprestige.core.prestige;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
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
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.lifecycle.ActiveLifecycleConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.lifecycle.PrestigeConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.PrestigeLimit;
import net.maddkraft.maddprestige.core.config.lifecycle.ResetPreservePolicy;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.BaselineKey;
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
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.Test;

class PrestigeProviderHealthTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final ConfigRevisionId REVISION = new ConfigRevisionId("prestige-provider-health");
    private static final StageId ORIGIN = new StageId("origin");
    private static final StageId SUMMIT = new StageId("summit");
    private static final RequirementId BOUNDARY = new RequirementId("reachable_boundary");
    private static final MetricId METRIC = new MetricId("count");

    @Test
    void unhealthyRankAdapterIsIrrelevantWithoutConfiguredLuckPermsReward() {
        ProviderRegistry providers = new ProviderRegistry();
        ProviderId id = new ProviderId("unhealthy_rank");
        UnhealthyRankAdapter adapter = new UnhealthyRankAdapter(id);
        var registration = providers.register("test", adapter);
        providers.activate(registration);

        PrestigeAuthorizationResult result = service(providers, Map.of(id, registration.generation()),
                StageProjection.group(id, "origin"), Optional.empty(), false).authorize(intent())
                .toCompletableFuture().join();

        assertTrue(result.plan().orElseThrow().executionAllowed());
        assertTrue(result.plan().orElseThrow().rankProjectionRequest().isEmpty());
    }

    @Test
    void unhealthyRequiredBoundaryProviderBlocksButDormantMissingProviderDoesNot() {
        ProviderRegistry providers = new ProviderRegistry();
        ProviderId id = new ProviderId("unhealthy_boundary");
        BoundaryMetricProvider metric = new BoundaryMetricProvider(id, ProviderHealthState.UNAVAILABLE);
        var registration = providers.register("test", metric);
        providers.activate(registration);
        assertTrue(service(providers, Map.of(id, registration.generation()), StageProjection.none(),
                Optional.of(id), true).authorize(intent()).toCompletableFuture().join()
                .plan().orElseThrow().executionAllowed());

        PrestigeAuthorizationResult dormant = service(new ProviderRegistry(), Map.of(), StageProjection.none(),
                Optional.of(new ProviderId("missing_dormant")), false).authorize(intent())
                .toCompletableFuture().join();
        assertTrue(dormant.plan().orElseThrow().executionAllowed());
        assertTrue(dormant.plan().orElseThrow().simulation().baselineChanges().isEmpty());
    }

    @Test
    void unrelatedOptionalUnhealthyProviderDoesNotEnterTheOperationSeal() {
        ProviderRegistry providers = new ProviderRegistry();
        ProviderId id = new ProviderId("unrelated_optional");
        Provider provider = new SimpleProvider(id, ProviderHealthState.UNAVAILABLE);
        var registration = providers.register("test", provider);
        providers.activate(registration);

        PrestigePlan plan = service(providers, Map.of(id, registration.generation()), StageProjection.none(),
                Optional.empty(), false).authorize(intent()).toCompletableFuture().join().plan().orElseThrow();
        assertTrue(plan.executionAllowed());
        assertFalse(plan.providerGenerations().containsKey(id));
    }

    private static PrestigeAuthorizationService service(
            ProviderRegistry providers,
            Map<ProviderId, Long> pins,
            StageProjection resetProjection,
            Optional<ProviderId> boundaryProvider,
            boolean reachableBoundary) {
        UUID player = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RequirementDefinition boundary = boundaryProvider.map(id -> RequirementDefinition.create(BOUNDARY, id,
                METRIC, MetricOperator.GREATER_OR_EQUAL, RequirementTarget.single(MetricValue.count(1)),
                MeasurementScope.SINCE_PRESTIGE_START, CompletionMode.LIVE, ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of(), Map.of(), false)).orElse(null);
        Map<RequirementId, RequirementDefinition> definitions = boundary == null ? Map.of() : Map.of(BOUNDARY, boundary);
        Map<RequirementId, net.maddkraft.maddprestige.core.requirement.RequirementNode> trees = boundary == null
                ? Map.of() : Map.of(BOUNDARY, new RequirementLeaf(boundary));
        ProgressionConfiguration progression = new ProgressionConfiguration(3, 16, definitions, trees, Map.of(),
                Map.of(), CommandActionPolicy.safeDefaults());
        StageDefinition origin = new StageDefinition(ORIGIN, true, "Origin", Map.of(), resetProjection);
        if (reachableBoundary) {
            origin = new StageDefinition(ORIGIN, true, "Origin", Map.of(), resetProjection,
                    Optional.of(BOUNDARY), List.of(), List.of());
        }
        StageDefinition summit = new StageDefinition(SUMMIT, true, "Summit", Map.of(), StageProjection.none());
        StageConfiguration stages = new StageConfiguration(2, true, Map.of(ORIGIN, origin, SUMMIT, summit),
                List.of(ORIGIN, SUMMIT), Optional.of(ORIGIN), ReconciliationPolicy.WARN_ONLY);
        PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(SUMMIT), ORIGIN, 1, 1,
                PrestigeLimit.unlimited(), Duration.ZERO, Optional.empty(), List.of(), List.of(), Optional.empty(),
                Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
        LifecycleConfiguration lifecycle = new LifecycleConfiguration(4, prestige, Map.of(), Map.of(), Map.of(),
                Map.of(), CompetitionConfiguration.disabled());
        ActiveStageConfiguration prior = new ActiveStageConfiguration(new StageConfigurationSnapshot(REVISION, stages),
                new ProgressionConfigurationSnapshot(REVISION, progression, pins));
        ActiveLifecycleConfiguration active = new ActiveLifecycleConfiguration(prior,
                new LifecycleConfigurationSnapshot(REVISION, lifecycle, pins));
        PlayerPrestigeState prestigeState = new PlayerPrestigeState(player, 0, 0, 0, REVISION,
                new ScopeId("prestige-current"), Optional.empty(), NOW, NOW);
        return new PrestigeAuthorizationService(() -> Optional.of(active),
                ignored -> Optional.of(prestigeState), (ignored, state, configuration) ->
                        new PrestigeProgressContext(player, REVISION, 0, ExactDecimal.ZERO,
                                new ScopeContext(Map.of(MeasurementScope.SINCE_PRESTIGE_START,
                                        state.prestigeScope()))),
                emptyRequirementState(), providers, (ignored, currency) -> ExactDecimal.ZERO,
                (ignored, milestone, key) -> false, ActiveSeasonContext::none,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    private static PrestigeIntent intent() {
        UUID player = UUID.fromString("22222222-2222-2222-2222-222222222222");
        return new PrestigeIntent(new Actor("player", Optional.of(player), "Player"), player, "health-test");
    }

    private static class SimpleProvider implements Provider {
        private final ProviderDescriptor descriptor;
        private final ProviderHealthState health;

        private SimpleProvider(ProviderId id, ProviderHealthState health) {
            descriptor = new ProviderDescriptor(id, "test", "1", "test", List.of(), List.of());
            this.health = health;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(health, "test.health", health.name(), NOW);
        }
    }

    private static final class BoundaryMetricProvider extends SimpleProvider implements MetricProvider {
        private BoundaryMetricProvider(ProviderId id, ProviderHealthState health) {
            super(id, health);
        }

        @Override
        public Collection<MetricDescriptor> metrics() {
            return List.of(new MetricDescriptor(descriptor().id(), METRIC, MetricValueType.COUNT,
                    MetricOperator.compatibleWith(MetricValueType.COUNT), Set.of(MetricReadMode.CURRENT), false,
                    MetricMonotonicity.MONOTONIC, MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Count", "Count",
                    "count", "test"));
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId,
                List<MetricQuery> queries,
                long providerGeneration) {
            return CompletableFuture.completedFuture(queries.stream().collect(java.util.stream.Collectors.toMap(
                    query -> query, query -> MetricSample.available(MetricValue.count(10), providerGeneration, NOW,
                            "test"))));
        }
    }

    private static final class UnhealthyRankAdapter extends SimpleProvider implements RankAdapter {
        private UnhealthyRankAdapter(ProviderId id) {
            super(id, ProviderHealthState.UNAVAILABLE);
        }

        @Override
        public CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            return unavailable();
        }

        @Override
        public CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups) {
            return unavailable();
        }

        @Override
        public CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request) {
            return unavailable();
        }

        private static <T> CompletionStage<Result<T>> unavailable() {
            return CompletableFuture.completedFuture(Result.failure(StructuredError.unavailable("test.unavailable",
                    "unhealthy")));
        }
    }
}
