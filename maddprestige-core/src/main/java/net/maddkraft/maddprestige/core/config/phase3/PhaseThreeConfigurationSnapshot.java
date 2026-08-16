package net.maddkraft.maddprestige.core.config.phase3;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record PhaseThreeConfigurationSnapshot(
        ConfigRevisionId revisionId,
        PhaseThreeConfiguration configuration,
        Map<ProviderId, Long> providerGenerations) {
    public PhaseThreeConfigurationSnapshot {
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        configuration = Objects.requireNonNull(configuration, "configuration");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        if (providerGenerations.values().stream().anyMatch(generation -> generation == null || generation < 1)) {
            throw new IllegalArgumentException("Pinned provider generations must be positive");
        }
    }
}
