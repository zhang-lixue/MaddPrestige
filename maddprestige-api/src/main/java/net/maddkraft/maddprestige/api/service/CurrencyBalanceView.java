package net.maddkraft.maddprestige.api.service;

import java.util.Objects;
import net.maddkraft.maddprestige.api.metric.MetricValue;

/**
 * Immutable configured internal-currency balance using exact typed decimal semantics.
 *
 * @param currencyId canonical configured identity, 1-64 characters
 * @param balance exact non-null {@code CURRENCY_AMOUNT} value
 * @param scale configured decimal scale in the inclusive range 0-18
 * @param prestigeScoped whether the balance resets with the current Prestige scope
 */
public record CurrencyBalanceView(String currencyId, MetricValue balance, int scale, boolean prestigeScoped) {
    public CurrencyBalanceView {
        currencyId = Objects.requireNonNull(currencyId, "currency ID");
        balance = Objects.requireNonNull(balance, "balance");
        if (!currencyId.matches("[a-z0-9][a-z0-9._-]{0,63}") || scale < 0 || scale > 18
                || balance.type() != net.maddkraft.maddprestige.api.metric.MetricValueType.CURRENCY_AMOUNT) {
            throw new IllegalArgumentException("Currency balance view is outside stable bounds");
        }
    }
}
