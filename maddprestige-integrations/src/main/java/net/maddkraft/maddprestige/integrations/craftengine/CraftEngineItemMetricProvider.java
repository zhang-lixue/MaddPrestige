package net.maddkraft.maddprestige.integrations.craftengine;

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
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Exact CraftEngine item count in online-player storage contents only. */
public final class CraftEngineItemMetricProvider implements MetricProvider {
    public static final MetricId COUNT = new MetricId("item_count");

    private final Server server;
    private final CraftEngineItemAccess access;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final Clock clock;
    private final ProviderDescriptor descriptor;
    private final MetricDescriptor metric;

    public CraftEngineItemMetricProvider(Server server, CraftEngineItemAccess access,
            IntegrationTaskScheduler scheduler, MutableProviderHealth health, Clock clock, String detectedVersion) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.access = java.util.Objects.requireNonNull(access, "access");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.health = java.util.Objects.requireNonNull(health, "health");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        descriptor = CraftEngineProviderDescriptors.descriptor(
                CraftEngineProviderDescriptors.METRIC, "metric", detectedVersion);
        metric = new MetricDescriptor(CraftEngineProviderDescriptors.METRIC, COUNT, MetricValueType.COUNT,
                MetricOperator.compatibleWith(MetricValueType.COUNT), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE,
                Map.of(CraftEngineProviderDescriptors.ITEM_ID,
                        new MetricDimension(CraftEngineProviderDescriptors.ITEM_ID, true, Set.of(),
                                "Fully namespaced CraftEngine custom-item ID")),
                "CraftEngine item count", "Exact matching custom items in player storage contents", "items",
                "authoritative at observation; armor, off-hand, cursor, ender chest, and nested storage are excluded");
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
            return unavailable(generation, now, "CraftEngine binding is unavailable");
        }
        String itemId;
        try {
            if (!COUNT.equals(query.metricId()) || query.readMode() != MetricReadMode.CURRENT
                    || !query.filters().keySet().equals(Set.of(CraftEngineProviderDescriptors.ITEM_ID))) {
                throw new IllegalArgumentException("Unsupported CraftEngine query");
            }
            itemId = CraftEngineProviderDescriptors.requireItemId(
                    query.filters().get(CraftEngineProviderDescriptors.ITEM_ID));
        } catch (IllegalArgumentException exception) {
            return unavailable(generation, now, exception.getMessage());
        }
        Player player = server.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return unavailable(generation, now, "CraftEngine inventory metric requires an online player");
        }
        try {
            if (!access.exists(itemId)) {
                return unavailable(generation, now, "CraftEngine item is not currently loaded");
            }
            long count = count(access, player.getInventory().getStorageContents(), itemId);
            return MetricSample.available(MetricValue.count(count), generation, now, "craftengine");
        } catch (ArithmeticException exception) {
            return unavailable(generation, now, "CraftEngine item count overflowed");
        } catch (RuntimeException exception) {
            return unavailable(generation, now, "CraftEngine item identity read failed");
        }
    }

    public static long count(CraftEngineItemAccess access, ItemStack[] contents, String itemId) {
        long total = 0L;
        for (ItemStack stack : contents) {
            if (stack != null && access.identify(stack).filter(itemId::equals).isPresent()) {
                total = Math.addExact(total, stack.getAmount());
            }
        }
        return total;
    }

    private static MetricSample unavailable(long generation, Instant now, String detail) {
        return MetricSample.unavailable(generation, now, "craftengine", detail);
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
