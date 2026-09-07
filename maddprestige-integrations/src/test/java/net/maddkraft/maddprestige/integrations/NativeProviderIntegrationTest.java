package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.action.PreflightStatus;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfigurationCompiler;
import net.maddkraft.maddprestige.integrations.config.IntegrationPlan;
import net.maddkraft.maddprestige.integrations.craftengine.CraftEngineItemAccess;
import net.maddkraft.maddprestige.integrations.craftengine.CraftEngineItemMetricProvider;
import net.maddkraft.maddprestige.integrations.craftengine.CraftEngineItemRewardProvider;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionAccess;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionClaimBlockRewardProvider;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionMetricProvider;
import net.maddkraft.maddprestige.integrations.griefprevention.GriefPreventionProviderDescriptors;
import net.maddkraft.maddprestige.integrations.worldguard.WorldGuardRegionAccess;
import net.maddkraft.maddprestige.integrations.worldguard.WorldGuardRegionMetricProvider;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NativeProviderIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER_ID = UUID.fromString("c24e5bf5-dce8-4bd2-9da4-823905602b29");

    @Test
    @DisplayName("[A73] Schema 7 providers are dormant by default and activation is exact")
    void runtimeConfigurationIsStrictAndDormant() {
        var dormant = new IntegrationConfigurationCompiler().compile("schema-version: 7\n");
        assertFalse(dormant.validation().hasErrors());
        assertTrue(IntegrationPlan.from(dormant.configuration()).reachableProviders().isEmpty());
        assertEquals(2304, dormant.configuration().craftEngineRewardMaximumQuantity());

        var enabled = new IntegrationConfigurationCompiler().compile("""
                schema-version: 7
                griefprevention: {enabled: true}
                worldguard: {enabled: true}
                craftengine:
                  enabled: true
                  reward-maximum-quantity: 128
                """);
        assertFalse(enabled.validation().hasErrors());
        assertEquals(5, IntegrationPlan.from(enabled.configuration()).reachableProviders().size());
        assertEquals(128, enabled.configuration().craftEngineRewardMaximumQuantity());

        assertTrue(new IntegrationConfigurationCompiler().compile("""
                schema-version: 7
                craftengine: {enabled: true, reward-maximum-quantity: 2305}
                """).validation().hasErrors());
        assertTrue(new IntegrationConfigurationCompiler().compile("""
                schema-version: 5
                craftengine: {enabled: true}
                """).validation().hasErrors());
    }

    @Test
    @DisplayName("[A73] GriefPrevention metrics and post-verified bonus reward retain uncertainty")
    void griefPreventionBoundaryIsTypedCheckedAndUncertainAfterMutationFailure() {
        FakeGriefPrevention access = new FakeGriefPrevention(40, 70, 12, 3);
        GriefPreventionMetricProvider metrics = new GriefPreventionMetricProvider(access, immediate(), health(),
                CLOCK, "16.18.7");
        List<MetricQuery> queries = List.of(query(GriefPreventionMetricProvider.REMAINING),
                query(GriefPreventionMetricProvider.ACCRUED), query(GriefPreventionMetricProvider.BONUS),
                query(GriefPreventionMetricProvider.OWNED_CLAIMS));
        var samples = metrics.read(PLAYER_ID, queries, 4).toCompletableFuture().join();
        assertEquals(List.of("40", "70", "12", "3"),
                queries.stream().map(query -> samples.get(query).value().orElseThrow().canonical()).toList());

        GriefPreventionClaimBlockRewardProvider rewards = new GriefPreventionClaimBlockRewardProvider(
                access, immediate(), health(), "16.18.7");
        PlannedReward reward = reward(GriefPreventionProviderDescriptors.REWARD, "bonus_claim_blocks", "8",
                Map.of());
        assertEquals(PreflightStatus.READY, rewards.preflight(reward).toCompletableFuture().join().status());
        assertEquals(ActionExecutionStatus.APPLIED,
                rewards.execute(reward).toCompletableFuture().join().status());
        assertEquals(20, access.bonus);

        access.failAfterMutation = true;
        assertEquals(ActionExecutionStatus.UNCERTAIN,
                rewards.execute(reward).toCompletableFuture().join().status());
        assertEquals(28, access.bonus);
        assertTrue(rewards.characteristics(reward.definition()).externalUncertaintyPossible());
        assertFalse(rewards.characteristics(reward.definition()).idempotent());
    }

    @Test
    @DisplayName("[A73][A74] WorldGuard region predicate distinguishes false from unavailable without mutation")
    void worldGuardPredicateFailsClosed() {
        UUID worldId = UUID.fromString("97d7f89c-a777-483f-8b7c-b55736ac8370");
        WorldGuardRegionAccess inside = (player, world, region) -> WorldGuardRegionAccess.Observation.inside();
        WorldGuardRegionMetricProvider provider = new WorldGuardRegionMetricProvider(
                inside, immediate(), health(), CLOCK, "7.0.18");
        MetricQuery query = new MetricQuery(WorldGuardRegionMetricProvider.INSIDE, MetricReadMode.CURRENT,
                Map.of(WorldGuardRegionMetricProvider.WORLD_ID, worldId.toString(),
                        WorldGuardRegionMetricProvider.REGION_ID, "spawn"));
        assertEquals("true", provider.read(PLAYER_ID, List.of(query), 2).toCompletableFuture().join()
                .get(query).value().orElseThrow().canonical());

        WorldGuardRegionMetricProvider unavailable = new WorldGuardRegionMetricProvider(
                (player, world, region) -> WorldGuardRegionAccess.Observation.unavailable("virtual set"),
                immediate(), health(), CLOCK, "7.0.18");
        assertEquals(MetricSampleStatus.UNAVAILABLE,
                unavailable.read(PLAYER_ID, List.of(query), 3).toCompletableFuture().join().get(query).status());
    }

    @Test
    @DisplayName("[A73] CraftEngine exact identity excludes forged lookalikes and reward verifies capacity")
    void craftEngineIdentityAndRewardAreFailClosed() {
        FakeCraftEngine access = new FakeCraftEngine();
        InventoryHarness inventory = new InventoryHarness(4);
        ItemStack exact = access.custom("maddkraft:token", 5);
        ItemStack forged = stack(61, true);
        inventory.contents[0] = exact;
        inventory.contents[1] = forged;
        Player player = player(inventory.proxy());
        Server server = server(player);
        CraftEngineItemMetricProvider metrics = new CraftEngineItemMetricProvider(server, access, immediate(),
                health(), CLOCK, "26.7.4");
        MetricQuery count = new MetricQuery(CraftEngineItemMetricProvider.COUNT, MetricReadMode.CURRENT,
                Map.of("item-id", "maddkraft:token"));
        assertEquals("5", metrics.read(PLAYER_ID, List.of(count), 7).toCompletableFuture().join()
                .get(count).value().orElseThrow().canonical());

        CraftEngineItemRewardProvider rewards = new CraftEngineItemRewardProvider(server, access, immediate(),
                health(), 128, "26.7.4");
        PlannedReward reward = reward(new ProviderId("craftengine_item_reward"), "custom_item", "70",
                Map.of("item-id", "maddkraft:token"));
        assertEquals(PreflightStatus.READY, rewards.preflight(reward).toCompletableFuture().join().status());
        var executed = rewards.execute(reward).toCompletableFuture().join();
        assertEquals(ActionExecutionStatus.APPLIED, executed.status(), executed.detail().orElse("no detail"));
        assertEquals(75L, CraftEngineItemMetricProvider.count(access, inventory.contents, "maddkraft:token"));
        assertEquals(61, forged.getAmount(), "lookalike stack must not be counted or overwritten");

        InventoryHarness forgeryOnlyCapacity = new InventoryHarness(4);
        forgeryOnlyCapacity.contents[0] = access.custom("maddkraft:token", 64);
        forgeryOnlyCapacity.contents[1] = stack(1, true);
        forgeryOnlyCapacity.contents[2] = stack(64, false);
        forgeryOnlyCapacity.contents[3] = stack(64, false);
        Player forgeryOnlyPlayer = player(forgeryOnlyCapacity.proxy());
        CraftEngineItemRewardProvider failClosedReward = new CraftEngineItemRewardProvider(
                server(forgeryOnlyPlayer), access, immediate(), health(), 128, "26.7.4");
        PlannedReward oneItem = reward(new ProviderId("craftengine_item_reward"), "custom_item", "1",
                Map.of("item-id", "maddkraft:token"));
        assertEquals(PreflightStatus.INVALID,
                failClosedReward.preflight(oneItem).toCompletableFuture().join().status(),
                "a similar forged stack must not contribute merge capacity");

        PlannedReward invalid = reward(new ProviderId("craftengine_item_reward"), "custom_item", "1",
                Map.of("item-id", "token"));
        assertTrue(rewards.validate(invalid.definition()).hasErrors());
    }

    @Test
    @DisplayName("[A73] CraftEngine metrics reject unknown, offline, and spoofed inventory state")
    void craftEngineMetricFailureClassesAreUnavailable() {
        FakeCraftEngine access = new FakeCraftEngine();
        InventoryHarness inventory = new InventoryHarness(5);
        inventory.contents[0] = access.custom("maddkraft:token", 2);
        inventory.contents[1] = access.custom("maddkraft:token", 3);
        inventory.contents[2] = access.custom("other:token", 7);
        inventory.contents[3] = stack(11, true);
        Player online = player(inventory.proxy());
        MetricQuery exact = new MetricQuery(CraftEngineItemMetricProvider.COUNT, MetricReadMode.CURRENT,
                Map.of("item-id", "maddkraft:token"));
        CraftEngineItemMetricProvider provider = new CraftEngineItemMetricProvider(server(online), access,
                immediate(), health(), CLOCK, "26.7.4");
        assertEquals("5", provider.read(PLAYER_ID, List.of(exact), 8).toCompletableFuture().join()
                .get(exact).value().orElseThrow().canonical());

        MetricQuery removed = new MetricQuery(CraftEngineItemMetricProvider.COUNT, MetricReadMode.CURRENT,
                Map.of("item-id", "maddkraft:removed"));
        assertEquals(MetricSampleStatus.UNAVAILABLE,
                provider.read(PLAYER_ID, List.of(removed), 8).toCompletableFuture().join().get(removed).status());
        CraftEngineItemMetricProvider offline = new CraftEngineItemMetricProvider(
                server(player(inventory.proxy(), false)), access, immediate(), health(), CLOCK, "26.7.4");
        assertEquals(MetricSampleStatus.UNAVAILABLE,
                offline.read(PLAYER_ID, List.of(exact), 8).toCompletableFuture().join().get(exact).status());
        assertEquals(5L, CraftEngineItemMetricProvider.count(access,
                new ItemStack[]{null, inventory.contents[0], inventory.contents[1]}, "maddkraft:token"));
    }

    @Test
    @DisplayName("[A73] CraftEngine reward definitions reject unsafe quantities and construction failures")
    void craftEngineRewardValidationAndConstructionFailClosed() {
        InventoryHarness inventory = new InventoryHarness(2);
        Player player = player(inventory.proxy());
        FakeCraftEngine access = new FakeCraftEngine();
        CraftEngineItemRewardProvider provider = new CraftEngineItemRewardProvider(
                server(player), access, immediate(), health(), 128, "26.7.4");
        for (String amount : List.of("0", "129", "2147483648")) {
            assertTrue(provider.validate(reward(new ProviderId("craftengine_item_reward"), "custom_item",
                    amount, Map.of("item-id", "maddkraft:token")).definition()).hasErrors());
        }
        assertThrows(IllegalArgumentException.class,
                () -> MetricValue.parse(MetricValueType.COUNT, "-1"));
        RewardDefinition fractional = new RewardDefinition(new RewardId("runtime_fractional"),
                new ProviderId("craftengine_item_reward"), "custom_item",
                MetricValue.parse(MetricValueType.EXACT_DECIMAL, "1.5"),
                Map.of("item-id", "maddkraft:token"), "fractional", RewardFailurePolicy.REQUIRED,
                RewardRepeatability.ONCE_PER_OPERATION);
        assertTrue(provider.validate(fractional).hasErrors());

        PlannedReward one = reward(new ProviderId("craftengine_item_reward"), "custom_item", "1",
                Map.of("item-id", "maddkraft:token"));
        CraftEngineItemAccess nullBuild = failingBuildAccess(null, Optional.empty());
        assertEquals(PreflightStatus.INVALID, new CraftEngineItemRewardProvider(server(player), nullBuild,
                immediate(), health(), 128, "26.7.4").preflight(one).toCompletableFuture().join().status());
        CraftEngineItemAccess wrongIdentity = failingBuildAccess(stack(1, true), Optional.of("other:token"));
        assertEquals(PreflightStatus.INVALID, new CraftEngineItemRewardProvider(server(player), wrongIdentity,
                immediate(), health(), 128, "26.7.4").preflight(one).toCompletableFuture().join().status());
        CraftEngineItemAccess throwing = new CraftEngineItemAccess() {
            @Override
            public boolean exists(String itemId) {
                return true;
            }

            @Override
            public Optional<String> identify(ItemStack stack) {
                return Optional.of("maddkraft:token");
            }

            @Override
            public ItemStack build(String itemId, Player target, int amount) {
                throw new IllegalStateException("registry not ready");
            }
        };
        assertEquals(PreflightStatus.UNAVAILABLE, new CraftEngineItemRewardProvider(server(player), throwing,
                immediate(), health(), 128, "26.7.4").preflight(one).toCompletableFuture().join().status());
    }

    @Test
    @DisplayName("[A73] CraftEngine reward splits legal stacks and direct duplicates remain non-idempotent")
    void craftEngineRewardSplitsAndDuplicateExecutionIsVisible() {
        FakeCraftEngine access = new FakeCraftEngine();
        InventoryHarness inventory = new InventoryHarness(6);
        CraftEngineItemRewardProvider provider = new CraftEngineItemRewardProvider(
                server(player(inventory.proxy())), access, immediate(), health(), 128, "26.7.4");
        PlannedReward large = reward(new ProviderId("craftengine_item_reward"), "custom_item", "128",
                Map.of("item-id", "maddkraft:token"));
        assertEquals(ActionExecutionStatus.APPLIED, provider.execute(large).toCompletableFuture().join().status());
        assertTrue(access.buildAmounts.containsAll(List.of(1, 64)));
        assertEquals(128L, CraftEngineItemMetricProvider.count(access, inventory.contents, "maddkraft:token"));

        PlannedReward one = reward(new ProviderId("craftengine_item_reward"), "custom_item", "1",
                Map.of("item-id", "maddkraft:token"));
        assertEquals(ActionExecutionStatus.APPLIED, provider.execute(one).toCompletableFuture().join().status());
        assertEquals(ActionExecutionStatus.APPLIED, provider.execute(one).toCompletableFuture().join().status());
        assertEquals(130L, CraftEngineItemMetricProvider.count(access, inventory.contents, "maddkraft:token"));
        assertFalse(provider.characteristics(one.definition()).idempotent());
    }

    @Test
    @DisplayName("[A73] CraftEngine capacity changes fail before insertion")
    void craftEngineCapacityChangeAfterPreflightHasZeroEffect() {
        FakeCraftEngine access = new FakeCraftEngine();
        InventoryHarness inventory = new InventoryHarness(1);
        CraftEngineItemRewardProvider provider = new CraftEngineItemRewardProvider(
                server(player(inventory.proxy())), access, immediate(), health(), 64, "26.7.4");
        PlannedReward reward = reward(new ProviderId("craftengine_item_reward"), "custom_item", "64",
                Map.of("item-id", "maddkraft:token"));
        assertEquals(PreflightStatus.READY, provider.preflight(reward).toCompletableFuture().join().status());
        inventory.contents[0] = stack(64, false);
        assertEquals(ActionExecutionStatus.FAILED, provider.execute(reward).toCompletableFuture().join().status());
        assertEquals(0, inventory.addCalls);
    }

    @Test
    @DisplayName("[OR7-03] CraftEngine configured reward identity is revalidated after registry removal")
    void craftEngineRewardValidationTracksCurrentRegistryIdentity() {
        FakeCraftEngine access = new FakeCraftEngine();
        CraftEngineItemRewardProvider provider = new CraftEngineItemRewardProvider(
                server(null), access, immediate(), health(), 64, "26.7.4");
        RewardDefinition definition = reward(new ProviderId("craftengine_item_reward"), "custom_item", "1",
                Map.of("item-id", "maddkraft:token")).definition();
        assertFalse(provider.validate(definition).hasErrors());

        access.present = false;
        assertTrue(provider.validate(definition).hasErrors());
        assertTrue(provider.validate(definition).findings().getFirst().explanation()
                .contains("not currently loaded"));
    }

    @Test
    @DisplayName("[A73] CraftEngine leftovers and post-mutation exceptions are uncertain and never replayed")
    void craftEnginePostMutationFailuresAreUncertainWithoutReplay() {
        PlannedReward reward = reward(new ProviderId("craftengine_item_reward"), "custom_item", "1",
                Map.of("item-id", "maddkraft:token"));
        FakeCraftEngine leftoversAccess = new FakeCraftEngine();
        InventoryHarness leftovers = new InventoryHarness(2);
        leftovers.returnUnexpectedLeftover = true;
        CraftEngineItemRewardProvider leftoversProvider = new CraftEngineItemRewardProvider(
                server(player(leftovers.proxy())), leftoversAccess, immediate(), health(), 64, "26.7.4");
        assertEquals(ActionExecutionStatus.UNCERTAIN,
                leftoversProvider.execute(reward).toCompletableFuture().join().status());
        assertEquals(1, leftovers.addCalls, "an uncertain result must not cause adapter-level replay");

        FakeCraftEngine crashAccess = new FakeCraftEngine();
        InventoryHarness crash = new InventoryHarness(2);
        crash.throwAfterMutation = true;
        CraftEngineItemRewardProvider crashProvider = new CraftEngineItemRewardProvider(
                server(player(crash.proxy())), crashAccess, immediate(), health(), 64, "26.7.4");
        assertEquals(ActionExecutionStatus.UNCERTAIN,
                crashProvider.execute(reward).toCompletableFuture().join().status());
        assertEquals(1, crash.addCalls, "post-mutation exceptions must not be retried by the adapter");
    }

    private static PlannedReward reward(ProviderId providerId, String type, String amount,
            Map<String, String> metadata) {
        RewardDefinition definition = new RewardDefinition(new RewardId("runtime_reward"), providerId, type,
                MetricValue.parse(MetricValueType.COUNT, amount), metadata, "runtime integration reward",
                RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
        return new PlannedReward(OperationId.random(), "runtime-reward", PLAYER_ID, definition,
                new ConfigRevisionId("revision_7"), 1, new ActionCharacteristics(false, false, false, true),
                "runtime integration reward");
    }

    private static CraftEngineItemAccess failingBuildAccess(ItemStack built, Optional<String> identity) {
        return new CraftEngineItemAccess() {
            @Override
            public boolean exists(String itemId) {
                return true;
            }

            @Override
            public Optional<String> identify(ItemStack stack) {
                return identity;
            }

            @Override
            public ItemStack build(String itemId, Player player, int amount) {
                return built;
            }
        };
    }

    private static MetricQuery query(MetricId metricId) {
        return new MetricQuery(metricId, MetricReadMode.CURRENT, Map.of());
    }

    private static MutableProviderHealth health() {
        return new MutableProviderHealth(CLOCK, ProviderHealthState.ACTIVE, "test.active", "test");
    }

    private static IntegrationTaskScheduler immediate() {
        return new IntegrationTaskScheduler() {
            @Override
            public <T> CompletionStage<T> call(Supplier<T> action) {
                return CompletableFuture.completedFuture(action.get());
            }
        };
    }

    private static Server server(Player player) {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, arguments) -> "getPlayer".equals(method.getName()) ? player
                        : defaultValue(method.getReturnType()));
    }

    private static Player player(PlayerInventory inventory) {
        return player(inventory, true);
    }

    private static Player player(PlayerInventory inventory, boolean online) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isOnline" -> online;
                    case "getInventory" -> inventory;
                    case "getUniqueId" -> PLAYER_ID;
                    default -> defaultValue(method.getReturnType());
                });
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
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return null;
    }

    private static final class FakeGriefPrevention implements GriefPreventionAccess {
        private final int remaining;
        private final int accrued;
        private final int owned;
        private int bonus;
        private boolean failAfterMutation;

        private FakeGriefPrevention(int remaining, int accrued, int bonus, int owned) {
            this.remaining = remaining;
            this.accrued = accrued;
            this.bonus = bonus;
            this.owned = owned;
        }

        @Override
        public ClaimBlockSnapshot read(UUID playerId) {
            return new ClaimBlockSnapshot(remaining, accrued, bonus, owned);
        }

        @Override
        public int addBonus(UUID playerId, int amount) {
            bonus = Math.addExact(bonus, amount);
            if (failAfterMutation) {
                throw new IllegalStateException("save failed after mutation");
            }
            return bonus;
        }
    }

    private static final class FakeCraftEngine implements CraftEngineItemAccess {
        private final Map<ItemStack, String> identities = new IdentityHashMap<>();
        private final List<Integer> buildAmounts = new ArrayList<>();
        private boolean present = true;

        @Override
        public boolean exists(String itemId) {
            return present && "maddkraft:token".equals(itemId);
        }

        @Override
        public Optional<String> identify(ItemStack stack) {
            return Optional.ofNullable(identities.get(stack));
        }

        @Override
        public ItemStack build(String itemId, Player player, int amount) {
            if (!exists(itemId)) {
                throw new IllegalArgumentException("missing item");
            }
            buildAmounts.add(amount);
            return custom(itemId, amount);
        }

        private ItemStack custom(String itemId, int amount) {
            ItemStack stack = stack(amount, true);
            identities.put(stack, itemId);
            return stack;
        }
    }

    private static final class InventoryHarness {
        private final ItemStack[] contents;
        private int addCalls;
        private boolean returnUnexpectedLeftover;
        private boolean throwAfterMutation;

        private InventoryHarness(int size) {
            contents = new ItemStack[size];
        }

        private PlayerInventory proxy() {
            return (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                    new Class<?>[]{PlayerInventory.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getStorageContents", "getContents" -> contents;
                        case "addItem" -> add((ItemStack[]) arguments[0]);
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private Map<Integer, ItemStack> add(ItemStack[] additions) {
            addCalls++;
            HashMap<Integer, ItemStack> leftovers = new HashMap<>();
            for (int itemIndex = 0; itemIndex < additions.length; itemIndex++) {
                ItemStack addition = additions[itemIndex];
                int remaining = addition.getAmount();
                for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                    if (contents[slot] == null) {
                        int accepted = Math.min(remaining, addition.getMaxStackSize());
                        addition.setAmount(accepted);
                        contents[slot] = addition;
                        remaining -= accepted;
                        if (throwAfterMutation) {
                            throw new IllegalStateException("simulated crash after inventory mutation");
                        }
                    }
                }
                if (remaining > 0) {
                    ItemStack leftover = addition.clone();
                    leftover.setAmount(remaining);
                    leftovers.put(itemIndex, leftover);
                }
            }
            if (returnUnexpectedLeftover && additions.length > 0) {
                leftovers.put(0, additions[0]);
            }
            return leftovers;
        }
    }

    private static ItemStack stack(int initialAmount, boolean similar) {
        ItemStack stack = mock(ItemStack.class);
        AtomicInteger amount = new AtomicInteger(initialAmount);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.getAmount()).thenAnswer(invocation -> amount.get());
        when(stack.getMaxStackSize()).thenReturn(64);
        when(stack.isSimilar(any(ItemStack.class))).thenReturn(similar);
        when(stack.clone()).thenReturn(stack);
        doAnswer(invocation -> {
            amount.set(invocation.getArgument(0));
            return null;
        }).when(stack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        return stack;
    }
}
