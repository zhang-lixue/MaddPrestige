package net.maddkraft.maddprestige.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.action.ActionExecutionStatus;
import net.maddkraft.maddprestige.api.action.PreflightStatus;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.integrations.vault.VaultEconomyBinding;
import net.maddkraft.maddprestige.integrations.vault.VaultEconomyCostProvider;
import net.maddkraft.maddprestige.integrations.vault.VaultEconomyRewardProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VaultEconomyProviderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A51] Vault preflight aggregates coupled costs and performs zero mutation")
    void aggregatePreflightDoesNotMutate() {
        EconomyHarness economy = new EconomyHarness();
        VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy), "2.20.2");
        BatchIdentity identity = BatchIdentity.create();
        List<PlannedCost> costs = List.of(cost("2.25", identity), cost("5.25", identity));
        var result = provider.preflightBatch(costs).toCompletableFuture().join();
        assertTrue(result.stream().allMatch(preflight -> preflight.plannedCost().isPresent()));
        assertEquals(7.5, economy.lastHasAmount.get());
        assertEquals(1, economy.hasCalls.get());
        assertEquals(0, economy.withdrawals.get());
        assertEquals(0, economy.deposits.get());
    }

    @Test
    @DisplayName("[A51] Valid coherent aggregate preserves truthful insufficient-balance behavior")
    void coherentAggregateCanBeInsufficient() {
        EconomyHarness economy = new EconomyHarness();
        economy.hasResult = false;
        VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy), "2.20.2");
        BatchIdentity identity = BatchIdentity.create();
        var result = provider.preflightBatch(List.of(cost("2.25", identity), cost("5.25", identity)))
                .toCompletableFuture().join();
        assertTrue(result.stream().allMatch(preflight -> preflight.status() == PreflightStatus.BLOCKED));
        assertEquals(7.5, economy.lastHasAmount.get());
        assertEquals(1, economy.hasCalls.get());
        assertEquals(0, economy.withdrawals.get());
    }

    @Test
    @DisplayName("[A51] Mixed-player Vault aggregate is rejected before any provider call")
    void rejectsMixedPlayerAggregate() {
        BatchIdentity first = BatchIdentity.create();
        BatchIdentity second = new BatchIdentity(first.operationId(), UUID.randomUUID(), first.configRevision(),
                first.providerGeneration());
        assertBatchRejected(List.of(cost("2.25", first), cost("5.25", second)), "player UUID");
    }

    @Test
    @DisplayName("[A51] Mixed-operation Vault aggregate is rejected before any provider call")
    void rejectsMixedOperationAggregate() {
        BatchIdentity first = BatchIdentity.create();
        BatchIdentity second = new BatchIdentity(OperationId.random(), first.playerId(), first.configRevision(),
                first.providerGeneration());
        assertBatchRejected(List.of(cost("2.25", first), cost("5.25", second)), "operation ID");
    }

    @Test
    @DisplayName("[A51] Mixed revision or generation Vault aggregate is rejected before any provider call")
    void rejectsMixedSealedIdentityAggregate() {
        BatchIdentity first = BatchIdentity.create();
        BatchIdentity revision = new BatchIdentity(first.operationId(), first.playerId(),
                new ConfigRevisionId("revision_2"), first.providerGeneration());
        assertBatchRejected(List.of(cost("2.25", first), cost("5.25", revision)), "configuration revision");
        BatchIdentity generation = new BatchIdentity(first.operationId(), first.playerId(), first.configRevision(),
                first.providerGeneration() + 1);
        assertBatchRejected(List.of(cost("2.25", first), cost("5.25", generation)), "provider generation");
    }

    @Test
    @DisplayName("[A51] Canonical Vault execution performs one exact withdrawal with no compensation claim")
    void executesOneWithdrawal() {
        EconomyHarness economy = new EconomyHarness();
        VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy), "2.20.2");
        PlannedCost cost = cost("5.25");
        assertFalse(provider.characteristics(cost.definition()).idempotent());
        assertFalse(provider.characteristics(cost.definition()).reversible());
        assertTrue(provider.characteristics(cost.definition()).externalUncertaintyPossible());
        assertEquals(ActionExecutionStatus.APPLIED, provider.execute(cost).toCompletableFuture().join().status());
        assertEquals(1, economy.withdrawals.get());
        assertEquals(5.25, economy.lastWithdrawal.get());
    }

    @Test
    @DisplayName("[A51] Vault disappearance before execution fails closed without a debit")
    void disappearanceBeforeExecutionDoesNotDebit() {
        EconomyHarness economy = new EconomyHarness();
        MutableProviderHealth health = activeHealth();
        VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy, health), "2.20.2");
        health.transition(ProviderHealthState.UNAVAILABLE, "vault.disabled", "service unregistered");
        assertEquals(ActionExecutionStatus.FAILED,
                provider.execute(cost("4.00")).toCompletableFuture().join().status());
        assertEquals(0, economy.withdrawals.get());
    }

    @Test
    @DisplayName("[A51] Vault calls are allowed only for AVAILABLE and ACTIVE provider health")
    void vaultHealthGateUsesCanonicalAllowlist() {
        for (ProviderHealthState state : ProviderHealthState.values()) {
            EconomyHarness economy = new EconomyHarness();
            MutableProviderHealth health = new MutableProviderHealth(CLOCK, state, "test." + state.name(), "test");
            VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy, health), "2.20.2");
            ActionExecutionStatus status = provider.execute(cost("4.00")).toCompletableFuture().join().status();
            boolean usable = state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;

            assertEquals(usable ? ActionExecutionStatus.APPLIED : ActionExecutionStatus.FAILED,
                    status, state.name());
            assertEquals(usable ? 1 : 0, economy.withdrawals.get(), state.name());
            if (!usable) {
                assertEquals(0, economy.providerCalls.get(), state.name());
            }
        }
    }

    @Test
    @DisplayName("[A51] Vault success with a mismatched amount is marked externally uncertain")
    void mismatchedResponseIsUncertain() {
        EconomyHarness economy = new EconomyHarness();
        economy.responseAmount = 1.0;
        VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy), "2.20.2");
        assertEquals(ActionExecutionStatus.UNCERTAIN,
                provider.execute(cost("4.00")).toCompletableFuture().join().status());
        assertEquals(1, economy.withdrawals.get());
    }

    @Test
    @DisplayName("[A51] Vault reward validates exact decimals and deposits once under execution authority")
    void depositsRewardOnce() {
        EconomyHarness economy = new EconomyHarness();
        VaultEconomyRewardProvider provider = new VaultEconomyRewardProvider(binding(economy), "2.20.2");
        PlannedReward reward = reward("3.75");
        assertFalse(provider.validate(reward.definition()).hasErrors());
        assertEquals(0, economy.fractionalDigitReads.get());
        assertTrue(provider.preflight(reward).toCompletableFuture().join().plannedReward().isPresent());
        assertEquals(1, economy.fractionalDigitReads.get());
        assertEquals(0, economy.deposits.get());
        assertEquals(ActionExecutionStatus.APPLIED, provider.execute(reward).toCompletableFuture().join().status());
        assertEquals(1, economy.deposits.get());
        assertEquals(3.75, economy.lastDeposit.get());
    }

    @Test
    @DisplayName("Frozen Vault cost and reward contracts reject zero without provider calls")
    void zeroCostAndRewardAreInvalidForVaultContracts() {
        EconomyHarness economy = new EconomyHarness();
        VaultEconomyBinding binding = binding(economy);
        VaultEconomyCostProvider costProvider = new VaultEconomyCostProvider(binding, "2.20.2");
        VaultEconomyRewardProvider rewardProvider = new VaultEconomyRewardProvider(binding, "2.20.2");

        var costValidation = costProvider.validate(cost("0").definition());
        var rewardValidation = rewardProvider.validate(reward("0").definition());

        assertTrue(costValidation.hasErrors());
        assertTrue(costValidation.findings().getFirst().explanation().contains("must be positive"));
        assertTrue(rewardValidation.hasErrors());
        assertTrue(rewardValidation.findings().getFirst().explanation().contains("must be positive"));
        assertEquals(0, economy.providerCalls.get());
        assertEquals(0, economy.withdrawals.get());
        assertEquals(0, economy.deposits.get());
    }

    private static PlannedCost cost(String amount) {
        return cost(amount, BatchIdentity.create());
    }

    private static PlannedCost cost(String amount, BatchIdentity identity) {
        CostDefinition definition = new CostDefinition(new CostId("entry_fee"),
                new ProviderId("vault_economy_cost"), "vault_economy",
                MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, amount), Map.of(), "Entry fee");
        var characteristics = new net.maddkraft.maddprestige.api.action.ActionCharacteristics(false, false, false,
                true);
        return new PlannedCost(identity.operationId(), "vault-cost", identity.playerId(), definition,
                identity.configRevision(), identity.providerGeneration(), characteristics, "vault economy cost");
    }

    private static void assertBatchRejected(List<PlannedCost> costs, String detail) {
        EconomyHarness economy = new EconomyHarness();
        VaultEconomyCostProvider provider = new VaultEconomyCostProvider(binding(economy), "2.20.2");
        var result = provider.preflightBatch(costs).toCompletableFuture().join();
        assertTrue(result.stream().allMatch(preflight -> preflight.status() == PreflightStatus.BLOCKED));
        assertTrue(result.stream().allMatch(preflight -> preflight.detail().contains(detail)));
        assertEquals(0, economy.providerCalls.get());
        assertEquals(0, economy.withdrawals.get());
        assertEquals(0, economy.deposits.get());
    }

    private static PlannedReward reward(String amount) {
        RewardDefinition definition = new RewardDefinition(new RewardId("cash_reward"),
                new ProviderId("vault_economy_reward"), "vault_economy",
                MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, amount), Map.of(), "Cash reward",
                RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);
        var characteristics = new net.maddkraft.maddprestige.api.action.ActionCharacteristics(false, false, false,
                true);
        return new PlannedReward(OperationId.random(), "vault-reward", UUID.randomUUID(), definition,
                new ConfigRevisionId("revision_1"), 1, characteristics, "vault economy reward");
    }

    private static VaultEconomyBinding binding(EconomyHarness harness) {
        return binding(harness, activeHealth());
    }

    private static VaultEconomyBinding binding(EconomyHarness harness, MutableProviderHealth health) {
        OfflinePlayer player = (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class}, (proxy, method, arguments) -> defaultValue(method.getReturnType()));
        return new VaultEconomyBinding(harness.proxy(), ignored -> player, immediate(), health);
    }

    private static MutableProviderHealth activeHealth() {
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

    private static final class EconomyHarness {
        private final AtomicInteger providerCalls = new AtomicInteger();
        private final AtomicInteger withdrawals = new AtomicInteger();
        private final AtomicInteger deposits = new AtomicInteger();
        private final AtomicInteger fractionalDigitReads = new AtomicInteger();
        private final AtomicInteger hasCalls = new AtomicInteger();
        private final AtomicReference<Double> lastHasAmount = new AtomicReference<>();
        private final AtomicReference<Double> lastWithdrawal = new AtomicReference<>();
        private final AtomicReference<Double> lastDeposit = new AtomicReference<>();
        private double responseAmount = Double.NaN;
        private boolean hasResult = true;

        private Economy proxy() {
            return (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[]{Economy.class},
                    (proxy, method, arguments) -> {
                        providerCalls.incrementAndGet();
                        return switch (method.getName()) {
                        case "isEnabled" -> true;
                        case "fractionalDigits" -> {
                            fractionalDigitReads.incrementAndGet();
                            yield 2;
                        }
                        case "has" -> {
                            hasCalls.incrementAndGet();
                            lastHasAmount.set((Double) arguments[arguments.length - 1]);
                            yield hasResult;
                        }
                        case "withdrawPlayer" -> response(withdrawals, lastWithdrawal,
                                (Double) arguments[arguments.length - 1]);
                        case "depositPlayer" -> response(deposits, lastDeposit,
                                (Double) arguments[arguments.length - 1]);
                        case "getBalance" -> 100.0;
                        case "getName" -> "test-economy";
                        default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private EconomyResponse response(AtomicInteger count, AtomicReference<Double> observed, double requested) {
            count.incrementAndGet();
            observed.set(requested);
            double amount = Double.isNaN(responseAmount) ? requested : responseAmount;
            return new EconomyResponse(amount, 100.0, EconomyResponse.ResponseType.SUCCESS, "");
        }
    }

    private record BatchIdentity(
            OperationId operationId,
            UUID playerId,
            ConfigRevisionId configRevision,
            long providerGeneration) {
        private static BatchIdentity create() {
            return new BatchIdentity(OperationId.random(), UUID.randomUUID(), new ConfigRevisionId("revision_1"), 1);
        }
    }
}
