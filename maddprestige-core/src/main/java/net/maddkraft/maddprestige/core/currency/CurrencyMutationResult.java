package net.maddkraft.maddprestige.core.currency;

import java.util.Objects;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record CurrencyMutationResult(ExactDecimal before, ExactDecimal after, boolean replay) {
    public CurrencyMutationResult {
        before = Objects.requireNonNull(before, "before balance");
        after = Objects.requireNonNull(after, "after balance");
    }
}
