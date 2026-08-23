package org.example.maddprestige;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.maddprestige.api.provider.RequirementProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExampleProgressProviderPlugin extends JavaPlugin implements ProviderDeclaration {
    private static final MetricId VISITS = new MetricId("visits");
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicReference<ProviderRegistrationHandle> registration = new AtomicReference<>();

    @Override
    public void onEnable() {
        ready.set(true);
        getServer().getServicesManager().register(
                ProviderDeclaration.class, this, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        ready.set(false);
        getServer().getServicesManager().unregister(ProviderDeclaration.class, this);
        Optional.ofNullable(registration.getAndSet(null)).ifPresent(handle -> handle.unregister());
    }

    @Override
    public ProviderMetadata metadata(ProviderCallContext context) {
        return new ProviderMetadata("visits", "example.provider.visits", "1.0.0", List.of(
                new ProviderMetricDefinition(VISITS, MetricValueType.COUNT,
                        Set.of(MetricOperator.GREATER_OR_EQUAL), Set.of(MetricReadMode.CURRENT), false,
                        MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                        "example.metric.visits", "example.metric.visits.description", "visits", "authoritative")));
    }

    @Override
    public RequirementProvider requirements() {
        return this::read;
    }

    @Override
    public void registered(ProviderRegistrationHandle handle) {
        registration.set(handle);
    }

    @Override
    public void unregistered() {
        registration.set(null);
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> read(
            ProviderCallContext context,
            java.util.UUID playerId,
            List<ProviderMetricRequest> queries) {
        Instant observedAt = Instant.now();
        boolean unavailable = !ready.get() || context.cancellationRequested() || context.expired(observedAt);
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> results = new LinkedHashMap<>();
        for (ProviderMetricRequest query : queries) {
            ProviderMetricResult result = unavailable
                    ? ProviderMetricResult.unavailable(observedAt, "example.provider.unavailable",
                            "example.provider.unavailable", Map.of())
                    : ProviderMetricResult.available(MetricValue.count(1), observedAt);
            results.put(query, result);
        }
        return CompletableFuture.completedFuture(Map.copyOf(results));
    }
}
