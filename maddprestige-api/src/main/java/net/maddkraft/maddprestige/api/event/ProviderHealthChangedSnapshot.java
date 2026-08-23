package net.maddkraft.maddprestige.api.event;

import java.time.Instant;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

/**
 * Immutable payload emitted when cached provider health changes.
 *
 * @param providerId canonical owner-attested provider identity
 * @param previous prior cached health
 * @param current new cached health
 * @param code bounded machine reason code, never rendered English text
 * @param occurredAt non-null transition time
 */
public record ProviderHealthChangedSnapshot(
        ProviderId providerId,
        ProviderHealthState previous,
        ProviderHealthState current,
        String code,
        Instant occurredAt) {
    public ProviderHealthChangedSnapshot {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        previous = Objects.requireNonNull(previous, "previous health");
        current = Objects.requireNonNull(current, "current health");
        code = Objects.requireNonNull(code, "health code");
        occurredAt = Objects.requireNonNull(occurredAt, "occurrence time");
        if (code.isBlank() || code.length() > 64) {
            throw new IllegalArgumentException("Provider health code is outside stable bounds");
        }
    }
}
