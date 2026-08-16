package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record MetricBinding(ProviderId providerId, MetricId metricId) {
    public MetricBinding {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
    }
}
