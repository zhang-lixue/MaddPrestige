package net.maddkraft.maddprestige.api.service;

import java.time.Instant;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

/**
 * Immutable provider health view without registry tokens, generations, or implementation objects.
 *
 * @param id canonical owner-attested provider identity
 * @param health coarse cached health at observation time
 * @param healthCode bounded machine code, never rendered English text
 * @param observedAt non-null observation time; the snapshot is not live after construction
 */
public record ProviderView(
        ProviderId id,
        ProviderHealthState health,
        String healthCode,
        Instant observedAt) {
    public ProviderView {
        id = Objects.requireNonNull(id, "provider ID");
        health = Objects.requireNonNull(health, "health");
        healthCode = requireText(healthCode, 64, "health code");
        observedAt = Objects.requireNonNull(observedAt, "observation time");
    }

    private static String requireText(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximum || !value.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(name + " is outside stable bounds");
        }
        return value;
    }
}
