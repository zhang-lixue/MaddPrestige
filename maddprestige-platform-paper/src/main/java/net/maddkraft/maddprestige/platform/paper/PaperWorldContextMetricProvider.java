package net.maddkraft.maddprestige.platform.paper;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.core.compatibility.ProviderMetadataVersions;
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
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Generic Paper-only current-world predicates with no world lifecycle ownership. */
public final class PaperWorldContextMetricProvider implements MetricProvider {
    public static final ProviderId ID = new ProviderId("paper_world_context");
    public static final MetricId WORLD_NAME = new MetricId("world_name");
    public static final MetricId WORLD_UUID = new MetricId("world_uuid");
    public static final MetricId ENVIRONMENT = new MetricId("environment");
    public static final MetricId IN_WORLD_SET = new MetricId("in_world_set");
    public static final String WORLD_IDS = "world-ids";

    private final PaperTaskScheduler scheduler;
    private final Function<UUID, Player> players;
    private final Supplier<ProviderHealth> health;
    private final Clock clock;
    private final List<MetricDescriptor> metrics;
    private final ProviderDescriptor descriptor;

    public PaperWorldContextMetricProvider(PaperTaskScheduler scheduler, Function<UUID, Player> players,
            Supplier<ProviderHealth> health, Clock clock) {
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.health = java.util.Objects.requireNonNull(health, "health");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        metrics = List.of(metric(WORLD_NAME, MetricValueType.STRING, Map.of(), "World name"),
                metric(WORLD_UUID, MetricValueType.STRING, Map.of(), "World UUID"),
                metric(ENVIRONMENT, MetricValueType.ENUM, Map.of(), "World environment"),
                metric(IN_WORLD_SET, MetricValueType.BOOLEAN,
                        Map.of(WORLD_IDS, new MetricDimension(WORLD_IDS, true, Set.of(),
                                "Comma-separated canonical world UUIDs")), "In configured world set"));
        descriptor = new ProviderDescriptor(ID, "maddprestige", ProviderMetadataVersions.STABLE_API,
                ProviderMetadataVersions.implementationVersion(PaperWorldContextMetricProvider.class), List.of(),
                metrics.stream().map(metric -> new CapabilityDescriptor(metric.metricId().value(), "metric",
                        metric.description(), Map.of("thread", "server", "mutation", "none"))).toList());
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return metrics;
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        return scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD,
                () -> readScheduled(playerId, queries, providerGeneration));
    }

    private Map<MetricQuery, MetricSample> readScheduled(
            UUID playerId, List<MetricQuery> queries, long generation) {
        Instant now = clock.instant();
        LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
        if (!usable(health.get())) {
            queries.forEach(query -> result.put(query, unavailable(generation, now,
                    "Paper world provider is unavailable")));
            return Map.copyOf(result);
        }
        Player player = players.apply(playerId);
        if (player == null || !player.isOnline()) {
            queries.forEach(query -> result.put(query, unavailable(generation, now,
                    "Paper world predicates require an online player")));
            return Map.copyOf(result);
        }
        World world = player.getWorld();
        for (MetricQuery query : queries) {
            try {
                MetricValue value = value(query, world);
                result.put(query, value == null ? unavailable(generation, now, "Unsupported Paper world query")
                        : MetricSample.available(value, generation, now, "paper-world-context"));
            } catch (IllegalArgumentException exception) {
                result.put(query, unavailable(generation, now, exception.getMessage()));
            }
        }
        return Map.copyOf(result);
    }

    private static MetricValue value(MetricQuery query, World world) {
        if (query.readMode() != MetricReadMode.CURRENT) {
            return null;
        }
        if (WORLD_NAME.equals(query.metricId()) && query.filters().isEmpty()) {
            return MetricValue.parse(MetricValueType.STRING, world.getName());
        }
        if (WORLD_UUID.equals(query.metricId()) && query.filters().isEmpty()) {
            return MetricValue.parse(MetricValueType.STRING, world.getUID().toString());
        }
        if (ENVIRONMENT.equals(query.metricId()) && query.filters().isEmpty()) {
            return MetricValue.parse(MetricValueType.ENUM, world.getEnvironment().name().toLowerCase(Locale.ROOT));
        }
        if (IN_WORLD_SET.equals(query.metricId()) && query.filters().keySet().equals(Set.of(WORLD_IDS))) {
            return MetricValue.bool(worldIds(query.filters().get(WORLD_IDS)).contains(world.getUID()));
        }
        return null;
    }

    private static Set<UUID> worldIds(String value) {
        if (value == null || value.isBlank() || value.length() > 1183) {
            throw new IllegalArgumentException("world-ids must contain 1 to 32 canonical UUIDs");
        }
        String[] entries = value.split(",", -1);
        if (entries.length > 32) {
            throw new IllegalArgumentException("world-ids accepts at most 32 UUIDs");
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (String entry : entries) {
            UUID parsed = UUID.fromString(entry);
            if (!parsed.toString().equals(entry) || !result.add(parsed)) {
                throw new IllegalArgumentException("world-ids must be unique canonical UUIDs");
            }
        }
        return Set.copyOf(result);
    }

    private static boolean usable(ProviderHealth health) {
        return health.state() == net.maddkraft.maddprestige.api.provider.ProviderHealthState.AVAILABLE
                || health.state() == net.maddkraft.maddprestige.api.provider.ProviderHealthState.ACTIVE;
    }

    private static MetricDescriptor metric(MetricId id, MetricValueType type,
            Map<String, MetricDimension> dimensions, String name) {
        return new MetricDescriptor(ID, id, type, MetricOperator.compatibleWith(type),
                Set.of(MetricReadMode.CURRENT), false, MetricMonotonicity.NON_MONOTONIC,
                MetricResetPolicy.NOT_APPLICABLE, dimensions, name,
                "Read-only current world context from Paper", type == MetricValueType.BOOLEAN ? "boolean" : "text",
                "authoritative at observation; player world may change immediately afterward");
    }

    private static MetricSample unavailable(long generation, Instant now, String detail) {
        return MetricSample.unavailable(generation, now, "paper-world-context", detail);
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
