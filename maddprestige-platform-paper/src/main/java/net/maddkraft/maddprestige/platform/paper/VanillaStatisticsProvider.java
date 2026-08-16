package net.maddkraft.maddprestige.platform.paper;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class VanillaStatisticsProvider implements MetricProvider {
    private static final Set<String> TICK_STATISTICS = Set.of(
            "PLAY_ONE_MINUTE", "TOTAL_WORLD_TIME", "TIME_SINCE_DEATH", "TIME_SINCE_REST",
            "SNEAK_TIME");
    private final ProviderId providerId;
    private final String ownerIdentity;
    private final PaperTaskScheduler scheduler;
    private final Function<UUID, Player> players;
    private final Supplier<ProviderHealth> health;
    private final Runnable threadGuard;
    private final Map<MetricId, SupportedStatistic> statistics;

    public VanillaStatisticsProvider(
            ProviderId providerId,
            String ownerIdentity,
            PaperTaskScheduler scheduler,
            Function<UUID, Player> players,
            Supplier<ProviderHealth> health) {
        this(providerId, ownerIdentity, scheduler, players, health, new PaperStatisticDimensionCatalog(),
                () -> PaperThreadGuard.requireServerThread("Vanilla statistic batch read"));
    }

    VanillaStatisticsProvider(
            ProviderId providerId,
            String ownerIdentity,
            PaperTaskScheduler scheduler,
            Function<UUID, Player> players,
            Supplier<ProviderHealth> health,
            StatisticDimensionCatalog dimensions,
            Runnable threadGuard) {
        this.providerId = Objects.requireNonNull(providerId, "provider ID");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "player resolver");
        this.health = Objects.requireNonNull(health, "health supplier");
        this.threadGuard = Objects.requireNonNull(threadGuard, "thread guard");
        Objects.requireNonNull(dimensions, "statistic dimensions");
        LinkedHashMap<MetricId, SupportedStatistic> discovered = new LinkedHashMap<>();
        for (Statistic statistic : Statistic.values()) {
            MetricId id = new MetricId(statistic.name().toLowerCase(Locale.ROOT));
            discovered.put(id, new SupportedStatistic(statistic, descriptor(providerId, id, statistic, dimensions)));
        }
        this.statistics = Map.copyOf(discovered);
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return statistics.values().stream().map(SupportedStatistic::descriptor).toList();
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
        return scheduler.submit(ExecutionThread.PAPER_SERVER_THREAD,
                () -> readOnServerThread(playerId, queries, providerGeneration));
    }

    @Override
    public ProviderDescriptor descriptor() {
        List<CapabilityDescriptor> capabilities = statistics.entrySet().stream()
                .map(entry -> new CapabilityDescriptor(entry.getKey().value(), "vanilla-statistic",
                        entry.getValue().descriptor.description(), Map.of(
                                "value-type", entry.getValue().descriptor.valueType().name(),
                                "statistic", entry.getValue().statistic.name())))
                .toList();
        return new ProviderDescriptor(providerId, ownerIdentity, "phase3-foundation", "paper-api", List.of(),
                capabilities);
    }

    @Override
    public ProviderHealth health() {
        return health.get();
    }

    private Map<MetricQuery, MetricSample> readOnServerThread(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        threadGuard.run();
        LinkedHashMap<MetricQuery, MetricSample> samples = new LinkedHashMap<>();
        Player player = players.apply(playerId);
        Instant now = Instant.now();
        if (player == null) {
            queries.forEach(query -> samples.put(query, MetricSample.unavailable(providerGeneration, now,
                    "Paper Player statistics",
                    "Player statistic source is unavailable; this provider currently requires an online Player")));
            return Map.copyOf(samples);
        }
        for (MetricQuery query : queries) {
            SupportedStatistic supported = statistics.get(query.metricId());
            if (supported == null || !supported.descriptor.supportedReads().contains(query.readMode())) {
                samples.put(query, MetricSample.unavailable(providerGeneration, now, "Paper Player statistics",
                        "Statistic or read mode is unsupported"));
                continue;
            }
            try {
                int raw = readStatistic(player, supported.statistic, query.filters());
                MetricValue value = supported.descriptor.valueType() == MetricValueType.DURATION
                        ? MetricValue.duration(Duration.ofMillis(Math.multiplyExact((long) raw, 50L)))
                        : MetricValue.count(raw);
                samples.put(query, MetricSample.available(value, providerGeneration, now,
                        "Paper Player#getStatistic(" + supported.statistic.name() + ")"));
            } catch (RuntimeException exception) {
                samples.put(query, MetricSample.unavailable(providerGeneration, now, "Paper Player statistics",
                        "Statistic read failed: " + exception.getMessage()));
            }
        }
        return Map.copyOf(samples);
    }

    private static int readStatistic(Player player, Statistic statistic, Map<String, String> filters) {
        return switch (statistic.getType()) {
            case UNTYPED -> {
                requireNoFilters(filters);
                yield player.getStatistic(statistic);
            }
            case BLOCK, ITEM -> {
                requireExactFilter(filters, "material");
                Material material = Material.valueOf(filters.get("material").toUpperCase(Locale.ROOT));
                if (statistic.getType() == Statistic.Type.BLOCK && !material.isBlock()) {
                    throw new IllegalArgumentException("Material is not a block: " + material);
                }
                if (statistic.getType() == Statistic.Type.ITEM && !material.isItem()) {
                    throw new IllegalArgumentException("Material is not an item: " + material);
                }
                yield player.getStatistic(statistic, material);
            }
            case ENTITY -> {
                requireExactFilter(filters, "entity");
                EntityType entity = EntityType.valueOf(filters.get("entity").toUpperCase(Locale.ROOT));
                yield player.getStatistic(statistic, entity);
            }
        };
    }

    private static MetricDescriptor descriptor(
            ProviderId providerId,
            MetricId id,
            Statistic statistic,
            StatisticDimensionCatalog catalog) {
        boolean duration = TICK_STATISTICS.contains(statistic.name());
        boolean resettable = statistic.name().startsWith("TIME_SINCE_");
        Map<String, MetricDimension> dimensions = switch (statistic.getType()) {
            case UNTYPED -> Map.of();
            case BLOCK -> Map.of("material", new MetricDimension("material", true,
                    catalog.blockMaterials(),
                    "Bukkit block material"));
            case ITEM -> Map.of("material", new MetricDimension("material", true,
                    catalog.itemMaterials(),
                    "Bukkit item material"));
            case ENTITY -> Map.of("entity", new MetricDimension("entity", true,
                    catalog.entityTypes(), "Bukkit entity type"));
        };
        MetricValueType type = duration ? MetricValueType.DURATION : MetricValueType.COUNT;
        return new MetricDescriptor(providerId, id, type, MetricOperator.compatibleWith(type),
                EnumSet.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), !resettable,
                resettable ? MetricMonotonicity.NON_MONOTONIC : MetricMonotonicity.MONOTONIC,
                resettable ? MetricResetPolicy.NOT_APPLICABLE : MetricResetPolicy.FAIL_RECONCILIATION,
                dimensions, title(statistic.name()), "Authoritative statistic exposed by the supported Bukkit API",
                duration ? "duration" : "count", "Paper/Bukkit persisted player statistic");
    }

    private static void requireNoFilters(Map<String, String> filters) {
        if (!filters.isEmpty()) {
            throw new IllegalArgumentException("Untyped statistic does not accept filters");
        }
    }

    private static void requireExactFilter(Map<String, String> filters, String name) {
        if (filters.size() != 1 || !filters.containsKey(name)) {
            throw new IllegalArgumentException("Statistic requires exactly the " + name + " filter");
        }
    }

    private static String title(String value) {
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private record SupportedStatistic(Statistic statistic, MetricDescriptor descriptor) {
    }
}
