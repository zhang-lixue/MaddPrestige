package net.maddkraft.maddprestige.api.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ExactDecimal implements Comparable<ExactDecimal> {
    public static final ExactDecimal ZERO = new ExactDecimal(BigDecimal.ZERO);
    private final BigDecimal value;

    private ExactDecimal(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "decimal").stripTrailingZeros();
        this.value = normalized.signum() == 0 ? BigDecimal.ZERO : normalized;
    }

    public static ExactDecimal parse(String value) {
        Objects.requireNonNull(value, "decimal text");
        if (value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
            throw new IllegalArgumentException("Scientific notation is not canonical decimal input: " + value);
        }
        return new ExactDecimal(new BigDecimal(value));
    }

    public static ExactDecimal of(BigDecimal value) {
        return new ExactDecimal(value);
    }

    public BigDecimal asBigDecimal() {
        return value;
    }

    public ExactDecimal withScale(int scale, RoundingMode roundingMode) {
        return new ExactDecimal(value.setScale(scale, Objects.requireNonNull(roundingMode, "rounding mode")));
    }

    public ExactDecimal add(ExactDecimal other) {
        return new ExactDecimal(value.add(other.value));
    }

    @Override
    public int compareTo(ExactDecimal other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExactDecimal decimal && value.compareTo(decimal.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
