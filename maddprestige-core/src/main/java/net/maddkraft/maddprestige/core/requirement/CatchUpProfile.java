package net.maddkraft.maddprestige.core.requirement;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record CatchUpProfile(
        boolean enabled,
        ExactDecimal startThreshold,
        ExactDecimal reductionRate,
        ExactDecimal maximumReduction,
        Optional<ExactDecimal> floor,
        TargetRounding rounding,
        ExactDecimal roundingQuantum) {
    public CatchUpProfile {
        startThreshold = Objects.requireNonNull(startThreshold, "start threshold");
        reductionRate = Objects.requireNonNull(reductionRate, "reduction rate");
        maximumReduction = Objects.requireNonNull(maximumReduction, "maximum reduction");
        floor = Objects.requireNonNull(floor, "floor");
        rounding = Objects.requireNonNull(rounding, "rounding");
        roundingQuantum = Objects.requireNonNull(roundingQuantum, "rounding quantum");
        if (startThreshold.asBigDecimal().signum() < 0 || reductionRate.asBigDecimal().signum() < 0
                || maximumReduction.asBigDecimal().signum() < 0
                || maximumReduction.asBigDecimal().compareTo(BigDecimal.ONE) > 0
                || roundingQuantum.asBigDecimal().signum() <= 0) {
            throw new IllegalArgumentException("Catch-up values are outside the safe domain");
        }
    }

    public static CatchUpProfile disabled() {
        return new CatchUpProfile(false, ExactDecimal.ZERO, ExactDecimal.ZERO, ExactDecimal.ZERO,
                Optional.empty(), TargetRounding.EXACT, ExactDecimal.parse("1"));
    }

    public BigDecimal reduction(ExactDecimal position) {
        if (!enabled || position.compareTo(startThreshold) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal distance = position.asBigDecimal().subtract(startThreshold.asBigDecimal());
        return distance.multiply(reductionRate.asBigDecimal()).min(maximumReduction.asBigDecimal());
    }
}
