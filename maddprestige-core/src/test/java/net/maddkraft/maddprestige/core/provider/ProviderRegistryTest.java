package net.maddkraft.maddprestige.core.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
    @Test
    @DisplayName("[A50][A65] Provider owner, activation, and stale generations are enforced")
    void enforcesRegistrationIdentityAndGeneration() {
        ProviderRegistry registry = new ProviderRegistry();
        TestProvider provider = new TestProvider("test-owner");
        assertThrows(SecurityException.class, () -> registry.register("spoofed-owner", provider));

        ProviderRegistration first = registry.register("test-owner", provider);
        assertFalse(registry.acceptsEvent(first));
        registry.activate(first);
        assertTrue(registry.acceptsEvent(first));
        assertEquals(ProviderHealthState.AVAILABLE,
                registry.find(new ProviderId("fake_progress")).orElseThrow().health().state());
        registry.unregister(first);

        ProviderRegistration second = registry.register("test-owner", provider);
        assertEquals(first.generation() + 1, second.generation());
        assertFalse(registry.acceptsEvent(first));
    }

    @Test
    @DisplayName("[A65][8B] Blocking metadata callbacks never hold the registry monitor and snapshots are cached")
    void callbacksNeverRunUnderRegistryMonitor() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger descriptors = new AtomicInteger();
        AtomicInteger health = new AtomicInteger();
        Provider provider = new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                descriptors.incrementAndGet();
                entered.countDown();
                await(release);
                return ProviderRegistryTest.descriptor("blocking-owner", "blocking_metadata");
            }

            @Override
            public ProviderHealth health() {
                health.incrementAndGet();
                return available();
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var registration = executor.submit(() -> registry.register("blocking-owner", provider));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var snapshotRead = executor.submit(registry::snapshots);
            assertTrue(snapshotRead.get(500, TimeUnit.MILLISECONDS).isEmpty());
            release.countDown();
            registration.get(2, TimeUnit.SECONDS);
        }
        registry.snapshots();
        registry.find(new ProviderId("blocking_metadata"));
        assertEquals(1, descriptors.get());
        assertEquals(1, health.get());
    }

    @Test
    @DisplayName("[A65][8B] Health callback failures are isolated and observer callbacks run outside the monitor")
    void healthFailuresAndObserversAreIsolated() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        Provider provider = new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return ProviderRegistryTest.descriptor("owner", "health_boundary");
            }

            @Override
            public ProviderHealth health() {
                if (calls.getAndIncrement() == 0) {
                    return available();
                }
                throw new LinkageError("foreign classloader disappeared");
            }
        };
        ProviderRegistration registration = registry.register("owner", provider);
        registry.activate(registration);
        CountDownLatch observed = new CountDownLatch(1);
        try (AutoCloseable ignored = registry.addHealthListener((id, before, after) -> {
            assertEquals(1, registry.snapshots().size());
            observed.countDown();
        })) {
            assertEquals(ProviderHealthState.UNHEALTHY,
                    registry.refreshHealth(registration.providerId()).orElseThrow().state());
            assertTrue(observed.await(1, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("[8B] Health observations with only a newer timestamp are coalesced")
    void unchangedHealthMeaningDoesNotPublishAnotherTransition() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        AtomicInteger observations = new AtomicInteger();
        AtomicInteger timestamps = new AtomicInteger();
        Provider provider = new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return ProviderRegistryTest.descriptor("owner", "coalesced_health");
            }

            @Override
            public ProviderHealth health() {
                return new ProviderHealth(ProviderHealthState.AVAILABLE, "provider.ready", "Ready",
                        Instant.ofEpochSecond(timestamps.getAndIncrement()));
            }
        };
        registry.register("owner", provider);
        try (AutoCloseable listener = registry.addHealthListener((id, before, after) ->
                observations.incrementAndGet())) {
            registry.refreshHealth(new ProviderId("coalesced_health"));
        }
        assertEquals(0, observations.get());
    }

    @Test
    @DisplayName("[8B] Provider lifecycle observations are ordered and execute outside the registry monitor")
    void lifecycleObserversRunOutsideRegistryMonitor() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        AtomicInteger observations = new AtomicInteger();
        try (AutoCloseable ignored = registry.addLifecycleListener(id -> {
            registry.snapshots();
            observations.incrementAndGet();
        })) {
            ProviderRegistration registration = registry.register("owner",
                    new TestProvider("owner"));
            registry.activate(registration);
            registry.activate(registration);
            registry.deactivate(registration);
            registry.deactivate(registration);
            registry.unregister(registration);
        }
        assertEquals(4, observations.get());
    }

    private static ProviderDescriptor descriptor(String owner, String id) {
        return new ProviderDescriptor(new ProviderId(id), owner, "1", "1.0.0", List.of(), List.of());
    }

    private static ProviderHealth available() {
        return new ProviderHealth(ProviderHealthState.AVAILABLE, "provider.ready", "Ready", Instant.EPOCH);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static final class TestProvider implements Provider {
        private final ProviderDescriptor descriptor;

        private TestProvider(String owner) {
            descriptor = new ProviderDescriptor(new ProviderId("fake_progress"), owner, "1", "1.0.0",
                    List.of(), List.of());
        }

        @Override
        public ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "provider.ready", "Ready for activation", Instant.EPOCH);
        }
    }
}
