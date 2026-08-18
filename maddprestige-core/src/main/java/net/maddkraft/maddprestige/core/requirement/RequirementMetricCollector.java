package net.maddkraft.maddprestige.core.requirement;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

public final class RequirementMetricCollector {
    private final ProviderRegistry providers;
    private final Clock clock;

    public RequirementMetricCollector(ProviderRegistry providers, Clock clock) {
        this.providers = java.util.Objects.requireNonNull(providers, "provider registry");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<Map<RequirementId, MetricSample>> collect(
            UUID playerId,
            RequirementNode tree,
            Map<ProviderId, Long> pinnedGenerations) {
        LinkedHashMap<ProviderId, List<LeafQuery>> grouped = new LinkedHashMap<>();
        collectLeaves(tree, grouped);
        LinkedHashMap<ProviderId, CompletableFuture<Map<MetricQuery, MetricSample>>> reads = new LinkedHashMap<>();
        LinkedHashMap<RequirementId, MetricSample> immediate = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            ProviderId providerId = entry.getKey();
            providers.refreshHealth(providerId);
            var snapshot = providers.find(providerId);
            var provider = providers.provider(providerId);
            Long pinnedGeneration = pinnedGenerations.get(providerId);
            long generation = pinnedGeneration == null
                    ? snapshot.map(value -> value.generation()).orElse(1L) : pinnedGeneration;
            if (pinnedGeneration == null || snapshot.isEmpty()
                    || provider.filter(MetricProvider.class::isInstance).isEmpty()
                    || snapshot.orElseThrow().generation() != generation
                    || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                    || !healthy(snapshot.orElseThrow().health().state())) {
                entry.getValue().forEach(leaf -> immediate.put(leaf.id, MetricSample.unavailable(generation,
                        clock.instant(), providerId.value(), "Metric provider is unavailable or stale")));
                continue;
            }
            List<MetricQuery> queries = entry.getValue().stream().map(LeafQuery::query).distinct().toList();
            try {
                CompletionStage<Map<MetricQuery, MetricSample>> read =
                        ((MetricProvider) provider.orElseThrow()).read(playerId, queries, generation);
                if (read == null) {
                    throw new IllegalStateException("Metric provider returned a null completion stage");
                }
                reads.put(providerId, read.toCompletableFuture());
            } catch (RuntimeException exception) {
                reads.put(providerId, CompletableFuture.failedFuture(exception));
            }
        }
        CompletableFuture<?>[] all = reads.values().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all).handle((ignored, failure) -> {
            LinkedHashMap<RequirementId, MetricSample> result = new LinkedHashMap<>(immediate);
            for (var entry : grouped.entrySet()) {
                CompletableFuture<Map<MetricQuery, MetricSample>> read = reads.get(entry.getKey());
                if (read == null) {
                    continue;
                }
                long generation = pinnedGenerations.getOrDefault(entry.getKey(),
                        providers.find(entry.getKey()).map(value -> value.generation()).orElse(1L));
                if (read.isCompletedExceptionally()) {
                    entry.getValue().forEach(leaf -> result.put(leaf.id, MetricSample.unavailable(generation,
                            clock.instant(), entry.getKey().value(), "Metric batch read failed")));
                    continue;
                }
                Map<MetricQuery, MetricSample> samples = read.join();
                entry.getValue().forEach(leaf -> result.put(leaf.id,
                        samples.getOrDefault(leaf.query, MetricSample.unavailable(generation, clock.instant(),
                                entry.getKey().value(), "Provider omitted a requested metric sample"))));
            }
            return Map.copyOf(result);
        });
    }

    private static void collectLeaves(
            RequirementNode node,
            Map<ProviderId, List<LeafQuery>> grouped) {
        if (node instanceof RequirementLeaf leaf) {
            RequirementDefinition definition = leaf.definition();
            grouped.computeIfAbsent(definition.providerId(), ignored -> new ArrayList<>()).add(new LeafQuery(
                    definition.id(), new MetricQuery(definition.metricId(), definition.scope().readMode(),
                    definition.filters())));
            return;
        }
        ((RequirementGroup) node).children().forEach(child -> collectLeaves(child.node(), grouped));
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private record LeafQuery(RequirementId id, MetricQuery query) {
    }
}
