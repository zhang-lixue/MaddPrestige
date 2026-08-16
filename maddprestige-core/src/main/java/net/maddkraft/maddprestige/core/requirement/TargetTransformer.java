package net.maddkraft.maddprestige.core.requirement;

import java.math.BigDecimal;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public final class TargetTransformer {
    public EffectiveTarget transform(
            RequirementTarget target,
            ScalingProfile scaling,
            CatchUpProfile catchUp,
            long scalingIndex,
            ExactDecimal catchUpPosition) {
        if (!target.lower().type().isNumeric()) {
            if (scaling.strategy() != ScalingStrategy.NONE || catchUp.enabled()) {
                throw new IllegalArgumentException("Scaling and catch-up require a numeric metric target");
            }
            return new EffectiveTarget(target, "non-numeric target unchanged");
        }
        BigDecimal multiplier = scaling.multiplier(scalingIndex);
        BigDecimal reduction = catchUp.reduction(catchUpPosition);
        MetricValue lower = transformValue(target.lower(), multiplier, reduction, scaling, catchUp);
        var upper = target.upper().map(value -> transformValue(value, multiplier, reduction, scaling, catchUp));
        return new EffectiveTarget(new RequirementTarget(lower, upper),
                "scaled=base*" + multiplier.toPlainString() + ", catch-up-reduction="
                        + reduction.toPlainString() + " (scaling before catch-up)");
    }

    private static MetricValue transformValue(
            MetricValue value,
            BigDecimal multiplier,
            BigDecimal reduction,
            ScalingProfile scaling,
            CatchUpProfile catchUp) {
        BigDecimal transformed = value.asNumber().multiply(multiplier).multiply(BigDecimal.ONE.subtract(reduction));
        if (catchUp.floor().isPresent()) {
            transformed = transformed.max(catchUp.floor().orElseThrow().asBigDecimal());
        }
        if (transformed.abs().compareTo(ScalingProfile.MAX_MAGNITUDE) > 0) {
            throw new IllegalArgumentException("Effective target exceeds the configured safe magnitude");
        }
        if (value.type().isDiscrete()) {
            BigDecimal quantum = catchUp.enabled()
                    ? catchUp.roundingQuantum().asBigDecimal() : scaling.roundingQuantum().asBigDecimal();
            TargetRounding rounding = catchUp.enabled() ? catchUp.rounding() : scaling.rounding();
            transformed = rounding.apply(transformed, quantum);
        }
        return MetricValue.fromNumber(value.type(), transformed);
    }
}
