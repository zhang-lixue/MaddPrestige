package net.maddkraft.maddprestige.core.manual;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;

/** Opaque registration binding; only the owning provider can issue one. */
public final class ManualMetricRegistration {
    private final ProviderId providerId;
    private final MetricId metricId;
    private final String ownerIdentity;
    private final long generation;
    private final UUID token;

    ManualMetricRegistration(
            ProviderId providerId,
            MetricId metricId,
            String ownerIdentity,
            long generation,
            UUID token) {
        this.providerId = Objects.requireNonNull(providerId, "provider ID");
        this.metricId = Objects.requireNonNull(metricId, "metric ID");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
        if (generation < 1) {
            throw new IllegalArgumentException("Registration generation must be positive");
        }
        this.generation = generation;
        this.token = Objects.requireNonNull(token, "token");
    }

    public ProviderId providerId() {
        return providerId;
    }

    public MetricId metricId() {
        return metricId;
    }

    public String ownerIdentity() {
        return ownerIdentity;
    }

    public long generation() {
        return generation;
    }

    UUID token() {
        return token;
    }
}
