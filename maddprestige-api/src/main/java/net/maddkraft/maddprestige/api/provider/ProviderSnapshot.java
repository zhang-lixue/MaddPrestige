package net.maddkraft.maddprestige.api.provider;

import java.util.Objects;

public record ProviderSnapshot(
        ProviderDescriptor descriptor,
        ProviderLifecycle lifecycle,
        ActivationState activation,
        ProviderHealth health,
        long generation) {
    public ProviderSnapshot {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        activation = Objects.requireNonNull(activation, "activation");
        health = Objects.requireNonNull(health, "health");
        if (generation < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
    }
}
