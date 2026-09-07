package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.util.Transaction;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.manual.ManualCounterDefinition;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.core.manual.ManualProgressBootstrap;
import net.maddkraft.maddprestige.core.manual.ManualProgressRecord;
import net.maddkraft.maddprestige.core.manual.ManualProgressRepository;
import net.maddkraft.maddprestige.core.manual.ManualProgressProvider;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.economyshopgui.EconomyShopGuiCompatibilityListener;
import net.maddkraft.maddprestige.integrations.mcmmo.McMmoAdjustedXpListener;
import net.maddkraft.maddprestige.integrations.quickshop.QuickShopCompatibilityListener;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShopAndMcMmoEventContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A49] Official mcMMO XP events feed cumulative decimal progress without mcMMO mutation")
    void capturesMcMmoAdjustedXpAcrossEvents() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        ManualHarness harness = manual("mcmmo_total_xp", MetricValueType.EXACT_DECIMAL);
        McMmoAdjustedXpListener listener = new McMmoAdjustedXpListener(harness.handle(), gate(true), CLOCK);
        listener.onXpGain(new McMMOPlayerXpGainEvent(player, PrimarySkillType.MINING, 4.5F, XPGainReason.UNKNOWN));
        listener.onXpGain(new McMMOPlayerXpGainEvent(player, PrimarySkillType.MINING, 6.25F,
                XPGainReason.UNKNOWN));
        assertEquals("10.75", read(harness, playerId));
        assertEquals(net.maddkraft.maddprestige.api.metric.MetricMonotonicity.MONOTONIC,
                harness.bootstrap().provider().metrics().iterator().next().monotonicity());
    }

    @Test
    @DisplayName("[A49][A50] Cancelled or inactive mcMMO events never become progression")
    void ignoresCancelledAndInactiveMcMmoEvents() {
        UUID playerId = UUID.randomUUID();
        ManualHarness harness = manual("mcmmo_total_xp", MetricValueType.EXACT_DECIMAL);
        McMmoAdjustedXpListener inactive = new McMmoAdjustedXpListener(harness.handle(), gate(false), CLOCK);
        inactive.onXpGain(new McMMOPlayerXpGainEvent(player(playerId), PrimarySkillType.MINING, 5F,
                XPGainReason.UNKNOWN));
        McMMOPlayerXpGainEvent cancelled = new McMMOPlayerXpGainEvent(player(playerId), PrimarySkillType.MINING, 5F,
                XPGainReason.UNKNOWN);
        cancelled.setCancelled(true);
        new McMmoAdjustedXpListener(harness.handle(), gate(true), CLOCK).onXpGain(cancelled);
        assertEquals("0", read(harness, playerId));
    }

    @Test
    @DisplayName("[A49][A50] mcMMO event credit requires the exact active healthy generation")
    void bindsMcMmoEventsToExactHealthyRegistration() {
        UUID playerId = UUID.randomUUID();
        ManualHarness harness = manual("mcmmo_generation_xp", MetricValueType.EXACT_DECIMAL);
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId id = new ProviderId("mcmmo");
        MutableProviderHealth firstHealth = new MutableProviderHealth(CLOCK, ProviderHealthState.AVAILABLE,
                "test.available", "test");
        ProviderRegistrationGate firstGate = new ProviderRegistrationGate(registry, id, firstHealth);
        IntegrationProviderLifecycle lifecycle = new IntegrationProviderLifecycle(registry, "maddprestige");
        lifecycle.discover(List.of(new IntegrationProviderLifecycle.ManagedProvider(
                provider(id, firstHealth), firstHealth, firstGate)));
        lifecycle.reconcileActiveProviders(Set.of(id));
        var firstRegistration = lifecycle.registrations().get(id);
        McMmoAdjustedXpListener first = new McMmoAdjustedXpListener(harness.handle(), firstGate, CLOCK);

        credit(first, playerId, 1F);
        assertEquals("1", read(harness, playerId));
        for (ProviderHealthState outage : List.of(ProviderHealthState.UNHEALTHY,
                ProviderHealthState.UNAVAILABLE, ProviderHealthState.DEGRADED)) {
            firstHealth.transition(outage, "test.outage", outage.name());
            credit(first, playerId, 1F);
            assertEquals("1", read(harness, playerId), outage.name());
        }

        lifecycle.dependencyRecovered(firstRegistration, "successful probe");
        lifecycle.reconcileActiveProviders(Set.of());
        credit(first, playerId, 1F);
        assertEquals("1", read(harness, playerId));
        lifecycle.reconcileActiveProviders(Set.of(id));
        credit(first, playerId, 1F);
        assertEquals("2", read(harness, playerId));

        lifecycle.dependencyUnavailable("plugin disabled");
        credit(first, playerId, 1F);
        MutableProviderHealth secondHealth = new MutableProviderHealth(CLOCK, ProviderHealthState.AVAILABLE,
                "test.available", "test");
        ProviderRegistrationGate secondGate = new ProviderRegistrationGate(registry, id, secondHealth);
        lifecycle.discover(List.of(new IntegrationProviderLifecycle.ManagedProvider(
                provider(id, secondHealth), secondHealth, secondGate)));
        lifecycle.reconcileActiveProviders(Set.of(id));
        var secondRegistration = lifecycle.registrations().get(id);
        McMmoAdjustedXpListener second = new McMmoAdjustedXpListener(harness.handle(), secondGate, CLOCK);
        credit(first, playerId, 1F);
        credit(second, playerId, 1F);

        assertEquals(firstRegistration.generation() + 1, secondRegistration.generation());
        assertEquals("3", read(harness, playerId));
    }

    @Test
    @DisplayName("EconomyShopGUI events remain compatibility-only with hard zero progression credit")
    void economyShopGuiNeverCreditsProgression() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        EconomyShopGuiCompatibilityListener listener = new EconomyShopGuiCompatibilityListener(() -> true);
        listener.onPostTransaction(new PostTransactionEvent(null, player, 1, 12.5,
                Transaction.Type.SELL_SCREEN, Transaction.Result.SUCCESS));
        listener.onPostTransaction(new PostTransactionEvent(null, player, 1, 100.0,
                Transaction.Type.BUY_SCREEN, Transaction.Result.SUCCESS));
        listener.onPostTransaction(new PostTransactionEvent(null, player, 1, 100.0,
                Transaction.Type.SELL_SCREEN, Transaction.Result.TRANSACTION_CANCELLED));
        new EconomyShopGuiCompatibilityListener(() -> false).onPostTransaction(new PostTransactionEvent(null,
                player, 1, 100.0, Transaction.Type.SELL_SCREEN, Transaction.Result.SUCCESS));
        assertEquals(3, listener.observedEvents());
        assertEquals(1, listener.successfulSaleEvents());
        assertEquals(0, listener.progressionCredits());
    }

    @Test
    @DisplayName("[A54] QuickShop success events remain compatibility-only with hard zero progression credit")
    void quickShopNeverCreditsProgression() {
        UUID samePlayer = UUID.randomUUID();
        QuickShopCompatibilityListener listener = new QuickShopCompatibilityListener(() -> true);
        listener.observe(samePlayer, samePlayer);
        listener.observe(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(2, listener.successfulTransactions());
        assertEquals(1, listener.selfTransactions());
        assertEquals(0, listener.progressionCredits());
    }

    @Test
    @DisplayName("[A54] Published shop artifacts expose the exact post-success contracts used")
    void verifiesPublishedShopApiContracts() throws ReflectiveOperationException {
        assertEquals(double.class, PostTransactionEvent.class.getMethod("getPrice").getReturnType());
        assertEquals(Transaction.Result.class,
                PostTransactionEvent.class.getMethod("getTransactionResult").getReturnType());
        assertEquals(Shop.class, ShopSuccessPurchaseEvent.class.getMethod("getShop").getReturnType());
        assertEquals(QUser.class, ShopSuccessPurchaseEvent.class.getMethod("getPurchaser").getReturnType());
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> "getUniqueId".equals(method.getName()) ? id
                        : defaultValue(method.getReturnType()));
    }

    private static ProviderRegistrationGate gate(boolean active) {
        ProviderRegistry registry = new ProviderRegistry();
        ProviderId id = new ProviderId("mcmmo");
        MutableProviderHealth health = new MutableProviderHealth(CLOCK, ProviderHealthState.AVAILABLE,
                "test.available", "test");
        ProviderRegistrationGate gate = new ProviderRegistrationGate(registry, id, health);
        Provider provider = provider(id, health);
        var registration = registry.register("maddprestige", provider);
        gate.bind(registration);
        if (active) {
            registry.activate(registration);
        }
        return gate;
    }

    private static Provider provider(ProviderId id, MutableProviderHealth health) {
        return new Provider() {
            @Override
            public ProviderDescriptor descriptor() {
                return new ProviderDescriptor(id, "maddprestige", "2-stable", "test", List.of(), List.of());
            }

            @Override
            public ProviderHealth health() {
                return health.get();
            }
        };
    }

    private static void credit(McMmoAdjustedXpListener listener, UUID playerId, float amount) {
        listener.onXpGain(new McMMOPlayerXpGainEvent(player(playerId), PrimarySkillType.MINING, amount,
                XPGainReason.UNKNOWN));
    }

    private static ManualHarness manual(String metricId, MetricValueType type) {
        ManualProgressBootstrap bootstrap = ManualProgressProvider.bootstrap(new ProviderId("event_progress"),
                "maddprestige", new EmptyRepository(), Runnable::run, CLOCK, 10, 100);
        MetricId id = new MetricId(metricId);
        ManualMetricHandle handle = bootstrap.owner().registerMetric(new ManualCounterDefinition(id, type, true,
                false, metricId, "Official event-tracked progress", Map.of())).toCompletableFuture().join();
        return new ManualHarness(bootstrap, handle, id);
    }

    private static String read(ManualHarness harness, UUID playerId) {
        MetricQuery query = new MetricQuery(harness.metricId(), MetricReadMode.CURRENT, Map.of());
        return harness.bootstrap().provider().read(playerId, List.of(query), 1).toCompletableFuture().join().get(query)
                .value().orElseThrow().canonical();
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    private record ManualHarness(ManualProgressBootstrap bootstrap, ManualMetricHandle handle, MetricId metricId) {
    }

    private static final class EmptyRepository implements ManualProgressRepository {
        @Override
        public Map<UUID, ManualProgressRecord> load(ProviderId providerId, MetricId metricId) {
            return Map.of();
        }

        @Override
        public void writeBatch(Collection<ManualProgressRecord> records) {
        }
    }
}
