package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;

/** Independent piecewise multipliers for provider-owned costs and configured rewards. */
public record PrestigeValueScalingConfiguration(
        Map<CostId, SegmentedScalingProfile> costs,
        Map<RewardId, SegmentedScalingProfile> rewards) {
    public PrestigeValueScalingConfiguration {
        costs = Map.copyOf(Objects.requireNonNull(costs, "cost scaling"));
        rewards = Map.copyOf(Objects.requireNonNull(rewards, "reward scaling"));
    }

    public static PrestigeValueScalingConfiguration empty() {
        return new PrestigeValueScalingConfiguration(Map.of(), Map.of());
    }

    public CostDefinition scale(CostDefinition definition, long targetLevel) {
        SegmentedScalingProfile profile = costs.get(definition.id());
        if (profile == null) {
            return definition;
        }
        return new CostDefinition(definition.id(), definition.providerId(), definition.type(),
                scale(definition.amount(), profile, targetLevel), definition.metadata(), definition.displayName());
    }

    public RewardDefinition scale(RewardDefinition definition, long targetLevel) {
        SegmentedScalingProfile profile = rewards.get(definition.id());
        if (profile == null) {
            return definition;
        }
        return new RewardDefinition(definition.id(), definition.providerId(), definition.type(),
                scale(definition.value(), profile, targetLevel), definition.metadata(), definition.displayName(),
                definition.failurePolicy(), definition.repeatability());
    }

    private static MetricValue scale(MetricValue value, SegmentedScalingProfile profile, long targetLevel) {
        if (!value.type().isNumeric()) {
            throw new IllegalArgumentException("Segmented scaling requires a numeric provider value");
        }
        var scaled = value.asNumber().multiply(profile.multiplierAt(targetLevel));
        if (scaled.signum() < 0 || scaled.abs().compareTo(ScalingProfile.MAX_MAGNITUDE) > 0
                || scaled.precision() > ScalingProfile.MAX_PARAMETER_PRECISION
                || Math.abs((long) scaled.scale()) > ScalingProfile.MAX_PARAMETER_PRECISION) {
            throw new IllegalArgumentException("Scaled provider value exceeds the safe precision/scale/magnitude domain");
        }
        return MetricValue.fromNumber(value.type(), scaled);
    }
}
