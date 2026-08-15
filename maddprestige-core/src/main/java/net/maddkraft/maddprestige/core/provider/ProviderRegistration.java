package net.maddkraft.maddprestige.core.provider;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record ProviderRegistration(ProviderId providerId, long generation, UUID token) {
    public ProviderRegistration {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive");
        }
        token = Objects.requireNonNull(token, "token");
    }
}
