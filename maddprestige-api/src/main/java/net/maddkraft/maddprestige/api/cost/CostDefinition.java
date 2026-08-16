package net.maddkraft.maddprestige.api.cost;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record CostDefinition(
        CostId id,
        ProviderId providerId,
        String type,
        MetricValue amount,
        Map<String, String> metadata,
        String displayName) {
    public CostDefinition {
        id = Objects.requireNonNull(id, "cost ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        type = Objects.requireNonNull(type, "cost type");
        amount = Objects.requireNonNull(amount, "amount");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        displayName = Objects.requireNonNull(displayName, "display name");
    }
}
