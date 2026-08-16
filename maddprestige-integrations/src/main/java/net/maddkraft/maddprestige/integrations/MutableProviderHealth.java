package net.maddkraft.maddprestige.integrations;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

/** Thread-safe health state shared by an adapter and its dependency lifecycle binding. */
public final class MutableProviderHealth {
    private final Clock clock;
    private final AtomicReference<ProviderHealth> current;

    public MutableProviderHealth(Clock clock, ProviderHealthState initial, String code, String reason) {
        this.clock = Objects.requireNonNull(clock, "clock");
        current = new AtomicReference<>(new ProviderHealth(initial, code, reason, clock.instant()));
    }

    public ProviderHealth get() {
        return current.get();
    }

    /** Phase 3/4 canonical usability rule: only AVAILABLE and ACTIVE are healthy. */
    public boolean isUsable() {
        ProviderHealthState state = current.get().state();
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    public void transition(ProviderHealthState state, String code, String reason) {
        current.set(new ProviderHealth(state, code, reason, clock.instant()));
    }
}
