package net.maddkraft.maddprestige.integrations.griefprevention;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.MetricId;
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
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;

/** Read-only GriefPrevention claim-block and owned-claim metrics. */
public final class GriefPreventionMetricProvider implements MetricProvider {
    public static final MetricId REMAINING = new MetricId("remaining_claim_blocks");
    public static final MetricId ACCRUED = new MetricId("accrued_claim_blocks");
    public static final MetricId BONUS = new MetricId("bonus_claim_blocks");
    public static final MetricId OWNED_CLAIMS = new MetricId("owned_claim_count");

    private final GriefPreventionAccess access;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final Clock clock;
    private final ProviderDescriptor descriptor;
    private final List<MetricDescriptor> metrics;

    public GriefPreventionMetricProvider(GriefPreventionAccess access, IntegrationTaskScheduler scheduler,
            MutableProviderHealth health, Clock clock, String detectedVersion) {
        this.access = java.util.Objects.requireNonNull(access, "access");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.health = java.util.Objects.requireNonNull(health, "health");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        descriptor = GriefPreventionProviderDescriptors.descriptor(
                GriefPreventionProviderDescriptors.METRIC, "metric", detectedVersion);
        metrics = List.of(integer(REMAINING, "Remaining claim blocks", "Currently spendable claim blocks"),
                integer(ACCRUED, "Accrued claim blocks", "Play-time accrued claim blocks"),
                integer(BONUS, "Bonus claim blocks", "Administratively granted bonus claim blocks"),
                count(OWNED_CLAIMS, "Owned claims", "Number of claims owned by the player"));
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return metrics;
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        return scheduler.call(() -> readScheduled(playerId, queries, providerGeneration));
    }

    private Map<MetricQuery, MetricSample> readScheduled(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        Instant now = clock.instant();
        LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
        if (!health.isUsable()) {
            queries.forEach(query -> result.put(query, unavailable(providerGeneration, now,
                    "GriefPrevention binding is unavailable")));
            return Map.copyOf(result);
        }
        GriefPreventionAccess.ClaimBlockSnapshot snapshot;
        try {
            snapshot = access.read(playerId);
        } catch (RuntimeException exception) {
            queries.forEach(query -> result.put(query, unavailable(providerGeneration, now,
                    "GriefPrevention read failed")));
            return Map.copyOf(result);
        }
        for (MetricQuery query : queries) {
            MetricValue value = value(query, snapshot);
            result.put(query, value == null
                    ? unavailable(providerGeneration, now, "Unsupported GriefPrevention query")
                    : MetricSample.available(value, providerGeneration, now, "griefprevention"));
        }
        return Map.copyOf(result);
    }

    private static MetricValue value(MetricQuery query, GriefPreventionAccess.ClaimBlockSnapshot snapshot) {
        if (query.readMode() != MetricReadMode.CURRENT || !query.filters().isEmpty()) {
            return null;
        }
        if (REMAINING.equals(query.metricId())) {
            return MetricValue.integer(snapshot.remaining());
        }
        if (ACCRUED.equals(query.metricId())) {
            return MetricValue.integer(snapshot.accrued());
        }
        if (BONUS.equals(query.metricId())) {
            return MetricValue.integer(snapshot.bonus());
        }
        if (OWNED_CLAIMS.equals(query.metricId())) {
            return MetricValue.count(snapshot.ownedClaims());
        }
        return null;
    }

    private static MetricSample unavailable(long generation, Instant now, String detail) {
        return MetricSample.unavailable(generation, now, "griefprevention", detail);
    }

    private static MetricDescriptor integer(MetricId id, String name, String description) {
        return metric(id, MetricValueType.INTEGER, name, description, "claim blocks");
    }

    private static MetricDescriptor count(MetricId id, String name, String description) {
        return metric(id, MetricValueType.COUNT, name, description, "claims");
    }

    private static MetricDescriptor metric(
            MetricId id, MetricValueType type, String name, String description, String unit) {
        return new MetricDescriptor(GriefPreventionProviderDescriptors.METRIC, id, type,
                MetricOperator.compatibleWith(type), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), name, description,
                unit, "authoritative at observation; GriefPrevention may change afterward");
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
