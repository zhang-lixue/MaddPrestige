package net.maddkraft.maddprestige.platform.paper.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Logger;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
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
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class PaperProviderBridgeTest {
    private static final Instant NOW = Instant.parse("2026-08-17T20:00:00Z");
    private static final MetricId COURT = new MetricId("court");
    private static final MetricId GAMES = new MetricId("games");

    @Test
    void servicesManagerOwnerIsAttestedAndExactHandleControlsLifecycle() {
        ProviderRegistry registry = new ProviderRegistry();
        AtomicReference<ProviderRegistrationHandle> handle = new AtomicReference<>();
        AtomicInteger unregistered = new AtomicInteger();
        ProviderDeclaration declaration = declaration(handle, unregistered);
        Plugin owner = mock(Plugin.class);
        when(owner.getName()).thenReturn("Court-Plugin");
        when(owner.isEnabled()).thenReturn(true);
        RegisteredServiceProvider<ProviderDeclaration> registration = service(declaration, owner);
        Plugin host = host(List.of(registration));

        try (PaperProviderBridge bridge = new PaperProviderBridge(host, registry,
                Clock.fixed(NOW, ZoneOffset.UTC), ignored -> owner)) {
            bridge.start().toCompletableFuture().join();
            ProviderId canonical = new ProviderId("court_plugin:fixture");

            assertEquals(canonical, handle.get().providerId());
            assertEquals(1, bridge.size());
            assertTrue(registry.find(canonical).isPresent());
            MetricProvider provider = (MetricProvider) registry.provider(canonical).orElseThrow();
            var samples = provider.read(UUID.randomUUID(), List.of(new MetricQuery(COURT,
                    MetricReadMode.CURRENT, Map.of())), registry.find(canonical).orElseThrow().generation())
                    .toCompletableFuture().join();
            assertEquals("47", samples.values().iterator().next().value().orElseThrow().canonical());

            handle.get().unregister().toCompletableFuture().join();
            assertTrue(registry.find(canonical).isEmpty());
            assertEquals(1, unregistered.get());
        }
    }

    @Test
    void startupImportSerializesMoreDeclarationsThanTheCallbackPoolMaximum() {
        ProviderRegistry registry = new ProviderRegistry();
        Plugin owner = mock(Plugin.class);
        when(owner.getName()).thenReturn("Many-Providers");
        when(owner.isEnabled()).thenReturn(true);
        ArrayList<RegisteredServiceProvider<ProviderDeclaration>> registrations = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            registrations.add(service(declaration("fixture_" + index, new AtomicReference<>(),
                    new AtomicInteger()), owner));
        }

        try (PaperProviderBridge bridge = new PaperProviderBridge(host(registrations), registry,
                Clock.fixed(NOW, ZoneOffset.UTC), ignored -> owner)) {
            bridge.start().toCompletableFuture().join();
            assertEquals(12, bridge.size());
            assertEquals(12, registry.snapshots().size());
        }
    }

    @Test
    void serviceOwnerCannotSpoofOwnershipOfAnotherPluginsImplementationClass() {
        ProviderRegistry registry = new ProviderRegistry();
        Plugin claimed = mock(Plugin.class);
        when(claimed.getName()).thenReturn("Claimed-Plugin");
        when(claimed.isEnabled()).thenReturn(true);
        Plugin actual = mock(Plugin.class);
        when(actual.getName()).thenReturn("Actual-Plugin");
        ProviderDeclaration declaration = declaration(new AtomicReference<>(), new AtomicInteger());

        try (PaperProviderBridge bridge = new PaperProviderBridge(host(List.of(service(declaration, claimed))),
                registry, Clock.fixed(NOW, ZoneOffset.UTC), ignored -> actual)) {
            bridge.start().toCompletableFuture().join();
            assertEquals(0, bridge.size());
            assertFalse(registry.find(new ProviderId("claimed_plugin:fixture")).isPresent());
        }
    }

    @Test
    void normalizedNamespaceCollisionCannotClaimAnExistingPluginsNamespace() {
        ProviderRegistry registry = new ProviderRegistry();
        Plugin firstOwner = mock(Plugin.class);
        when(firstOwner.getName()).thenReturn("Court-Plugin");
        when(firstOwner.isEnabled()).thenReturn(true);
        Plugin collidingOwner = mock(Plugin.class);
        when(collidingOwner.getName()).thenReturn("Court_Plugin");
        when(collidingOwner.isEnabled()).thenReturn(true);
        ProviderDeclaration first = new FixtureDeclarationA("first", MetricValue.count(1));
        ProviderDeclaration collision = new FixtureDeclarationB("second", MetricValue.count(2));

        try (PaperProviderBridge bridge = new PaperProviderBridge(host(List.of(
                service(first, firstOwner), service(collision, collidingOwner))), registry,
                Clock.fixed(NOW, ZoneOffset.UTC), type -> type == first.getClass() ? firstOwner : collidingOwner)) {
            bridge.start().toCompletableFuture().join();

            assertEquals(1, bridge.size());
            assertTrue(registry.find(new ProviderId("court_plugin:first")).isPresent());
            assertTrue(registry.find(new ProviderId("court_plugin:second")).isEmpty());
        }
    }

    @Test
    void semanticallyInvalidProviderResultFailsClosed() {
        ProviderRegistry registry = new ProviderRegistry();
        Plugin owner = mock(Plugin.class);
        when(owner.getName()).thenReturn("Court-Plugin");
        when(owner.isEnabled()).thenReturn(true);
        ProviderDeclaration declaration = new FixtureDeclarationA("fixture", MetricValue.bool(true));

        try (PaperProviderBridge bridge = new PaperProviderBridge(host(List.of(service(declaration, owner))),
                registry, Clock.fixed(NOW, ZoneOffset.UTC), ignored -> owner)) {
            bridge.start().toCompletableFuture().join();
            ProviderId canonical = new ProviderId("court_plugin:fixture");
            MetricProvider provider = (MetricProvider) registry.provider(canonical).orElseThrow();

            assertThrows(CompletionException.class, () -> provider.read(UUID.randomUUID(), List.of(
                    new MetricQuery(COURT, MetricReadMode.CURRENT, Map.of())),
                    registry.find(canonical).orElseThrow().generation()).toCompletableFuture().join());
        }
    }

    @Test
    void multiQueryResultMapMustEqualTheRequestedKeySetExactly() {
        Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> missing = requests ->
                Map.of(requests.getFirst(), ProviderMetricResult.available(MetricValue.count(1), NOW));
        Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> extra = requests -> {
            LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = complete(requests);
            result.put(new ProviderMetricRequest(new MetricId("undeclared"), MetricReadMode.CURRENT, Map.of()),
                    ProviderMetricResult.available(MetricValue.count(3), NOW));
            return result;
        };
        Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> nullValue =
                requests -> {
                    LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = complete(requests);
                    result.put(requests.get(1), null);
                    return result;
                };
        Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> wrongType =
                requests -> {
                    LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = complete(requests);
                    result.put(requests.get(1), ProviderMetricResult.available(MetricValue.bool(true), NOW));
                    return result;
                };

        assertThrows(CompletionException.class, () -> readMulti(missing));
        assertThrows(CompletionException.class, () -> readMulti(extra));
        assertThrows(CompletionException.class, () -> readMulti(nullValue));
        assertThrows(CompletionException.class, () -> readMulti(wrongType));
        assertEquals(2, readMulti(PaperProviderBridgeTest::complete).size());
    }

    @Test
    void invalidMetadataCannotEscapeAndAbortTheLifecycleRegistrationJob() {
        ProviderRegistry registry = new ProviderRegistry();
        Plugin owner = mock(Plugin.class);
        when(owner.getName()).thenReturn("Boundary-Plugin");
        when(owner.isEnabled()).thenReturn(true);
        ProviderDeclaration invalid = new MultiResultDeclaration("x".repeat(32), PaperProviderBridgeTest::complete);
        ProviderDeclaration valid = new MultiResultDeclaration("x".repeat(31), PaperProviderBridgeTest::complete);

        try (PaperProviderBridge bridge = new PaperProviderBridge(host(List.of(
                service(invalid, owner), service(valid, owner))), registry,
                Clock.fixed(NOW, ZoneOffset.UTC), ignored -> owner)) {
            bridge.start().toCompletableFuture().join();
            assertEquals(1, bridge.size());
            assertTrue(registry.find(new ProviderId("boundary_plugin:" + "x".repeat(31))).isPresent());
        }
    }

    private static Map<MetricQuery, net.maddkraft.maddprestige.api.metric.MetricSample> readMulti(
            Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> response) {
        ProviderRegistry registry = new ProviderRegistry();
        Plugin owner = mock(Plugin.class);
        when(owner.getName()).thenReturn("Multi-Plugin");
        when(owner.isEnabled()).thenReturn(true);
        ProviderDeclaration declaration = new MultiResultDeclaration("fixture", response);
        try (PaperProviderBridge bridge = new PaperProviderBridge(host(List.of(service(declaration, owner))),
                registry, Clock.fixed(NOW, ZoneOffset.UTC), ignored -> owner)) {
            bridge.start().toCompletableFuture().join();
            ProviderId id = new ProviderId("multi_plugin:fixture");
            MetricProvider provider = (MetricProvider) registry.provider(id).orElseThrow();
            return provider.read(UUID.randomUUID(), List.of(
                    new MetricQuery(COURT, MetricReadMode.CURRENT, Map.of()),
                    new MetricQuery(GAMES, MetricReadMode.CURRENT, Map.of())),
                    registry.find(id).orElseThrow().generation()).toCompletableFuture().join();
        }
    }

    private static LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> complete(
            List<ProviderMetricRequest> requests) {
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>();
        result.put(requests.get(0), ProviderMetricResult.available(MetricValue.count(1), NOW));
        result.put(requests.get(1), ProviderMetricResult.available(MetricValue.count(2), NOW));
        return result;
    }

    private static ProviderDeclaration declaration(
            AtomicReference<ProviderRegistrationHandle> handle,
            AtomicInteger unregistered) {
        return declaration("fixture", handle, unregistered);
    }

    private abstract static class FixtureDeclaration implements ProviderDeclaration {
        private final String localId;
        private final MetricValue value;

        private FixtureDeclaration(String localId, MetricValue value) {
            this.localId = localId;
            this.value = value;
        }

        @Override
        public ProviderMetadata metadata(ProviderCallContext context) {
            return new ProviderMetadata(localId, "court.fixture.display_name", "1.0.0", List.of(
                    new ProviderMetricDefinition(COURT, MetricValueType.COUNT,
                            EnumSet.of(MetricOperator.GREATER_OR_EQUAL),
                            EnumSet.of(MetricReadMode.CURRENT), false, MetricMonotonicity.MONOTONIC,
                            MetricResetPolicy.NOT_APPLICABLE, Map.of(), "court.display_name",
                            "court.description", "visits", "owner_supplied")));
        }

        @Override
        public RequirementProvider requirements() {
            return (context, playerId, requests) -> CompletableFuture.completedFuture(Map.of(requests.getFirst(),
                    ProviderMetricResult.available(value, NOW)));
        }
    }

    private static final class FixtureDeclarationA extends FixtureDeclaration {
        private FixtureDeclarationA(String localId, MetricValue value) {
            super(localId, value);
        }
    }

    private static final class FixtureDeclarationB extends FixtureDeclaration {
        private FixtureDeclarationB(String localId, MetricValue value) {
            super(localId, value);
        }
    }

    private static final class MultiResultDeclaration implements ProviderDeclaration {
        private final String localId;
        private final Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> response;

        private MultiResultDeclaration(
                String localId,
                Function<List<ProviderMetricRequest>, Map<ProviderMetricRequest, ProviderMetricResult>> response) {
            this.localId = localId;
            this.response = response;
        }

        @Override
        public ProviderMetadata metadata(ProviderCallContext context) {
            return new ProviderMetadata(localId, "multi.fixture.display_name", "1.0.0",
                    List.of(metric(COURT), metric(GAMES)));
        }

        @Override
        public RequirementProvider requirements() {
            return (context, playerId, requests) -> CompletableFuture.completedFuture(response.apply(requests));
        }

        private static ProviderMetricDefinition metric(MetricId id) {
            return new ProviderMetricDefinition(id, MetricValueType.COUNT,
                    EnumSet.of(MetricOperator.GREATER_OR_EQUAL), EnumSet.of(MetricReadMode.CURRENT), false,
                    MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                    "multi.display_name", "multi.description", "count", "owner_supplied");
        }
    }

    private static ProviderDeclaration declaration(
            String localId,
            AtomicReference<ProviderRegistrationHandle> handle,
            AtomicInteger unregistered) {
        ProviderMetricDefinition definition = new ProviderMetricDefinition(COURT, MetricValueType.COUNT,
                EnumSet.of(MetricOperator.GREATER_OR_EQUAL), EnumSet.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), "court.display_name",
                "court.description", "visits", "owner_supplied");
        return new ProviderDeclaration() {
            @Override
            public ProviderMetadata metadata(ProviderCallContext context) {
                return new ProviderMetadata(localId, "court.fixture.display_name", "1.0.0", List.of(definition));
            }

            @Override
            public RequirementProvider requirements() {
                return (context, playerId, requests) -> {
                    ProviderMetricRequest request = requests.getFirst();
                    return CompletableFuture.completedFuture(Map.of(request,
                            ProviderMetricResult.available(MetricValue.count(47), NOW)));
                };
            }

            @Override
            public void registered(ProviderRegistrationHandle registrationHandle) {
                handle.set(registrationHandle);
            }

            @Override
            public void unregistered() {
                unregistered.incrementAndGet();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static RegisteredServiceProvider<ProviderDeclaration> service(
            ProviderDeclaration declaration,
            Plugin owner) {
        RegisteredServiceProvider<ProviderDeclaration> service = mock(RegisteredServiceProvider.class);
        when(service.getProvider()).thenReturn(declaration);
        when(service.getPlugin()).thenReturn(owner);
        return service;
    }

    private static Plugin host(List<RegisteredServiceProvider<ProviderDeclaration>> registrations) {
        Plugin host = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        ServicesManager services = mock(ServicesManager.class);
        when(host.getServer()).thenReturn(server);
        when(host.getLogger()).thenReturn(Logger.getLogger("PaperProviderBridgeTest"));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getServicesManager()).thenReturn(services);
        when(services.getRegistrations(ProviderDeclaration.class)).thenReturn(registrations);
        return host;
    }
}
