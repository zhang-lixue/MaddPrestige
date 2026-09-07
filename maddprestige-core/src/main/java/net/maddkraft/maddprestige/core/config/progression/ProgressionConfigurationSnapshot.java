package net.maddkraft.maddprestige.core.config.progression;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record ProgressionConfigurationSnapshot(
        ConfigRevisionId revisionId,
        ProgressionConfiguration configuration,
        Map<ProviderId, Long> providerGenerations) {
    public ProgressionConfigurationSnapshot {
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        configuration = Objects.requireNonNull(configuration, "configuration");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        if (providerGenerations.values().stream().anyMatch(generation -> generation == null || generation < 1)) {
            throw new IllegalArgumentException("Pinned provider generations must be positive");
        }
    }
}
