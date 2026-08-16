package net.maddkraft.maddprestige.core.manual;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.metric.MetricValue;

/** Provider-, metric-, owner-capability-, and generation-scoped mutation handle. */
public final class ManualMetricHandle {
    private final ManualProgressProvider provider;
    private final ManualMetricRegistration registration;
    private final UUID capability;

    ManualMetricHandle(
            ManualProgressProvider provider,
            ManualMetricRegistration registration,
            UUID capability) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.registration = Objects.requireNonNull(registration, "registration");
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    public ManualMetricRegistration registration() {
        return registration;
    }

    public MetricValue increment(UUID playerId, MetricValue amount, String source, Instant observedAt) {
        return provider.increment(registration, playerId, amount,
                provider.attest(capability, source, observedAt));
    }

    public MetricValue set(UUID playerId, MetricValue value, String source, Instant observedAt) {
        return provider.set(registration, playerId, value,
                provider.attest(capability, source, observedAt));
    }
}
