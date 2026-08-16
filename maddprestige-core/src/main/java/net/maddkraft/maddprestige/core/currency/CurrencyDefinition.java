package net.maddkraft.maddprestige.core.currency;

import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record CurrencyDefinition(
        CurrencyId id,
        String displayName,
        Optional<String> symbol,
        int scale,
        RoundingMode roundingMode,
        int maximumPrecision,
        ExactDecimal maximumBalance,
        boolean prestigeScoped) {
    public CurrencyDefinition {
        id = Objects.requireNonNull(id, "currency ID");
        displayName = Objects.requireNonNull(displayName, "display name");
        symbol = Objects.requireNonNull(symbol, "symbol");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Currency display name cannot be blank");
        }
        if (scale < 0 || scale > 18 || maximumPrecision < 1 || maximumPrecision > 38 || scale > maximumPrecision) {
            throw new IllegalArgumentException("Currency scale/precision is outside safe bounds");
        }
        roundingMode = Objects.requireNonNull(roundingMode, "rounding mode");
        maximumBalance = Objects.requireNonNull(maximumBalance, "maximum balance");
        if (maximumBalance.asBigDecimal().signum() <= 0 || maximumBalance.scale() > scale
                || maximumBalance.precision() > maximumPrecision) {
            throw new IllegalArgumentException("Currency maximum is not representable by its precision policy");
        }
    }

    public ExactDecimal normalize(ExactDecimal value) {
        Objects.requireNonNull(value, "value");
        ExactDecimal normalized = value.withScale(scale, roundingMode);
        if (normalized.asBigDecimal().signum() < 0 || normalized.precision() > maximumPrecision
                || normalized.compareTo(maximumBalance) > 0) {
            throw new IllegalArgumentException("Currency amount is outside configured bounds");
        }
        return normalized;
    }
}
