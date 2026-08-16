package net.maddkraft.maddprestige.core.milestone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record MilestoneDefinition(
        MilestoneId id,
        String displayName,
        boolean enabled,
        MilestoneTriggerType triggerType,
        MetricValue threshold,
        Optional<ProviderId> providerId,
        Optional<String> providerMetricId,
        MilestoneRepeatability repeatability,
        List<RewardId> rewardIds) {
    public MilestoneDefinition {
        id = Objects.requireNonNull(id, "milestone ID");
        displayName = Objects.requireNonNull(displayName, "display name");
        triggerType = Objects.requireNonNull(triggerType, "trigger type");
        threshold = Objects.requireNonNull(threshold, "threshold");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        providerMetricId = Objects.requireNonNull(providerMetricId, "provider metric ID");
        repeatability = Objects.requireNonNull(repeatability, "repeatability");
        rewardIds = List.copyOf(Objects.requireNonNull(rewardIds, "reward IDs"));
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Milestone display name cannot be blank");
        }
        boolean providerTrigger = triggerType == MilestoneTriggerType.PROVIDER_METRIC;
        if (providerTrigger != (providerId.isPresent() && providerMetricId.isPresent())) {
            throw new IllegalArgumentException("Provider milestones require provider and metric IDs exclusively");
        }
        if (rewardIds.stream().distinct().count() != rewardIds.size()) {
            throw new IllegalArgumentException("Milestone reward IDs must be unique");
        }
    }
}
