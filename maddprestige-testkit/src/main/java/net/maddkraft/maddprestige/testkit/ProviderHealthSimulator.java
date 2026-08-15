package net.maddkraft.maddprestige.testkit;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

public final class ProviderHealthSimulator {
    private final Clock clock;
    private final AtomicReference<ProviderHealth> health;

    public ProviderHealthSimulator(Clock clock, ProviderHealthState initial) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.health = new AtomicReference<>(new ProviderHealth(initial, "fake.initial", "Initial fake state", clock.instant()));
    }

    public ProviderHealth current() {
        return health.get();
    }

    public void transition(ProviderHealthState state, String code, String reason) {
        health.set(new ProviderHealth(state, code, reason, clock.instant()));
    }
}
