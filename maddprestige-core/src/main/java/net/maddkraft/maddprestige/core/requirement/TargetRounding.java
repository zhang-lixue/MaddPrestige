package net.maddkraft.maddprestige.core.requirement;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum TargetRounding {
    EXACT,
    FLOOR,
    CEILING,
    HALF_UP;

    public BigDecimal apply(BigDecimal value, BigDecimal quantum) {
        if (quantum.signum() <= 0) {
            throw new IllegalArgumentException("Rounding quantum must be positive");
        }
        if (this == EXACT) {
            BigDecimal units = value.divide(quantum);
            if (units.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Scaled discrete target is fractional without a rounding policy");
            }
            return value;
        }
        RoundingMode mode = switch (this) {
            case FLOOR -> RoundingMode.FLOOR;
            case CEILING -> RoundingMode.CEILING;
            case HALF_UP -> RoundingMode.HALF_UP;
            case EXACT -> throw new IllegalStateException("Handled above");
        };
        return value.divide(quantum, 0, mode).multiply(quantum);
    }
}
