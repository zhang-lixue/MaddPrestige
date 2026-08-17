package net.maddkraft.maddprestige.core.admin.setup;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record SetupProviderOption(
        ProviderId providerId,
        boolean active,
        boolean healthy,
        boolean rankCapable,
        List<String> metricIds,
        String healthReason) {
    public SetupProviderOption {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricIds = List.copyOf(Objects.requireNonNull(metricIds, "metric IDs"));
        healthReason = Objects.requireNonNull(healthReason, "health reason");
    }
}
