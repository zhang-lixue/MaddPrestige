package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrestigeBalanceProjectionTest {
    @Test
    @DisplayName("[Phase 9F-A] Canonical balance projection includes exact cost and monetary reward")
    void projectsExactPostPrestigeBalance() {
        PrestigeBalanceProjection projection = PrestigeBalanceProjection.project(
                money("8"), List.of(money("5")), List.of(money("1")));

        assertEquals("8", projection.current().canonical());
        assertEquals("4", projection.projected().canonical());
    }

    @Test
    @DisplayName("[Phase 9F-A] Canonical zero balance remains an exact available value")
    void preservesExactZeroBalance() {
        PrestigeBalanceProjection projection = PrestigeBalanceProjection.project(
                money("0"), List.of(), List.of());

        assertEquals("0", projection.current().canonical());
        assertEquals("0", projection.projected().canonical());
    }

    @Test
    @DisplayName("[Phase 9F-B] Canonical blocked balance uses the greater truthful requirement or cost shortfall")
    void derivesCanonicalBlockedBalanceShortfall() {
        ExplanationNode requirement = balanceRequirement("1", "8", "GREATER_OR_EQUAL",
                ExplanationStatus.UNSATISFIED);
        ExplanationNode root = new ExplanationNode("requirement.group", ExplanationStatus.UNSATISFIED,
                "Not Ready", Map.of("mode", "ALL"), List.of(requirement));

        Optional<MetricValue> missing = PrestigeBalanceProjection.requiredAdditionalBalance(
                root, List.of(money("5")), true);

        assertEquals(Optional.of("7"), missing.map(MetricValue::canonical));
    }

    @Test
    @DisplayName("[Phase 9F-D] Paired guided Money requirement and cost expose one truthful shortfall")
    void derivesPairedGuidedMoneyShortfall() {
        ExplanationNode requirement = balanceRequirement("1", "4", "GREATER_OR_EQUAL",
                ExplanationStatus.UNSATISFIED);
        ExplanationNode root = new ExplanationNode("requirement.group", ExplanationStatus.UNSATISFIED,
                "Not Ready", Map.of("mode", "ALL"), List.of(requirement));

        Optional<MetricValue> missing = PrestigeBalanceProjection.requiredAdditionalBalance(
                root, List.of(money("4")), true);

        assertEquals(Optional.of("3"), missing.map(MetricValue::canonical));
    }

    @Test
    @DisplayName("[Phase 9F-B] Canonical blocked balance declines to invent unsupported requirement shortfalls")
    void rejectsUnsupportedRequirementShortfall() {
        ExplanationNode requirement = balanceRequirement("1", "8", "LESS_OR_EQUAL",
                ExplanationStatus.UNSATISFIED);
        ExplanationNode root = new ExplanationNode("requirement.group", ExplanationStatus.UNSATISFIED,
                "Not Ready", Map.of("mode", "ALL"), List.of(requirement));

        assertEquals(Optional.empty(), PrestigeBalanceProjection.requiredAdditionalBalance(
                root, List.of(), false));
    }

    private static ExplanationNode balanceRequirement(
            String current,
            String target,
            String operator,
            ExplanationStatus status) {
        return new ExplanationNode("requirement.leaf", status, status.name(),
                Map.of("provider", "vault_balance", "metric", "balance", "operator", operator,
                        "current", current, "target", target), List.of());
    }

    private static MetricValue money(String amount) {
        return MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, amount);
    }
}
