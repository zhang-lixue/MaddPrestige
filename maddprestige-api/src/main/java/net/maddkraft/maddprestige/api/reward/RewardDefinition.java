package net.maddkraft.maddprestige.api.reward;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record RewardDefinition(
        RewardId id,
        ProviderId providerId,
        String type,
        MetricValue value,
        Map<String, String> metadata,
        String displayName,
        RewardFailurePolicy failurePolicy,
        RewardRepeatability repeatability) {
    public RewardDefinition {
        id = Objects.requireNonNull(id, "reward ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        type = Objects.requireNonNull(type, "reward type");
        value = Objects.requireNonNull(value, "value");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        displayName = Objects.requireNonNull(displayName, "display name");
        failurePolicy = Objects.requireNonNull(failurePolicy, "failure policy");
        repeatability = Objects.requireNonNull(repeatability, "repeatability");
    }
}
