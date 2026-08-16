package net.maddkraft.maddprestige.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.RequirementTreeValidator;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VanillaStatisticsProviderTest {
    @Test
    @DisplayName("[A15][A65] Real Bukkit Statistic capabilities are discovered with actual dimension types")
    void discoversActualBukkitStatistics() {
        VanillaStatisticsProvider provider = provider(playerReturning(42));
        assertEquals(Statistic.values().length, provider.metrics().size());
        for (Statistic statistic : Statistic.values()) {
            assertTrue(provider.metrics().stream().anyMatch(metric ->
                    metric.metricId().value().equals(statistic.name().toLowerCase(java.util.Locale.ROOT))));
        }
        assertTrue(provider.metrics().stream().filter(metric -> !metric.dimensions().isEmpty())
                .allMatch(metric -> metric.dimensions().values().stream().allMatch(dimension ->
                        dimension.required() && !dimension.allowedValues().isEmpty())));
        assertTrue(provider.metrics().stream().filter(metric -> metric.metricId().value().equals("mine_block"))
                .flatMap(metric -> metric.dimensions().values().stream())
                .allMatch(dimension -> dimension.allowedValues().contains("stone")
                        && !dimension.allowedValues().contains("stick")));
        assertTrue(provider.metrics().stream().filter(metric -> metric.metricId().value().equals("use_item"))
                .flatMap(metric -> metric.dimensions().values().stream())
                .allMatch(dimension -> dimension.allowedValues().contains("stick")
                        && !dimension.allowedValues().contains("stone_only_block")));
    }

    @Test
    @DisplayName("[A15] Representative authoritative untyped statistic reads through real Player API signature")
    void readsRepresentativeStatistic() {
        Statistic statistic = java.util.Arrays.stream(Statistic.values())
                .filter(value -> value.getType() == Statistic.Type.UNTYPED)
                .filter(value -> !value.name().contains("TIME") && !value.name().equals("PLAY_ONE_MINUTE"))
                .findFirst().orElseThrow();
        VanillaStatisticsProvider provider = provider(playerReturning(42));
        MetricQuery query = new MetricQuery(new MetricId(statistic.name().toLowerCase(java.util.Locale.ROOT)),
                MetricReadMode.CURRENT, Map.of());
        var sample = provider.read(UUID.randomUUID(), List.of(query), 3).toCompletableFuture().join().get(query);
        assertEquals(MetricSampleStatus.AVAILABLE, sample.status());
        assertEquals("42", sample.value().orElseThrow().canonical());
        assertEquals(3, sample.providerGeneration());
    }

    @Test
    @DisplayName("[A15] Configuration rejects non-block BLOCK filters and non-item ITEM filters before activation")
    void rejectsInvalidTypedMaterialDimensionsDuringValidation() {
        VanillaStatisticsProvider provider = provider(playerReturning(0));
        MetricDescriptor block = descriptor(provider, Statistic.Type.BLOCK);
        MetricDescriptor item = descriptor(provider, Statistic.Type.ITEM);

        assertTrue(validateFilter(block, "stick").hasErrors());
        assertFalse(validateFilter(block, "stone").hasErrors());
        assertTrue(validateFilter(item, "stone_only_block").hasErrors());
        assertFalse(validateFilter(item, "stick").hasErrors());
    }

    @Test
    @DisplayName("[A15] Unsupported metrics and unavailable player sources fail closed instead of returning zero")
    void failsClosedForUnsupportedOrUnavailableSource() {
        VanillaStatisticsProvider unavailable = provider(null);
        MetricQuery actual = new MetricQuery(new MetricId(Statistic.values()[0].name()
                .toLowerCase(java.util.Locale.ROOT)), MetricReadMode.CURRENT, Map.of());
        var sample = unavailable.read(UUID.randomUUID(), List.of(actual), 1)
                .toCompletableFuture().join().get(actual);
        assertEquals(MetricSampleStatus.UNAVAILABLE, sample.status());
        assertFalse(sample.value().isPresent());

        MetricQuery invented = new MetricQuery(new MetricId("invented_statistic"), MetricReadMode.CURRENT, Map.of());
        var unsupported = provider(playerReturning(0)).read(UUID.randomUUID(), List.of(invented), 1)
                .toCompletableFuture().join().get(invented);
        assertEquals(MetricSampleStatus.UNAVAILABLE, unsupported.status());
    }

    private static VanillaStatisticsProvider provider(Player player) {
        PaperTaskScheduler scheduler = new PaperTaskScheduler() {
            @Override
            public <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }
        };
        ProviderHealth health = new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "available",
                Instant.EPOCH);
        return new VanillaStatisticsProvider(new ProviderId("vanilla"), "maddprestige", scheduler,
                ignored -> player, () -> health, dimensions(), () -> { });
    }

    private static MetricDescriptor descriptor(VanillaStatisticsProvider provider, Statistic.Type type) {
        Set<String> statisticIds = java.util.Arrays.stream(Statistic.values())
                .filter(value -> value.getType() == type)
                .map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return provider.metrics().stream().filter(metric -> statisticIds.contains(metric.metricId().value()))
                .findFirst().orElseThrow();
    }

    private static net.maddkraft.maddprestige.api.validation.ValidationReport validateFilter(
            MetricDescriptor descriptor,
            String material) {
        RequirementDefinition definition = RequirementDefinition.create(new RequirementId("material_test"),
                descriptor.providerId(), descriptor.metricId(), MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(net.maddkraft.maddprestige.api.metric.MetricValue.count(1)),
                MeasurementScope.ABSOLUTE, CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(),
                Map.of("material", material), Map.of(), false);
        return new RequirementTreeValidator().validate(new RequirementLeaf(definition),
                Map.of(new MetricBinding(descriptor.providerId(), descriptor.metricId()), descriptor), 4);
    }

    private static StatisticDimensionCatalog dimensions() {
        return new StatisticDimensionCatalog() {
            @Override
            public Set<String> blockMaterials() {
                return Set.of("stone", "stone_only_block");
            }

            @Override
            public Set<String> itemMaterials() {
                return Set.of("stone", "stick");
            }

            @Override
            public Set<String> entityTypes() {
                return Set.of("zombie");
            }
        };
    }

    private static Player playerReturning(int value) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getStatistic")) {
                        return value;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
