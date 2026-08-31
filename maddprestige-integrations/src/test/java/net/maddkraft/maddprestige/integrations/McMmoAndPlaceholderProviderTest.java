package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.api.ExperienceAPI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationConfiguration.PlaceholderInput;
import net.maddkraft.maddprestige.integrations.mcmmo.McMmoExperienceAccess;
import net.maddkraft.maddprestige.integrations.mcmmo.McMmoMetricProvider;
import net.maddkraft.maddprestige.integrations.placeholder.PlaceholderInputMetricProvider;
import net.maddkraft.maddprestige.integrations.placeholder.PlaceholderResolver;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class McMmoAndPlaceholderProviderTest {
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    @DisplayName("[A48] mcMMO public API artifact exposes the exact read-only methods used by the adapter")
    void verifiesMcMmoPublicApiContract() throws ReflectiveOperationException {
        assertEquals(int.class, ExperienceAPI.class.getMethod("getLevelOffline", UUID.class, String.class)
                .getReturnType());
        assertEquals(int.class, ExperienceAPI.class.getMethod("getPowerLevel", Player.class).getReturnType());
        assertEquals(int.class, ExperienceAPI.class.getMethod("getPowerLevelOffline", UUID.class).getReturnType());
        assertEquals(boolean.class, ExperienceAPI.class.getMethod("isValidSkillType", String.class).getReturnType());
        assertFalse(java.util.Arrays.stream(ExperienceAPI.class.getMethods())
                .filter(method -> method.getName().startsWith("get"))
                .anyMatch(method -> method.getParameterTypes().length > 0
                        && method.getParameterTypes()[0] == ClassLoader.class));
    }

    @Test
    @DisplayName("[A48][OR9B-03] total_level is advertised while power_level remains a readable hidden alias")
    void readsMcMmoMetrics() {
        McMmoExperienceAccess access = new McMmoExperienceAccess() {
            @Override
            public boolean validSkill(String skill) {
                return "MINING".equals(skill);
            }

            @Override
            public int level(UUID playerId, String skill) {
                return 42;
            }

            @Override
            public int powerLevel(UUID playerId) {
                return 314;
            }
        };
        MutableProviderHealth health = activeHealth();
        McMmoMetricProvider provider = new McMmoMetricProvider(access, immediate(), health,
                Clock.fixed(NOW, ZoneOffset.UTC), "2.2.053");
        MetricQuery skill = new MetricQuery(McMmoMetricProvider.SKILL_LEVEL, MetricReadMode.CURRENT,
                Map.of("skill", "MINING"));
        MetricQuery total = new MetricQuery(McMmoMetricProvider.TOTAL_LEVEL, MetricReadMode.CURRENT, Map.of());
        MetricQuery power = new MetricQuery(McMmoMetricProvider.POWER_LEVEL, MetricReadMode.CURRENT, Map.of());
        var samples = provider.read(UUID.randomUUID(), List.of(skill, total, power), 7).toCompletableFuture().join();
        assertEquals("42", samples.get(skill).value().orElseThrow().canonical());
        assertEquals("314", samples.get(total).value().orElseThrow().canonical());
        assertEquals("314", samples.get(power).value().orElseThrow().canonical());
        assertEquals(7, samples.get(power).providerGeneration());
        assertTrue(provider.metrics().stream().anyMatch(metric -> metric.metricId().equals(
                McMmoMetricProvider.TOTAL_LEVEL)));
        assertTrue(provider.metrics().stream().noneMatch(metric -> metric.metricId().equals(
                McMmoMetricProvider.POWER_LEVEL)));
    }

    @Test
    @DisplayName("[A48] mcMMO current levels may decrease and advertise external non-monotonic ownership")
    void acceptsDecreasingMcMmoCurrentLevels() {
        AtomicInteger skill = new AtomicInteger(42);
        AtomicInteger power = new AtomicInteger(314);
        McMmoExperienceAccess access = new McMmoExperienceAccess() {
            @Override
            public boolean validSkill(String name) {
                return true;
            }

            @Override
            public int level(UUID playerId, String name) {
                return skill.get();
            }

            @Override
            public int powerLevel(UUID playerId) {
                return power.get();
            }
        };
        McMmoMetricProvider provider = new McMmoMetricProvider(access, immediate(), activeHealth(),
                Clock.fixed(NOW, ZoneOffset.UTC), "2.2.053");
        MetricQuery skillQuery = new MetricQuery(McMmoMetricProvider.SKILL_LEVEL, MetricReadMode.CURRENT,
                Map.of("skill", "MINING"));
        MetricQuery powerQuery = new MetricQuery(McMmoMetricProvider.POWER_LEVEL, MetricReadMode.CURRENT, Map.of());
        UUID player = UUID.randomUUID();
        assertEquals("42", provider.read(player, List.of(skillQuery), 1).toCompletableFuture().join()
                .get(skillQuery).value().orElseThrow().canonical());
        assertEquals("314", provider.read(player, List.of(powerQuery), 1).toCompletableFuture().join()
                .get(powerQuery).value().orElseThrow().canonical());

        skill.set(17);
        power.set(80);
        assertEquals("17", provider.read(player, List.of(skillQuery), 1).toCompletableFuture().join()
                .get(skillQuery).value().orElseThrow().canonical());
        assertEquals("80", provider.read(player, List.of(powerQuery), 1).toCompletableFuture().join()
                .get(powerQuery).value().orElseThrow().canonical());
        provider.metrics().forEach(descriptor -> {
            assertEquals(MetricMonotonicity.NON_MONOTONIC, descriptor.monotonicity());
            assertEquals(MetricResetPolicy.NOT_APPLICABLE, descriptor.resetPolicy());
        });
    }

    @Test
    @DisplayName("[A50] mcMMO outage is unavailable without affecting independent state")
    void mcMmoOutageFailsClosed() {
        MutableProviderHealth health = activeHealth();
        McMmoMetricProvider provider = new McMmoMetricProvider(new ConstantMcMmo(), immediate(), health,
                Clock.fixed(NOW, ZoneOffset.UTC), "2.2.053");
        health.transition(ProviderHealthState.UNAVAILABLE, "mcmmo.disabled", "plugin disabled");
        MetricQuery power = new MetricQuery(McMmoMetricProvider.POWER_LEVEL, MetricReadMode.CURRENT, Map.of());
        var sample = provider.read(UUID.randomUUID(), List.of(power), 3).toCompletableFuture().join().get(power);
        assertEquals(MetricSampleStatus.UNAVAILABLE, sample.status());
    }

    @Test
    @DisplayName("[A53] Placeholder input invokes the API only during scheduled refresh, never during reads")
    void placeholderReadsUseOnlyTypedCache() {
        MutableClock clock = new MutableClock(NOW);
        AtomicInteger resolutions = new AtomicInteger();
        PlaceholderInputMetricProvider provider = placeholderProvider(
                Map.of("tokens", new PlaceholderInput("%tokens_balance%", MetricValueType.COUNT,
                        Duration.ofSeconds(5))),
                (player, placeholder) -> {
                    resolutions.incrementAndGet();
                    return "21";
                }, immediate(), activeHealth(), clock);
        UUID player = UUID.randomUUID();
        MetricQuery query = new MetricQuery(new net.maddkraft.maddprestige.api.id.MetricId("tokens"),
                MetricReadMode.CURRENT, Map.of());
        assertEquals(MetricSampleStatus.UNAVAILABLE,
                provider.read(player, List.of(query), 2).toCompletableFuture().join().get(query).status());
        provider.refresh(player).toCompletableFuture().join();
        for (int index = 0; index < 100; index++) {
            assertEquals("21", provider.read(player, List.of(query), 2).toCompletableFuture().join().get(query)
                    .value().orElseThrow().canonical());
        }
        assertEquals(1, resolutions.get());
    }

    @Test
    @DisplayName("[A53] Missing, unparseable, and stale PlaceholderAPI samples are actionable unavailable values")
    void placeholderInvalidAndStaleSamplesFailClosed() {
        MutableClock clock = new MutableClock(NOW);
        AtomicInteger calls = new AtomicInteger();
        PlaceholderInputMetricProvider provider = placeholderProvider(
                Map.of("tokens", new PlaceholderInput("%tokens_balance%", MetricValueType.COUNT,
                        Duration.ofSeconds(1))),
                (player, placeholder) -> calls.incrementAndGet() == 1 ? placeholder : "not-a-count",
                immediate(), activeHealth(), clock);
        UUID player = UUID.randomUUID();
        MetricQuery query = new MetricQuery(new net.maddkraft.maddprestige.api.id.MetricId("tokens"),
                MetricReadMode.CURRENT, Map.of());
        provider.refresh(player).toCompletableFuture().join();
        assertTrue(provider.read(player, List.of(query), 1).toCompletableFuture().join().get(query).detail()
                .orElseThrow().contains("missing"));
        provider.refresh(player).toCompletableFuture().join();
        assertTrue(provider.read(player, List.of(query), 1).toCompletableFuture().join().get(query).detail()
                .orElseThrow().contains("does not match"));

        PlaceholderInputMetricProvider valid = placeholderProvider(
                Map.of("tokens", new PlaceholderInput("%tokens_balance%", MetricValueType.COUNT,
                        Duration.ofSeconds(1))), (ignored, placeholder) -> "1", immediate(), activeHealth(), clock);
        valid.refresh(player).toCompletableFuture().join();
        clock.advance(Duration.ofSeconds(2));
        assertTrue(valid.read(player, List.of(query), 1).toCompletableFuture().join().get(query).detail()
                .orElseThrow().contains("stale"));
    }

    @Test
    @DisplayName("[A48][A51][A53] Phase 5 metric paths accept only AVAILABLE and ACTIVE health")
    void metricProviderHealthGatesUseCanonicalAllowlist() {
        MetricQuery mcmmoQuery = new MetricQuery(McMmoMetricProvider.POWER_LEVEL, MetricReadMode.CURRENT, Map.of());
        MetricQuery placeholderQuery = placeholderQuery();
        for (ProviderHealthState state : ProviderHealthState.values()) {
            MutableProviderHealth mcmmoHealth = health(state);
            McMmoMetricProvider mcmmo = new McMmoMetricProvider(new ConstantMcMmo(), immediate(), mcmmoHealth,
                    Clock.fixed(NOW, ZoneOffset.UTC), "2.2.053");
            MetricSampleStatus expected = mcmmoHealth.isUsable()
                    ? MetricSampleStatus.AVAILABLE : MetricSampleStatus.UNAVAILABLE;
            assertEquals(expected, mcmmo.read(UUID.randomUUID(), List.of(mcmmoQuery), 1)
                    .toCompletableFuture().join().get(mcmmoQuery).status(), state.name());

            AtomicInteger resolutions = new AtomicInteger();
            MutableProviderHealth placeholderHealth = health(state);
            PlaceholderHarness placeholder = placeholderHarness(new ProviderRegistry(), immediate(),
                    (player, token) -> {
                        resolutions.incrementAndGet();
                        return "9";
                    }, placeholderHealth, Clock.fixed(NOW, ZoneOffset.UTC));
            UUID placeholderPlayer = UUID.randomUUID();
            int refreshed = placeholder.provider().refresh(placeholderPlayer).toCompletableFuture().join();
            assertEquals(placeholderHealth.isUsable() ? 1 : 0, refreshed, state.name());
            assertEquals(placeholderHealth.isUsable() ? 1 : 0, resolutions.get(), state.name());
            assertEquals(expected, placeholder.provider().read(placeholderPlayer, List.of(placeholderQuery), 1)
                    .toCompletableFuture().join().get(placeholderQuery).status(), state.name());
        }
    }

    @Test
    @DisplayName("[A53] Placeholder refresh rechecks health inside the deferred scheduled task")
    void placeholderRefreshDoesNotCrossHealthOutage() {
        DeferredScheduler scheduler = new DeferredScheduler();
        AtomicInteger resolutions = new AtomicInteger();
        MutableProviderHealth health = activeHealth();
        PlaceholderHarness harness = placeholderHarness(new ProviderRegistry(), scheduler,
                (player, token) -> Integer.toString(resolutions.incrementAndGet()), health,
                Clock.fixed(NOW, ZoneOffset.UTC));

        CompletionStage<Integer> refresh = harness.provider().refresh(UUID.randomUUID());
        health.transition(ProviderHealthState.UNHEALTHY, "test.outage", "disabled after scheduling");
        scheduler.runPending();

        assertEquals(0, refresh.toCompletableFuture().join());
        assertEquals(0, resolutions.get());
        assertEquals(MetricSampleStatus.UNAVAILABLE,
                harness.provider().read(UUID.randomUUID(), List.of(placeholderQuery()), 1)
                        .toCompletableFuture().join().get(placeholderQuery()).status());

        MutableProviderHealth midRefreshHealth = activeHealth();
        AtomicInteger midRefreshCalls = new AtomicInteger();
        Map<String, PlaceholderInput> twoInputs = Map.of(
                "first", new PlaceholderInput("%first_value%", MetricValueType.COUNT, Duration.ofSeconds(5)),
                "second", new PlaceholderInput("%second_value%", MetricValueType.COUNT, Duration.ofSeconds(5)));
        PlaceholderHarness midRefresh = placeholderHarness(new ProviderRegistry(), twoInputs, immediate(),
                (player, token) -> {
                    midRefreshCalls.incrementAndGet();
                    midRefreshHealth.transition(ProviderHealthState.UNAVAILABLE, "test.outage",
                            "disabled during resolution");
                    return "7";
                }, midRefreshHealth, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID midRefreshPlayer = UUID.randomUUID();
        assertEquals(0, midRefresh.provider().refresh(midRefreshPlayer).toCompletableFuture().join());
        assertEquals(1, midRefreshCalls.get());
        midRefreshHealth.transition(ProviderHealthState.AVAILABLE, "test.recovered", "successful probe");
        MetricQuery first = new MetricQuery(new net.maddkraft.maddprestige.api.id.MetricId("first"),
                MetricReadMode.CURRENT, Map.of());
        assertTrue(midRefresh.provider().read(midRefreshPlayer, List.of(first), 1).toCompletableFuture().join()
                .get(first).detail().orElseThrow().contains("not been sampled"));
    }

    @Test
    @DisplayName("[A53] Stale Placeholder task cannot resolve after rebind; the new generation can")
    void placeholderRefreshIsBoundToExactRegistration() {
        ProviderRegistry registry = new ProviderRegistry();
        DeferredScheduler oldScheduler = new DeferredScheduler();
        AtomicInteger oldCalls = new AtomicInteger();
        PlaceholderHarness old = placeholderHarness(registry, oldScheduler,
                (player, token) -> Integer.toString(oldCalls.incrementAndGet()), activeHealth(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        CompletionStage<Integer> staleRefresh = old.provider().refresh(UUID.randomUUID());
        registry.unregister(old.registration());

        AtomicInteger newCalls = new AtomicInteger();
        PlaceholderHarness replacement = placeholderHarness(registry, immediate(),
                (player, token) -> Integer.toString(newCalls.incrementAndGet()), activeHealth(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        oldScheduler.runPending();

        assertEquals(0, staleRefresh.toCompletableFuture().join());
        assertEquals(0, oldCalls.get());
        assertEquals(old.registration().generation() + 1, replacement.registration().generation());
        assertEquals(1, replacement.provider().refresh(UUID.randomUUID()).toCompletableFuture().join());
        assertEquals(1, newCalls.get());
    }

    @Test
    @DisplayName("[A53] Outage refresh retains prior cache and explicit recovery resumes updates")
    void placeholderCacheRemainsTruthfulAcrossOutageAndRecovery() {
        AtomicInteger calls = new AtomicInteger();
        MutableProviderHealth health = activeHealth();
        PlaceholderHarness harness = placeholderHarness(new ProviderRegistry(), immediate(),
                (player, token) -> Integer.toString(calls.incrementAndGet()), health,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID player = UUID.randomUUID();
        MetricQuery query = placeholderQuery();
        assertEquals(1, harness.provider().refresh(player).toCompletableFuture().join());
        assertEquals("1", harness.provider().read(player, List.of(query), 1).toCompletableFuture().join()
                .get(query).value().orElseThrow().canonical());

        health.transition(ProviderHealthState.UNAVAILABLE, "test.outage", "plugin disabled");
        assertEquals(0, harness.provider().refresh(player).toCompletableFuture().join());
        assertEquals(1, calls.get());
        assertEquals(MetricSampleStatus.UNAVAILABLE,
                harness.provider().read(player, List.of(query), 1).toCompletableFuture().join().get(query).status());
        health.transition(ProviderHealthState.AVAILABLE, "test.recovered", "successful rebind");
        assertEquals("1", harness.provider().read(player, List.of(query), 1).toCompletableFuture().join()
                .get(query).value().orElseThrow().canonical());
        assertEquals(1, harness.provider().refresh(player).toCompletableFuture().join());
        assertEquals(2, calls.get());
    }

    private static MutableProviderHealth activeHealth() {
        return new MutableProviderHealth(Clock.fixed(NOW, ZoneOffset.UTC), ProviderHealthState.ACTIVE,
                "test.active", "test");
    }

    private static MutableProviderHealth health(ProviderHealthState state) {
        return new MutableProviderHealth(Clock.fixed(NOW, ZoneOffset.UTC), state, "test." + state.name(), "test");
    }

    private static IntegrationTaskScheduler immediate() {
        return new IntegrationTaskScheduler() {
            @Override
            public <T> CompletionStage<T> call(Supplier<T> action) {
                return CompletableFuture.completedFuture(action.get());
            }
        };
    }

    private static PlaceholderInputMetricProvider placeholderProvider(
            Map<String, PlaceholderInput> definitions,
            PlaceholderResolver resolver,
            IntegrationTaskScheduler scheduler,
            MutableProviderHealth health,
            Clock clock) {
        return placeholderHarness(new ProviderRegistry(), definitions, scheduler, resolver, health, clock)
                .provider();
    }

    private static PlaceholderHarness placeholderHarness(
            ProviderRegistry registry,
            IntegrationTaskScheduler scheduler,
            PlaceholderResolver resolver,
            MutableProviderHealth health,
            Clock clock) {
        return placeholderHarness(registry, Map.of("tokens",
                new PlaceholderInput("%tokens_balance%", MetricValueType.COUNT, Duration.ofSeconds(5))),
                scheduler, resolver, health, clock);
    }

    private static PlaceholderHarness placeholderHarness(
            ProviderRegistry registry,
            Map<String, PlaceholderInput> definitions,
            IntegrationTaskScheduler scheduler,
            PlaceholderResolver resolver,
            MutableProviderHealth health,
            Clock clock) {
        ProviderRegistrationGate gate = new ProviderRegistrationGate(
                registry, PlaceholderInputMetricProvider.PROVIDER_ID, health);
        PlaceholderInputMetricProvider provider = new PlaceholderInputMetricProvider(definitions, resolver,
                scheduler, health, gate, clock, 10, "2.12.2");
        ProviderRegistration registration = registry.register("maddprestige", provider);
        gate.bind(registration);
        registry.activate(registration);
        return new PlaceholderHarness(provider, registration);
    }

    private static MetricQuery placeholderQuery() {
        return new MetricQuery(new net.maddkraft.maddprestige.api.id.MetricId("tokens"),
                MetricReadMode.CURRENT, Map.of());
    }

    private static final class ConstantMcMmo implements McMmoExperienceAccess {
        @Override
        public boolean validSkill(String skill) {
            return true;
        }

        @Override
        public int level(UUID playerId, String skill) {
            return 1;
        }

        @Override
        public int powerLevel(UUID playerId) {
            return 1;
        }
    }

    private static final class DeferredScheduler implements IntegrationTaskScheduler {
        private final AtomicReference<Runnable> pending = new AtomicReference<>();

        @Override
        public <T> CompletionStage<T> call(Supplier<T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            if (!pending.compareAndSet(null, () -> {
                try {
                    result.complete(action.get());
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            })) {
                throw new IllegalStateException("Only one deferred task is supported by this test scheduler");
            }
            return result;
        }

        private void runPending() {
            Runnable task = pending.getAndSet(null);
            if (task == null) {
                throw new IllegalStateException("No task is pending");
            }
            task.run();
        }
    }

    private record PlaceholderHarness(
            PlaceholderInputMetricProvider provider, ProviderRegistration registration) {
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
