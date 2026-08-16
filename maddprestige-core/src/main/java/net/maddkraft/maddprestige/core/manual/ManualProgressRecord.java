package net.maddkraft.maddprestige.core.manual;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record ManualProgressRecord(
        ProviderId providerId,
        MetricId metricId,
        UUID playerId,
        MetricValue value,
        long updateVersion,
        String provenance,
        Instant updatedAt) {
    public ManualProgressRecord {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        value = Objects.requireNonNull(value, "value");
        if (updateVersion < 0) {
            throw new IllegalArgumentException("Update version cannot be negative");
        }
        provenance = Objects.requireNonNull(provenance, "provenance");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
    }
}
