package net.maddkraft.maddprestige.core.requirement;

import java.math.BigDecimal;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record ScalingProfile(
        ScalingStrategy strategy,
        ExactDecimal parameter,
        NavigableMap<Long, ExactDecimal> stepMultipliers,
        TargetRounding rounding,
        ExactDecimal roundingQuantum) {
    public static final long MAX_INDEX = 10_000;
    public static final BigDecimal MAX_MAGNITUDE = BigDecimal.ONE.scaleByPowerOfTen(100);
    public static final int MAX_PARAMETER_PRECISION = 256;
    public static final long MAX_POWER_PRECISION_WORK = 100_000;

    public ScalingProfile {
        strategy = Objects.requireNonNull(strategy, "strategy");
        parameter = Objects.requireNonNull(parameter, "parameter");
        stepMultipliers = java.util.Collections.unmodifiableNavigableMap(
                new TreeMap<>(Objects.requireNonNull(stepMultipliers, "step multipliers")));
        rounding = Objects.requireNonNull(rounding, "rounding");
        roundingQuantum = Objects.requireNonNull(roundingQuantum, "rounding quantum");
        if (parameter.asBigDecimal().signum() < 0 || roundingQuantum.asBigDecimal().signum() <= 0) {
            throw new IllegalArgumentException("Scaling parameter cannot be negative and quantum must be positive");
        }
        if (strategy == ScalingStrategy.EXPONENTIAL && parameter.asBigDecimal().signum() == 0) {
            throw new IllegalArgumentException("Exponential base must be positive");
        }
        if (parameter.asBigDecimal().precision() > MAX_PARAMETER_PRECISION
                || Math.abs((long) parameter.asBigDecimal().scale()) > MAX_PARAMETER_PRECISION) {
            throw new IllegalArgumentException("Scaling parameter precision/scale exceeds the safe domain");
        }
        if (strategy == ScalingStrategy.STEPPED && stepMultipliers.isEmpty()) {
            throw new IllegalArgumentException("Stepped scaling requires at least one threshold");
        }
        for (var entry : stepMultipliers.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() > MAX_INDEX
                    || entry.getValue().asBigDecimal().signum() < 0) {
                throw new IllegalArgumentException("Stepped thresholds and multipliers must be within the safe domain");
            }
        }
    }

    public static ScalingProfile none() {
        return new ScalingProfile(ScalingStrategy.NONE, ExactDecimal.ZERO, new TreeMap<>(),
                TargetRounding.EXACT, ExactDecimal.parse("1"));
    }

    public static ScalingProfile linear(String rate, TargetRounding rounding) {
        return new ScalingProfile(ScalingStrategy.LINEAR, ExactDecimal.parse(rate), new TreeMap<>(), rounding,
                ExactDecimal.parse("1"));
    }

    public static ScalingProfile exponential(String base, TargetRounding rounding) {
        return new ScalingProfile(ScalingStrategy.EXPONENTIAL, ExactDecimal.parse(base), new TreeMap<>(), rounding,
                ExactDecimal.parse("1"));
    }

    public BigDecimal multiplier(long index) {
        if (index < 0 || index > MAX_INDEX) {
            throw new IllegalArgumentException("Scaling index must be between 0 and " + MAX_INDEX);
        }
        return switch (strategy) {
            case NONE -> BigDecimal.ONE;
            case LINEAR -> BigDecimal.ONE.add(parameter.asBigDecimal().multiply(BigDecimal.valueOf(index)));
            case EXPONENTIAL -> {
                validatePower(index);
                yield parameter.asBigDecimal().pow(Math.toIntExact(index));
            }
            case STEPPED -> {
                var selected = stepMultipliers.floorEntry(index);
                yield selected == null ? BigDecimal.ONE : selected.getValue().asBigDecimal();
            }
        };
    }

    private void validatePower(long index) {
        long estimatedPrecision = Math.multiplyExact((long) parameter.asBigDecimal().precision(), index);
        if (estimatedPrecision > MAX_POWER_PRECISION_WORK) {
            throw new IllegalArgumentException("Exponential scaling exceeds the safe precision-work bound");
        }
        double factor = parameter.asBigDecimal().doubleValue();
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("Exponential scaling factor exceeds the safe numeric domain");
        }
        if (factor > 1.0d && Math.log10(factor) * index > 110.0d) {
            throw new IllegalArgumentException("Exponential scaling exceeds the safe predicted magnitude");
        }
    }
}
