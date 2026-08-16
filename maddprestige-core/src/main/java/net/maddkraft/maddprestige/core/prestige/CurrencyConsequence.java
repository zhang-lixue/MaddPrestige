package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record CurrencyConsequence(
        CurrencyId currencyId,
        ExactDecimal before,
        ExactDecimal delta,
        ExactDecimal after,
        String reason) {
    public CurrencyConsequence {
        currencyId = Objects.requireNonNull(currencyId, "currency ID");
        before = Objects.requireNonNull(before, "before balance");
        delta = Objects.requireNonNull(delta, "delta");
        after = Objects.requireNonNull(after, "after balance");
        reason = Objects.requireNonNull(reason, "reason");
        if (!before.add(delta).equals(after) || after.asBigDecimal().signum() < 0) {
            throw new IllegalArgumentException("Currency consequence arithmetic is inconsistent");
        }
    }
}
