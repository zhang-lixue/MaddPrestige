package net.maddkraft.maddprestige.api.metric;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MetricSample(
        MetricSampleStatus status,
        Optional<MetricValue> value,
        long providerGeneration,
        Instant observedAt,
        String provenance,
        Optional<String> detail) {
    public MetricSample {
        status = Objects.requireNonNull(status, "sample status");
        value = Objects.requireNonNull(value, "value");
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
        observedAt = Objects.requireNonNull(observedAt, "observed at");
        provenance = Objects.requireNonNull(provenance, "provenance");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == MetricSampleStatus.AVAILABLE) != value.isPresent()) {
            throw new IllegalArgumentException("Only available samples carry a value");
        }
    }

    public static MetricSample available(MetricValue value, long generation, Instant at, String provenance) {
        return new MetricSample(MetricSampleStatus.AVAILABLE, Optional.of(value), generation, at, provenance,
                Optional.empty());
    }

    public static MetricSample unavailable(long generation, Instant at, String provenance, String detail) {
        return new MetricSample(MetricSampleStatus.UNAVAILABLE, Optional.empty(), generation, at, provenance,
                Optional.of(detail));
    }
}
