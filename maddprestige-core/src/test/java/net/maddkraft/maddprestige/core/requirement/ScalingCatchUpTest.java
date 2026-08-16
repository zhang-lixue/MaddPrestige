package net.maddkraft.maddprestige.core.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScalingCatchUpTest {
    private final TargetTransformer transformer = new TargetTransformer();

    @Test
    @DisplayName("[A18-A20] None, linear, exponential, and stepped scaling use exact documented multipliers")
    void appliesScalingStrategies() {
        RequirementTarget base = RequirementTarget.single(MetricValue.decimal("100"));
        assertEquals("100", transform(base, ScalingProfile.none(), CatchUpProfile.disabled(), 99, "0"));
        assertEquals("120", transform(base, ScalingProfile.linear("0.10", TargetRounding.EXACT),
                CatchUpProfile.disabled(), 2, "0"));
        assertEquals("800", transform(base, ScalingProfile.exponential("2", TargetRounding.EXACT),
                CatchUpProfile.disabled(), 3, "0"));
        NavigableMap<Long, ExactDecimal> steps = new TreeMap<>();
        steps.put(0L, ExactDecimal.parse("1"));
        steps.put(5L, ExactDecimal.parse("1.5"));
        ScalingProfile stepped = new ScalingProfile(ScalingStrategy.STEPPED, ExactDecimal.ZERO, steps,
                TargetRounding.EXACT, ExactDecimal.parse("1"));
        assertEquals("100", transform(base, stepped, CatchUpProfile.disabled(), 4, "0"));
        assertEquals("150", transform(base, stepped, CatchUpProfile.disabled(), 5, "0"));
    }

    @Test
    @DisplayName("[A21-A22] Catch-up is opt-in, capped, floored, rounded, and runs after scaling")
    void appliesExplicitCatchUpAfterScaling() {
        RequirementTarget base = RequirementTarget.single(MetricValue.count(100));
        CatchUpProfile disabled = CatchUpProfile.disabled();
        assertEquals("100", transform(base, ScalingProfile.none(), disabled, 0, "99"));

        CatchUpProfile catchUp = new CatchUpProfile(true, ExactDecimal.parse("2"),
                ExactDecimal.parse("0.1"), ExactDecimal.parse("0.5"), Optional.of(ExactDecimal.parse("80")),
                TargetRounding.CEILING, ExactDecimal.parse("1"));
        assertEquals("100", transform(base, ScalingProfile.none(), catchUp, 0, "2"));
        assertEquals("80", transform(base, ScalingProfile.none(), catchUp, 0, "5"));
        assertEquals("80", transform(base, ScalingProfile.none(), catchUp, 0, "100"));

        CatchUpProfile twentyFivePercent = new CatchUpProfile(true, ExactDecimal.ZERO,
                ExactDecimal.parse("0.25"), ExactDecimal.parse("0.25"), Optional.empty(),
                TargetRounding.EXACT, ExactDecimal.parse("1"));
        assertEquals("150", transform(RequirementTarget.single(MetricValue.decimal("100")),
                ScalingProfile.exponential("2", TargetRounding.EXACT), twentyFivePercent, 1, "1"));
    }

    @Test
    @DisplayName("[A18-A22] Discrete rounding, duration quantum, invalid domain, and overflow fail deterministically")
    void validatesBoundsAndRounding() {
        RequirementTarget count = RequirementTarget.single(MetricValue.count(3));
        assertThrows(IllegalArgumentException.class, () -> transform(count,
                ScalingProfile.linear("0.5", TargetRounding.EXACT), CatchUpProfile.disabled(), 1, "0"));
        assertEquals("5", transform(count, ScalingProfile.linear("0.5", TargetRounding.HALF_UP),
                CatchUpProfile.disabled(), 1, "0"));

        ScalingProfile durationScaling = new ScalingProfile(ScalingStrategy.LINEAR, ExactDecimal.parse("0.5"),
                new TreeMap<>(), TargetRounding.CEILING, ExactDecimal.parse("60000"));
        assertEquals("PT3M", transform(RequirementTarget.single(MetricValue.parse(
                net.maddkraft.maddprestige.api.metric.MetricValueType.DURATION, "2m")),
                durationScaling, CatchUpProfile.disabled(), 1, "0"));

        assertThrows(IllegalArgumentException.class,
                () -> ScalingProfile.exponential("0", TargetRounding.EXACT));
        assertThrows(IllegalArgumentException.class, () -> transform(count, ScalingProfile.none(),
                CatchUpProfile.disabled(), ScalingProfile.MAX_INDEX + 1, "0"));
        assertThrows(IllegalArgumentException.class, () -> transform(
                RequirementTarget.single(MetricValue.decimal("1" + "0".repeat(101))),
                ScalingProfile.none(), CatchUpProfile.disabled(), 0, "0"));
    }

    @Test
    @DisplayName("[A18-A20] Exponential precision work and predicted magnitude fail before pathological pow")
    void rejectsUnsafeExponentialWorkBeforeComputation() {
        assertThrows(IllegalArgumentException.class,
                () -> ScalingProfile.exponential("1." + "1".repeat(256), TargetRounding.EXACT));
        ScalingProfile excessiveWork = ScalingProfile.exponential("1." + "1".repeat(199),
                TargetRounding.EXACT);
        assertThrows(IllegalArgumentException.class, () -> excessiveWork.multiplier(10_000));
        ScalingProfile excessiveMagnitude = ScalingProfile.exponential("99", TargetRounding.EXACT);
        assertThrows(IllegalArgumentException.class, () -> excessiveMagnitude.multiplier(100));
    }

    private String transform(
            RequirementTarget target,
            ScalingProfile scaling,
            CatchUpProfile catchUp,
            long index,
            String position) {
        return transformer.transform(target, scaling, catchUp, index, ExactDecimal.parse(position))
                .target().lower().canonical();
    }
}
