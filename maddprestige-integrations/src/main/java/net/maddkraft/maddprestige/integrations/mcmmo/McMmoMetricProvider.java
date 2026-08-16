package net.maddkraft.maddprestige.integrations.mcmmo;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricDimension;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;

/** Authoritative read-only mcMMO skill-level and power-level metrics. */
public final class McMmoMetricProvider implements MetricProvider {
    public static final ProviderId PROVIDER_ID = new ProviderId("mcmmo");
    public static final MetricId SKILL_LEVEL = new MetricId("skill_level");
    public static final MetricId POWER_LEVEL = new MetricId("power_level");
    private final McMmoExperienceAccess access;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final Clock clock;
    private final ProviderDescriptor descriptor;
    private final List<MetricDescriptor> metrics;

    public McMmoMetricProvider(
            McMmoExperienceAccess access,
            IntegrationTaskScheduler scheduler,
            MutableProviderHealth health,
            Clock clock,
            String implementationVersion) {
        this.access = access;
        this.scheduler = scheduler;
        this.health = health;
        this.clock = clock;
        descriptor = new ProviderDescriptor(PROVIDER_ID, "maddprestige", "phase5", implementationVersion,
                List.of(new DependencyDescriptor("mcMMO", "[2.2,2.3)", Optional.of(implementationVersion))),
                List.of(new CapabilityDescriptor("mcmmo_levels", "metric", "Read-only mcMMO levels",
                        Map.of("thread", "server", "mutation", "none"))));
        metrics = List.of(metric(SKILL_LEVEL, Map.of("skill", new MetricDimension("skill", true, Set.of(),
                        "Official mcMMO skill name")), "mcMMO skill level"),
                metric(POWER_LEVEL, Map.of(), "mcMMO power level"));
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return metrics;
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        return scheduler.call(() -> {
            Instant now = clock.instant();
            LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
            for (MetricQuery query : queries) {
                result.put(query, sample(playerId, query, providerGeneration, now));
            }
            return Map.copyOf(result);
        });
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return health.get();
    }

    private MetricSample sample(UUID playerId, MetricQuery query, long generation, Instant now) {
        if (!health.isUsable()) {
            return unavailable(generation, now, "mcMMO dependency is unavailable");
        }
        if (query.readMode() != MetricReadMode.CURRENT) {
            return unavailable(generation, now, "mcMMO levels support CURRENT reads only");
        }
        if (POWER_LEVEL.equals(query.metricId()) && query.filters().isEmpty()) {
            return MetricSample.available(MetricValue.integer(access.powerLevel(playerId)), generation, now, "mcmmo");
        }
        String skill = query.filters().get("skill");
        if (SKILL_LEVEL.equals(query.metricId()) && query.filters().size() == 1 && skill != null
                && access.validSkill(skill)) {
            return MetricSample.available(MetricValue.integer(access.level(playerId, skill)), generation, now,
                    "mcmmo:" + skill);
        }
        return unavailable(generation, now, "Unknown metric or invalid/missing mcMMO skill dimension");
    }

    private static MetricSample unavailable(long generation, Instant now, String detail) {
        return MetricSample.unavailable(generation, now, "mcmmo", detail);
    }

    private static MetricDescriptor metric(MetricId id, Map<String, MetricDimension> dimensions, String display) {
        return new MetricDescriptor(PROVIDER_ID, id, MetricValueType.INTEGER,
                MetricOperator.compatibleWith(MetricValueType.INTEGER), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, dimensions, display,
                "Read-only value from the official mcMMO ExperienceAPI", "levels",
                "authoritative mcMMO state at observation");
    }
}
