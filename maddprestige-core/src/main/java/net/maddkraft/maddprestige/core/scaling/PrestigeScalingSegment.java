package net.maddkraft.maddprestige.core.scaling;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.requirement.TargetRounding;

/** One inclusive, independently configured range of Prestige target levels. */
public record PrestigeScalingSegment(
        long startLevel,
        OptionalLong endLevel,
        SegmentScalingMode mode,
        SegmentTransition transition,
        ExactDecimal base,
        ExactDecimal rate,
        TargetRounding rounding,
        ExactDecimal roundingQuantum,
        Optional<ExactDecimal> floor,
        Optional<ExactDecimal> cap,
        NavigableMap<Long, ExactDecimal> overrides) {
    public PrestigeScalingSegment {
        endLevel = Objects.requireNonNull(endLevel, "segment end level");
        if (startLevel < 1 || endLevel.isPresent() && endLevel.getAsLong() < startLevel) {
            throw new IllegalArgumentException("Scaling segment levels must form a positive inclusive range");
        }
        mode = Objects.requireNonNull(mode, "scaling mode");
        transition = Objects.requireNonNull(transition, "segment transition");
        base = Objects.requireNonNull(base, "segment base");
        rate = Objects.requireNonNull(rate, "segment rate");
        rounding = Objects.requireNonNull(rounding, "rounding");
        roundingQuantum = Objects.requireNonNull(roundingQuantum, "rounding quantum");
        floor = Objects.requireNonNull(floor, "floor");
        cap = Objects.requireNonNull(cap, "cap");
        overrides = java.util.Collections.unmodifiableNavigableMap(
                new TreeMap<>(Objects.requireNonNull(overrides, "per-level overrides")));
        if (base.asBigDecimal().signum() < 0 || rate.asBigDecimal().signum() < 0
                || roundingQuantum.asBigDecimal().signum() <= 0
                || floor.stream().anyMatch(value -> value.asBigDecimal().signum() < 0)
                || cap.stream().anyMatch(value -> value.asBigDecimal().signum() < 0)) {
            throw new IllegalArgumentException("Scaling base/rate cannot be negative and quantum must be positive");
        }
        validateInput("base", base);
        validateInput("rate", rate);
        validateInput("rounding quantum", roundingQuantum);
        floor.ifPresent(value -> validateInput("floor", value));
        cap.ifPresent(value -> validateInput("cap", value));
        if (mode == SegmentScalingMode.EXPONENTIAL && rate.asBigDecimal().signum() == 0) {
            throw new IllegalArgumentException("Exponential segment rate must be positive");
        }
        if (transition == SegmentTransition.CONTINUE && startLevel == 1) {
            throw new IllegalArgumentException("The first scaling segment requires an explicit base");
        }
        if (floor.isPresent() && cap.isPresent()
                && floor.orElseThrow().asBigDecimal().compareTo(cap.orElseThrow().asBigDecimal()) > 0) {
            throw new IllegalArgumentException("Scaling floor cannot exceed its cap");
        }
        for (Map.Entry<Long, ExactDecimal> override : overrides.entrySet()) {
            long level = override.getKey();
            ExactDecimal overrideValue = Objects.requireNonNull(override.getValue(), "per-level override");
            if (level < startLevel || endLevel.isPresent() && level > endLevel.getAsLong()
                    || overrideValue.asBigDecimal().signum() < 0) {
                throw new IllegalArgumentException("Per-level override is outside the segment or negative");
            }
            validateInput("per-level override", overrideValue);
        }
        if (mode == SegmentScalingMode.MANUAL) {
            if (endLevel.isEmpty()) {
                throw new IllegalArgumentException("Manual scaling segments must have a finite end");
            }
            long covered;
            try {
                covered = Math.addExact(Math.subtractExact(endLevel.getAsLong(), startLevel), 1);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Manual scaling range exceeds the safe level domain", exception);
            }
            if (covered != overrides.size()) {
                throw new IllegalArgumentException("Manual scaling requires an override for every level");
            }
        }
    }

    public boolean contains(long level) {
        return level >= startLevel && (endLevel.isEmpty() || level <= endLevel.getAsLong());
    }

    private static void validateInput(String name, ExactDecimal value) {
        var decimal = value.asBigDecimal();
        if (decimal.abs().compareTo(net.maddkraft.maddprestige.core.requirement.ScalingProfile.MAX_MAGNITUDE) > 0
                || decimal.precision()
                        > net.maddkraft.maddprestige.core.requirement.ScalingProfile.MAX_PARAMETER_PRECISION
                || Math.abs((long) decimal.scale())
                        > net.maddkraft.maddprestige.core.requirement.ScalingProfile.MAX_PARAMETER_PRECISION) {
            throw new IllegalArgumentException("Scaling " + name + " exceeds the safe precision/scale/magnitude domain");
        }
    }
}
