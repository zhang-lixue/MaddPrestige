package net.maddkraft.maddprestige.core.scaling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.requirement.TargetRounding;

/** Deterministic piecewise scaling shared by requirements, costs, and rewards. */
public final class SegmentedScalingProfile {
    private final List<PrestigeScalingSegment> segments;

    public SegmentedScalingProfile(List<PrestigeScalingSegment> segments) {
        ArrayList<PrestigeScalingSegment> ordered = new ArrayList<>(
                Objects.requireNonNull(segments, "scaling segments"));
        ordered.sort(Comparator.comparingLong(PrestigeScalingSegment::startLevel));
        if (ordered.isEmpty() || ordered.getFirst().startLevel() != 1) {
            throw new IllegalArgumentException("Segmented scaling must begin at Prestige level 1");
        }
        for (int index = 0; index < ordered.size(); index++) {
            PrestigeScalingSegment current = ordered.get(index);
            if (index < ordered.size() - 1) {
                if (current.endLevel().isEmpty()) {
                    throw new IllegalArgumentException("Only the final scaling segment may be open-ended");
                }
                long expected;
                try {
                    expected = Math.addExact(current.endLevel().getAsLong(), 1);
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("A finite segment end cannot exceed the safe level domain",
                            exception);
                }
                if (ordered.get(index + 1).startLevel() != expected) {
                    throw new IllegalArgumentException("Scaling segments must be contiguous and non-overlapping");
                }
            }
        }
        this.segments = List.copyOf(ordered);
    }

    public List<PrestigeScalingSegment> segments() {
        return segments;
    }

    public ExactDecimal valueAt(long targetPrestigeLevel) {
        if (targetPrestigeLevel < 1) {
            throw new IllegalArgumentException("Target Prestige level must be positive");
        }
        PrestigeScalingSegment segment = segments.stream().filter(value -> value.contains(targetPrestigeLevel))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "No scaling segment covers Prestige level " + targetPrestigeLevel));
        ExactDecimal override = segment.overrides().get(targetPrestigeLevel);
        if (override != null) {
            return boundedAndRounded(override.asBigDecimal(), segment);
        }
        BigDecimal anchor = segment.transition() == SegmentTransition.EXPLICIT_BASE
                ? segment.base().asBigDecimal()
                : valueAt(Math.subtractExact(segment.startLevel(), 1)).asBigDecimal();
        long offset = targetPrestigeLevel - segment.startLevel();
        BigDecimal value = switch (segment.mode()) {
            case FLAT -> anchor;
            case LINEAR -> anchor.add(segment.rate().asBigDecimal().multiply(BigDecimal.valueOf(offset)));
            case EXPONENTIAL -> {
                validatePower(segment.rate().asBigDecimal(), offset);
                yield anchor.multiply(segment.rate().asBigDecimal().pow(Math.toIntExact(offset)));
            }
            case MANUAL -> throw new IllegalStateException("Manual segment has no value for covered level");
        };
        return boundedAndRounded(value, segment);
    }

    public BigDecimal multiplierAt(long targetPrestigeLevel) {
        return valueAt(targetPrestigeLevel).asBigDecimal();
    }

    /** True when the profile covers every target level permitted by the configured Prestige limit. */
    public boolean covers(net.maddkraft.maddprestige.core.config.lifecycle.PrestigeLimit limit) {
        Objects.requireNonNull(limit, "Prestige limit");
        PrestigeScalingSegment last = segments.getLast();
        return last.endLevel().isEmpty() || limit.maximum().isPresent()
                && last.endLevel().getAsLong() >= limit.maximum().getAsLong();
    }

    private static ExactDecimal boundedAndRounded(BigDecimal input, PrestigeScalingSegment segment) {
        BigDecimal bounded = input;
        if (segment.floor().isPresent()) {
            bounded = bounded.max(segment.floor().orElseThrow().asBigDecimal());
        }
        if (segment.cap().isPresent()) {
            bounded = bounded.min(segment.cap().orElseThrow().asBigDecimal());
        }
        validateComputed(bounded);
        if (segment.rounding() == TargetRounding.EXACT) {
            return ExactDecimal.of(bounded);
        }
        BigDecimal quantum = segment.roundingQuantum().asBigDecimal();
        RoundingMode mode = switch (segment.rounding()) {
            case FLOOR -> RoundingMode.FLOOR;
            case CEILING -> RoundingMode.CEILING;
            case HALF_UP -> RoundingMode.HALF_UP;
            case EXACT -> throw new IllegalStateException("Exact rounding handled above");
        };
        BigDecimal rounded = bounded.divide(quantum, 0, mode).multiply(quantum);
        if (segment.floor().isPresent()) {
            rounded = rounded.max(segment.floor().orElseThrow().asBigDecimal());
        }
        if (segment.cap().isPresent()) {
            rounded = rounded.min(segment.cap().orElseThrow().asBigDecimal());
        }
        validateComputed(rounded);
        return ExactDecimal.of(rounded);
    }

    private static void validateComputed(BigDecimal value) {
        if (value.signum() < 0 || value.abs().compareTo(ScalingProfile.MAX_MAGNITUDE) > 0
                || value.precision() > ScalingProfile.MAX_PARAMETER_PRECISION
                || Math.abs((long) value.scale()) > ScalingProfile.MAX_PARAMETER_PRECISION) {
            throw new IllegalArgumentException("Segmented scaling exceeds the safe precision/scale/magnitude domain");
        }
    }

    private static void validatePower(BigDecimal rate, long exponent) {
        long precisionWork;
        try {
            precisionWork = Math.multiplyExact((long) rate.precision(), exponent);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Exponential segment exceeds the safe precision-work bound",
                    exception);
        }
        if (exponent > Integer.MAX_VALUE || precisionWork > ScalingProfile.MAX_POWER_PRECISION_WORK) {
            throw new IllegalArgumentException("Exponential segment exceeds the safe precision-work bound");
        }
        double value = rate.doubleValue();
        if (!Double.isFinite(value) || value > 1.0d && Math.log10(value) * exponent > 110.0d) {
            throw new IllegalArgumentException("Exponential segment exceeds the safe numeric domain");
        }
    }
}
