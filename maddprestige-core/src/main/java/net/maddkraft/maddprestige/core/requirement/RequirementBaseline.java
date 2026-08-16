package net.maddkraft.maddprestige.core.requirement;

import java.time.Instant;
import java.util.Objects;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record RequirementBaseline(BaselineKey key, MetricValue value, long providerGeneration, Instant createdAt) {
    public RequirementBaseline {
        key = Objects.requireNonNull(key, "key");
        value = Objects.requireNonNull(value, "value");
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
        createdAt = Objects.requireNonNull(createdAt, "created at");
    }
}
