package net.maddkraft.maddprestige.core.admin;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

/** Exact Vault balance snapshot and projected post-operation value from one canonical Prestige plan. */
public record PrestigeBalanceProjection(
        MetricValue current,
        MetricValue projected,
        Optional<MetricValue> missing) {
    private static final String BALANCE_PROVIDER = "vault_balance";
    private static final String COST_PROVIDER = "vault_economy_cost";
    private static final String REWARD_PROVIDER = "vault_economy_reward";
    private static final String ECONOMY_TYPE = "vault_economy";

    public PrestigeBalanceProjection {
        current = requireCurrency(current, "current balance");
        projected = requireCurrency(projected, "projected balance");
        missing = Objects.requireNonNull(missing, "missing balance").map(value ->
                requirePositiveCurrency(value, "missing balance"));
    }

    public PrestigeBalanceProjection(MetricValue current, MetricValue projected) {
        this(current, projected, Optional.empty());
    }

    public static Optional<PrestigeBalanceProjection> from(PrestigePlan plan) {
        Objects.requireNonNull(plan, "Prestige plan");
        ExplanationNode requirements = plan.simulation().requirements().result().explanation();
        Optional<MetricValue> current = findCurrentBalance(requirements);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashMap<String, MetricValue> costs = new LinkedHashMap<>();
        plan.costs().stream().filter(PrestigeBalanceProjection::vaultCost)
                .forEach(cost -> costs.put(cost.definition().id().value(), cost.definition().amount()));
        plan.authorizationBlockers().stream()
                .filter(blocker -> blocker.kind() == AuthorizationBlockerKind.COST_PREFLIGHT_BLOCKED)
                .filter(blocker -> COST_PROVIDER.equals(blocker.facts().get("provider")))
                .filter(blocker -> ECONOMY_TYPE.equals(blocker.facts().get("type")))
                .forEach(blocker -> addBlockedCost(costs, blocker));
        List<MetricValue> rewards = plan.rewards().stream().filter(PrestigeBalanceProjection::vaultReward)
                .map(reward -> reward.definition().value()).toList();
        List<MetricValue> costValues = List.copyOf(costs.values());
        PrestigeBalanceProjection projection = project(current.orElseThrow(), costValues, rewards);
        boolean costBlocked = plan.authorizationBlockers().stream()
                .anyMatch(PrestigeBalanceProjection::blockedVaultCost);
        Optional<MetricValue> missing = requiredAdditionalBalance(requirements, costValues, costBlocked);
        return Optional.of(new PrestigeBalanceProjection(projection.current(), projection.projected(), missing));
    }

    static PrestigeBalanceProjection project(
            MetricValue current,
            List<MetricValue> costs,
            List<MetricValue> rewards) {
        MetricValue balance = requireCurrency(current, "current balance");
        BigDecimal projected = balance.asNumber();
        for (MetricValue cost : List.copyOf(costs)) {
            projected = projected.subtract(requireCurrency(cost, "cost").asNumber());
        }
        for (MetricValue reward : List.copyOf(rewards)) {
            projected = projected.add(requireCurrency(reward, "reward").asNumber());
        }
        return new PrestigeBalanceProjection(balance,
                MetricValue.fromNumber(MetricValueType.CURRENCY_AMOUNT, projected));
    }

    static Optional<MetricValue> requiredAdditionalBalance(
            ExplanationNode requirements,
            List<MetricValue> costs,
            boolean costBlocked) {
        Objects.requireNonNull(requirements, "requirements");
        List<MetricValue> immutableCosts = List.copyOf(costs);
        Optional<MetricValue> current = findCurrentBalance(requirements);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Optional<BigDecimal> missing = requirements.status() == ExplanationStatus.UNSATISFIED
                ? findBalanceRequirementShortfall(requirements)
                : Optional.empty();
        if (costBlocked && !immutableCosts.isEmpty()) {
            BigDecimal totalCost = immutableCosts.stream()
                    .map(cost -> requireCurrency(cost, "cost").asNumber())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            missing = greaterPositive(missing, totalCost.subtract(current.orElseThrow().asNumber()));
        }
        return missing.map(value -> MetricValue.fromNumber(MetricValueType.CURRENCY_AMOUNT, value));
    }

    private static Optional<BigDecimal> findBalanceRequirementShortfall(ExplanationNode node) {
        Map<String, String> facts = node.facts();
        if (node.status() == ExplanationStatus.UNSATISFIED
                && BALANCE_PROVIDER.equals(facts.get("provider"))
                && "balance".equals(facts.get("metric"))
                && "GREATER_OR_EQUAL".equals(facts.get("operator"))
                && facts.containsKey("current")
                && facts.containsKey("target")) {
            try {
                BigDecimal current = MetricValue.parse(MetricValueType.CURRENCY_AMOUNT,
                        facts.get("current")).asNumber();
                BigDecimal target = MetricValue.parse(MetricValueType.CURRENCY_AMOUNT,
                        facts.get("target")).asNumber();
                return greaterPositive(Optional.empty(), target.subtract(current));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return node.children().stream().map(PrestigeBalanceProjection::findBalanceRequirementShortfall)
                .flatMap(Optional::stream).max(BigDecimal::compareTo);
    }

    private static Optional<BigDecimal> greaterPositive(Optional<BigDecimal> current, BigDecimal candidate) {
        if (candidate.signum() <= 0) {
            return current;
        }
        return Optional.of(current.map(value -> value.max(candidate)).orElse(candidate));
    }

    private static Optional<MetricValue> findCurrentBalance(ExplanationNode node) {
        Map<String, String> facts = node.facts();
        if (BALANCE_PROVIDER.equals(facts.get("provider"))
                && "balance".equals(facts.get("metric"))
                && facts.containsKey("current")) {
            try {
                return Optional.of(MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, facts.get("current")));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return node.children().stream().map(PrestigeBalanceProjection::findCurrentBalance)
                .flatMap(Optional::stream).findFirst();
    }

    private static void addBlockedCost(
            Map<String, MetricValue> costs,
            AuthorizationBlocker blocker) {
        String id = blocker.facts().get("id");
        String amount = blocker.facts().get("amount");
        if (id == null || amount == null || costs.containsKey(id)) {
            return;
        }
        try {
            costs.put(id, MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, amount));
        } catch (IllegalArgumentException ignored) {
            // Invalid structured facts cannot become a player-facing financial projection.
        }
    }

    private static boolean blockedVaultCost(AuthorizationBlocker blocker) {
        return blocker.kind() == AuthorizationBlockerKind.COST_PREFLIGHT_BLOCKED
                && COST_PROVIDER.equals(blocker.facts().get("provider"))
                && ECONOMY_TYPE.equals(blocker.facts().get("type"))
                && "BLOCKED".equals(blocker.facts().get("status"));
    }

    private static boolean vaultCost(PlannedCost cost) {
        return COST_PROVIDER.equals(cost.definition().providerId().value())
                && ECONOMY_TYPE.equals(cost.definition().type())
                && cost.definition().amount().type() == MetricValueType.CURRENCY_AMOUNT;
    }

    private static boolean vaultReward(PlannedReward reward) {
        return REWARD_PROVIDER.equals(reward.definition().providerId().value())
                && ECONOMY_TYPE.equals(reward.definition().type())
                && reward.definition().value().type() == MetricValueType.CURRENCY_AMOUNT;
    }

    private static MetricValue requireCurrency(MetricValue value, String label) {
        MetricValue result = Objects.requireNonNull(value, label);
        if (result.type() != MetricValueType.CURRENCY_AMOUNT) {
            throw new IllegalArgumentException(label + " must use CURRENCY_AMOUNT");
        }
        return result;
    }

    private static MetricValue requirePositiveCurrency(MetricValue value, String label) {
        MetricValue result = requireCurrency(value, label);
        if (result.asNumber().signum() <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return result;
    }
}
