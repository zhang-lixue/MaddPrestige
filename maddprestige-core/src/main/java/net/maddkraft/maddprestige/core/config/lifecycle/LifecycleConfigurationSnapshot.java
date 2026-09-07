package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record LifecycleConfigurationSnapshot(
        ConfigRevisionId revisionId,
        LifecycleConfiguration configuration,
        Map<ProviderId, Long> providerGenerations) {
    public LifecycleConfigurationSnapshot {
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        configuration = Objects.requireNonNull(configuration, "configuration");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        if (providerGenerations.values().stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("Pinned provider generations must be positive");
        }
    }
}
