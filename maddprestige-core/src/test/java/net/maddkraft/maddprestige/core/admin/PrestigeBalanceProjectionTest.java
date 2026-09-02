package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
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

    private static MetricValue money(String amount) {
        return MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, amount);
    }
}
