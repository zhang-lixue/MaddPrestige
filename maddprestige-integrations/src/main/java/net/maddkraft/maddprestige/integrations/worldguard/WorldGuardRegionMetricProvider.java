package net.maddkraft.maddprestige.integrations.worldguard;

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

/** Exact current-location membership in one configured WorldGuard region. */
public final class WorldGuardRegionMetricProvider implements MetricProvider {
    public static final ProviderId ID = new ProviderId("worldguard_region");
    public static final MetricId INSIDE = new MetricId("inside_region");
    public static final String WORLD_ID = "world-id";
    public static final String REGION_ID = "region-id";

    private final WorldGuardRegionAccess access;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final Clock clock;
    private final ProviderDescriptor descriptor;
    private final MetricDescriptor metric;

    public WorldGuardRegionMetricProvider(WorldGuardRegionAccess access, IntegrationTaskScheduler scheduler,
            MutableProviderHealth health, Clock clock, String detectedVersion) {
        this.access = java.util.Objects.requireNonNull(access, "access");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.health = java.util.Objects.requireNonNull(health, "health");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        descriptor = new ProviderDescriptor(ID, "maddprestige", "phase7", detectedVersion,
                List.of(new DependencyDescriptor("WorldGuard", "[7.0.18,7.0.19)",
                        Optional.of(detectedVersion))),
                List.of(new CapabilityDescriptor(ID.value(), "metric", "Read-only exact region membership",
                        Map.of("thread", "server", "mutation", "none"))));
        metric = new MetricDescriptor(ID, INSIDE, MetricValueType.BOOLEAN,
                MetricOperator.compatibleWith(MetricValueType.BOOLEAN), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE,
                Map.of(WORLD_ID, new MetricDimension(WORLD_ID, true, Set.of(), "Exact loaded world UUID"),
                        REGION_ID, new MetricDimension(REGION_ID, true, Set.of(), "Exact WorldGuard region ID")),
                "Inside WorldGuard region", "Whether the online player is inside the exact configured region",
                "boolean", "authoritative for the current block at observation; never mutates regions or worlds");
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return List.of(metric);
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

    private MetricSample sample(UUID playerId, MetricQuery query, long generation, Instant now) {
        if (!health.isUsable()) {
            return unavailable(generation, now, "WorldGuard binding is unavailable");
        }
        if (!INSIDE.equals(query.metricId()) || query.readMode() != MetricReadMode.CURRENT
                || !query.filters().keySet().equals(Set.of(WORLD_ID, REGION_ID))) {
            return unavailable(generation, now, "Unsupported WorldGuard query");
        }
        UUID worldId;
        String regionId = query.filters().get(REGION_ID);
        try {
            worldId = UUID.fromString(query.filters().get(WORLD_ID));
            if (!worldId.toString().equals(query.filters().get(WORLD_ID))
                    || regionId.isBlank() || regionId.length() > 128) {
                return unavailable(generation, now, "Malformed WorldGuard query dimensions");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            return unavailable(generation, now, "Malformed WorldGuard query dimensions");
        }
        try {
            WorldGuardRegionAccess.Observation observation = access.observe(playerId, worldId, regionId);
            return switch (observation.status()) {
                case INSIDE -> MetricSample.available(MetricValue.bool(true), generation, now, "worldguard");
                case OUTSIDE -> MetricSample.available(MetricValue.bool(false), generation, now, "worldguard");
                case UNAVAILABLE -> unavailable(generation, now, observation.detail());
            };
        } catch (RuntimeException exception) {
            return unavailable(generation, now, "WorldGuard read failed");
        }
    }

    private static MetricSample unavailable(long generation, Instant now, String detail) {
        return MetricSample.unavailable(generation, now, "worldguard", detail);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return health.get();
    }
}
