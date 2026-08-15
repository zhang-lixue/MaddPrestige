package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

public final class StageRuntimeBootstrap {
    public StageRuntimeStatus inspect(
            Optional<StageConfigurationSnapshot> activeConfiguration,
            ProviderRegistry providers) {
        Objects.requireNonNull(activeConfiguration, "active configuration");
        Objects.requireNonNull(providers, "provider registry");
        if (activeConfiguration.isEmpty() || !activeConfiguration.orElseThrow().configuration().active()) {
            return new StageRuntimeStatus(StageRuntimeState.INACTIVE, Optional.empty(),
                    "No active progression ladder is configured; no rank adapter is required.");
        }
        StageConfigurationSnapshot snapshot = activeConfiguration.orElseThrow();
        var providerId = snapshot.configuration().rankProvider();
        if (providerId.isEmpty()) {
            return new StageRuntimeStatus(StageRuntimeState.ACTIVE, Optional.of(snapshot.revisionId()),
                    "The active ladder uses internal stages with no external rank projection.");
        }
        var provider = providers.find(providerId.orElseThrow());
        if (provider.isEmpty()
                || provider.orElseThrow().activation() != ActivationState.ACTIVE
                || !healthy(provider.orElseThrow().health().state())) {
            return new StageRuntimeStatus(StageRuntimeState.UNAVAILABLE, Optional.of(snapshot.revisionId()),
                    "The configured rank adapter is unavailable; progression projection is fail-closed.");
        }
        return new StageRuntimeStatus(StageRuntimeState.ACTIVE, Optional.of(snapshot.revisionId()),
                "The active stage configuration and rank adapter are available.");
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }
}
