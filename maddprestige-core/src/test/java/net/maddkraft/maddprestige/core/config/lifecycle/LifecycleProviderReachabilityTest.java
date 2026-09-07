package net.maddkraft.maddprestige.core.config.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
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
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.Test;

class LifecycleProviderReachabilityTest {
    private static final ProviderId ACTIVE_PROVIDER = new ProviderId("lifecycle_active_metric");
    private static final ProviderId DORMANT_PROVIDER = new ProviderId("lifecycle_dormant_metric");
    private static final RequirementId ACTIVE = new RequirementId("lifecycle_active_requirement");
    private static final RequirementId DORMANT = new RequirementId("lifecycle_dormant_requirement");
    private static final MetricId METRIC = new MetricId("count");
    private static final StageId ORIGIN = new StageId("origin");
    private static final StageId SUMMIT = new StageId("summit");

    @Test
    void prestigeReachabilityPinsDormantProgressionProviderAndLeavesTrueDormancyAlone() {
        MutableMetricProvider provider = new MutableMetricProvider(ACTIVE_PROVIDER);
        ProviderRegistry providers = new ProviderRegistry();
        var registration = providers.register("test", provider);
        providers.activate(registration);

        LifecycleProviderValidation validation = new LifecycleConfigurationValidator().validateProviders(
                lifecycle(), progression(), stages(), providers, Map.of());

        assertFalse(validation.report().hasErrors(), validation.report().toString());
        assertTrue(validation.providerGenerations().get(ACTIVE_PROVIDER) == registration.generation());
        assertFalse(validation.providerGenerations().containsKey(DORMANT_PROVIDER));
    }

    @Test
    void absentOrUnhealthyReachableProviderFailsWhileUnreferencedProviderDoesNot() {
        LifecycleProviderValidation absent = new LifecycleConfigurationValidator().validateProviders(
                lifecycle(), progression(), stages(), new ProviderRegistry(), Map.of());
        assertTrue(absent.report().hasErrors());

        MutableMetricProvider provider = new MutableMetricProvider(ACTIVE_PROVIDER);
        provider.health = ProviderHealthState.UNAVAILABLE;
        ProviderRegistry providers = new ProviderRegistry();
        var registration = providers.register("test", provider);
        providers.activate(registration);
        LifecycleProviderValidation unhealthy = new LifecycleConfigurationValidator().validateProviders(
                lifecycle(), progression(), stages(), providers, Map.of());
        assertTrue(unhealthy.report().hasErrors());
        assertFalse(unhealthy.providerGenerations().containsKey(ACTIVE_PROVIDER));
    }

    private static LifecycleConfiguration lifecycle() {
        PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(SUMMIT), ORIGIN, 1, 1,
                PrestigeLimit.unlimited(), Duration.ZERO, Optional.of(ACTIVE), List.of(), List.of(),
                Optional.empty(), Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
        return new LifecycleConfiguration(4, prestige, Map.of(), Map.of(), Map.of(), Map.of(),
                CompetitionConfiguration.disabled());
    }

    private static ProgressionConfiguration progression() {
        RequirementDefinition active = definition(ACTIVE, ACTIVE_PROVIDER);
        RequirementDefinition dormant = definition(DORMANT, DORMANT_PROVIDER);
        return new ProgressionConfiguration(3, 16, Map.of(ACTIVE, active, DORMANT, dormant),
                Map.of(ACTIVE, new RequirementLeaf(active), DORMANT, new RequirementLeaf(dormant)),
                Map.of(), Map.of(), CommandActionPolicy.safeDefaults());
    }

    private static RequirementDefinition definition(RequirementId id, ProviderId provider) {
        return RequirementDefinition.create(id, provider, METRIC, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.count(1)), MeasurementScope.ABSOLUTE, CompletionMode.LIVE,
                ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false);
    }

    private static StageConfiguration stages() {
        StageDefinition origin = new StageDefinition(ORIGIN, true, "Origin", Map.of(), StageProjection.none());
        StageDefinition summit = new StageDefinition(SUMMIT, true, "Summit", Map.of(), StageProjection.none());
        return new StageConfiguration(2, true, Map.of(ORIGIN, origin, SUMMIT, summit), List.of(ORIGIN, SUMMIT),
                Optional.of(ORIGIN), ReconciliationPolicy.WARN_ONLY);
    }

    private static final class MutableMetricProvider implements MetricProvider {
        private final ProviderId id;
        private ProviderHealthState health = ProviderHealthState.AVAILABLE;

        private MutableMetricProvider(ProviderId id) {
            this.id = id;
        }

        @Override
        public Collection<MetricDescriptor> metrics() {
            return List.of(new MetricDescriptor(id, METRIC, MetricValueType.COUNT,
                    MetricOperator.compatibleWith(MetricValueType.COUNT), Set.of(MetricReadMode.CURRENT), false,
                    MetricMonotonicity.MONOTONIC, MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Count", "Count",
                    "count", "test"));
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId,
                List<MetricQuery> queries,
                long providerGeneration) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor(id, "test", "1", "test", List.of(), List.of());
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(health, "test.health", health.name(), Instant.EPOCH);
        }
    }
}
