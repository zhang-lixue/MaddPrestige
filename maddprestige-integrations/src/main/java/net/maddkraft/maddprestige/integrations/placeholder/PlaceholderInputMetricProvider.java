package net.maddkraft.maddprestige.integrations.placeholder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.compatibility.ProviderMetadataVersions;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
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
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfiguration.PlaceholderInput;

/** Typed, bounded, freshness-checked input cache; requirement reads never invoke PlaceholderAPI. */
public final class PlaceholderInputMetricProvider implements MetricProvider {
    public static final ProviderId PROVIDER_ID = new ProviderId("placeholder_input");
    private final Map<MetricId, InputDefinition> definitions;
    private final PlaceholderResolver resolver;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final ProviderRegistrationGate registrationGate;
    private final Clock clock;
    private final int maximumEntries;
    private final Map<CacheKey, CachedValue> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final ProviderDescriptor descriptor;

    public PlaceholderInputMetricProvider(
            Map<String, PlaceholderInput> configured,
            PlaceholderResolver resolver,
            IntegrationTaskScheduler scheduler,
            MutableProviderHealth health,
            ProviderRegistrationGate registrationGate,
            Clock clock,
            int maximumEntries,
            String implementationVersion) {
        if (maximumEntries < 1 || maximumEntries > 100_000) {
            throw new IllegalArgumentException("Placeholder input cache bound is outside the safe domain");
        }
        LinkedHashMap<MetricId, InputDefinition> compiled = new LinkedHashMap<>();
        configured.forEach((id, input) -> compiled.put(new MetricId(id), new InputDefinition(input)));
        this.definitions = Map.copyOf(compiled);
        this.resolver = java.util.Objects.requireNonNull(resolver, "placeholder resolver");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.health = java.util.Objects.requireNonNull(health, "provider health");
        this.registrationGate = java.util.Objects.requireNonNull(registrationGate, "registration gate");
        if (!registrationGate.controls(PROVIDER_ID, health)) {
            throw new IllegalArgumentException("Placeholder provider requires its own health-bound registration gate");
        }
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.maximumEntries = maximumEntries;
        descriptor = new ProviderDescriptor(PROVIDER_ID, "maddprestige", ProviderMetadataVersions.STABLE_API, implementationVersion,
                List.of(new DependencyDescriptor("PlaceholderAPI", "[2.12,3)",
                        Optional.of(implementationVersion))),
                List.of(new CapabilityDescriptor("placeholder_inputs", "metric",
                        "Scheduled typed PlaceholderAPI input snapshots",
                        Map.of("read-path", "cache-only", "maximum-entries", Integer.toString(maximumEntries)))));
    }

    public CompletionStage<Integer> refresh(UUID playerId) {
        return scheduler.call(() -> {
            if (!registrationGate.allowsUse()) {
                return 0;
            }
            int refreshed = 0;
            Instant observedAt = clock.instant();
            for (Map.Entry<MetricId, InputDefinition> entry : definitions.entrySet()) {
                if (!registrationGate.allowsUse()) {
                    return refreshed;
                }
                InputDefinition definition = entry.getValue();
                String raw = resolver.resolve(playerId, definition.placeholder());
                if (!registrationGate.allowsUse()) {
                    return refreshed;
                }
                CachedValue value = parse(raw, definition, observedAt);
                synchronized (cache) {
                    cache.put(new CacheKey(playerId, entry.getKey()), value);
                    while (cache.size() > maximumEntries) {
                        cache.remove(cache.keySet().iterator().next());
                    }
                }
                refreshed++;
            }
            return refreshed;
        });
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return definitions.entrySet().stream().map(entry -> descriptor(entry.getKey(), entry.getValue())).toList();
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        Instant now = clock.instant();
        LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
        for (MetricQuery query : queries) {
            result.put(query, sample(playerId, query, providerGeneration, now));
        }
        return java.util.concurrent.CompletableFuture.completedFuture(Map.copyOf(result));
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
        InputDefinition definition = definitions.get(query.metricId());
        if (!health.isUsable() || definition == null
                || query.readMode() != MetricReadMode.CURRENT || !query.filters().isEmpty()) {
            return unavailable(generation, now, "Provider unavailable or placeholder input query unsupported");
        }
        CachedValue value;
        synchronized (cache) {
            value = cache.get(new CacheKey(playerId, query.metricId()));
        }
        if (value == null) {
            return unavailable(generation, now, "Placeholder input has not been sampled");
        }
        if (Duration.between(value.observedAt(), now).compareTo(definition.maximumAge()) > 0) {
            return unavailable(generation, now, "Placeholder input sample is stale");
        }
        if (value.value().isEmpty()) {
            return unavailable(generation, now, value.detail());
        }
        return MetricSample.available(value.value().orElseThrow(), generation, value.observedAt(),
                "placeholderapi:scheduled-sample");
    }

    private CachedValue parse(String raw, InputDefinition definition, Instant observedAt) {
        if (raw == null || raw.equals(definition.placeholder())) {
            return new CachedValue(Optional.empty(), observedAt, "Placeholder is missing or unresolved");
        }
        try {
            return new CachedValue(Optional.of(MetricValue.parse(definition.valueType(), raw.trim())), observedAt,
                    "available");
        } catch (IllegalArgumentException exception) {
            return new CachedValue(Optional.empty(), observedAt,
                    "Placeholder value does not match configured " + definition.valueType());
        }
    }

    private static MetricSample unavailable(long generation, Instant now, String detail) {
        return MetricSample.unavailable(generation, now, "placeholderapi", detail);
    }

    private static MetricDescriptor descriptor(MetricId id, InputDefinition definition) {
        return new MetricDescriptor(PROVIDER_ID, id, definition.valueType(),
                MetricOperator.compatibleWith(definition.valueType()), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), id.value(),
                "Typed scheduled sample of " + definition.placeholder(), "",
                "eventually consistent; unavailable when missing, invalid, or stale");
    }

    private record InputDefinition(String placeholder, MetricValueType valueType, Duration maximumAge) {
        private InputDefinition(PlaceholderInput input) {
            this(input.placeholder(), input.valueType(), input.maximumAge());
        }
    }

    private record CacheKey(UUID playerId, MetricId metricId) {
    }

    private record CachedValue(Optional<MetricValue> value, Instant observedAt, String detail) {
    }
}
