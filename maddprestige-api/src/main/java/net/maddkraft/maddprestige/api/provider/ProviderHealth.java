package net.maddkraft.maddprestige.api.provider;

import java.time.Instant;
import java.util.Objects;

public record ProviderHealth(ProviderHealthState state, String code, String reason, Instant changedAt) {
    public ProviderHealth {
        state = Objects.requireNonNull(state, "state");
        code = Objects.requireNonNull(code, "code");
        reason = Objects.requireNonNull(reason, "reason");
        changedAt = Objects.requireNonNull(changedAt, "changed at");
    }
}
