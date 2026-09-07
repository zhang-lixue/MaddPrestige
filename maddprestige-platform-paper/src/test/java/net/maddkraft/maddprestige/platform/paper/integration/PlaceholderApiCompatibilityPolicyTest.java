package net.maddkraft.maddprestige.platform.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfiguration;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfiguration.PlaceholderInput;
import net.maddkraft.maddprestige.integrations.placeholder.PlaceholderInputMetricProvider;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceholderApiCompatibilityPolicyTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[P9D-PAPI] Only explicitly qualified PlaceholderAPI versions are accepted")
    void onlyExplicitlyQualifiedVersionsAreAccepted() {
        assertTrue(OptionalIntegrationManager.supportsVersion("PlaceholderAPI", "2.12.2"));
        assertTrue(OptionalIntegrationManager.supportsVersion("PlaceholderAPI", "2.12.3"));
        assertFalse(OptionalIntegrationManager.supportsVersion("PlaceholderAPI", "2.12.4"));
        assertFalse(OptionalIntegrationManager.supportsVersion("PlaceholderAPI", "2.13.0"));
    }

    @Test
    @DisplayName("[P9D-PAPI] Each supported version initializes the configured PAPI capability")
    void supportedVersionsInitializeTheCapability() {
        for (String version : List.of("2.12.2", "2.12.3")) {
            Fixture fixture = fixture(version);
            fixture.manager().start(configuration());

            assertTrue(fixture.registry().find(PlaceholderInputMetricProvider.PROVIDER_ID).isPresent(), version);
            assertTrue(fixture.manager().statusLines().get(1).contains("PlaceholderAPI"), version);
            fixture.manager().stop();
        }
    }

    @Test
    @DisplayName("[P9D-PAPI] An unsupported version cannot partially initialize the capability")
    void unsupportedVersionDoesNotPartiallyInitialize() {
        Fixture fixture = fixture("2.12.4");
        fixture.manager().start(configuration());

        assertTrue(fixture.registry().find(PlaceholderInputMetricProvider.PROVIDER_ID).isEmpty());
        assertFalse(fixture.manager().statusLines().get(1).contains("PlaceholderAPI"));
        fixture.manager().stop();
    }

    @Test
    @DisplayName("[P9D-PAPI] An absent optional PlaceholderAPI dependency remains safe")
    void absentDependencyRemainsSafe() {
        Fixture fixture = fixture(null);
        fixture.manager().start(configuration());

        assertTrue(fixture.registry().find(PlaceholderInputMetricProvider.PROVIDER_ID).isEmpty());
        assertFalse(fixture.manager().statusLines().get(1).contains("PlaceholderAPI"));
        fixture.manager().stop();
    }

    private static Fixture fixture(String version) {
        Plugin owner = mock(Plugin.class, RETURNS_DEEP_STUBS);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
        when(owner.getServer()).thenReturn(server);
        when(owner.getLogger()).thenReturn(Logger.getLogger("placeholderapi-policy-test"));
        when(owner.getPluginMeta().getVersion()).thenReturn("2.0.0-rc.2");
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(bukkitScheduler);
        when(bukkitScheduler.runTaskTimer(eq(owner), any(Runnable.class), eq(1L), eq(20L)))
                .thenReturn(mock(BukkitTask.class));
        if (version != null) {
            Plugin dependency = mock(Plugin.class, RETURNS_DEEP_STUBS);
            when(dependency.isEnabled()).thenReturn(true);
            when(dependency.getPluginMeta().getVersion()).thenReturn(version);
            when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(dependency);
        }

        ProviderRegistry registry = new ProviderRegistry();
        OptionalIntegrationManager manager = new OptionalIntegrationManager(
                owner, registry, mock(IntegrationTaskScheduler.class), mock(ManualMetricHandle.class),
                new MaddPrestigePlaceholderCache(16), CLOCK);
        return new Fixture(manager, registry);
    }

    private static IntegrationConfiguration configuration() {
        return new IntegrationConfiguration(
                7, false, false, false,
                Map.of("external", new PlaceholderInput(
                        "%external_value%", MetricValueType.COUNT, Duration.ofSeconds(5))),
                false, false, false, false, false, false, false, 128);
    }

    private record Fixture(OptionalIntegrationManager manager, ProviderRegistry registry) {
    }
}
