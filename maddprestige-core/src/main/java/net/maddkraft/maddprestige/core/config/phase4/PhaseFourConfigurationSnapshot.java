package net.maddkraft.maddprestige.core.config.phase4;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record PhaseFourConfigurationSnapshot(
        ConfigRevisionId revisionId,
        PhaseFourConfiguration configuration,
        Map<ProviderId, Long> providerGenerations) {
    public PhaseFourConfigurationSnapshot {
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        configuration = Objects.requireNonNull(configuration, "configuration");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        if (providerGenerations.values().stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("Pinned provider generations must be positive");
        }
    }
}
