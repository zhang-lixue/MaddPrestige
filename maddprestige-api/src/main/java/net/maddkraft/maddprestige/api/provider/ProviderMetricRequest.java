package net.maddkraft.maddprestige.api.provider;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;

/**
 * One bounded, generation-free metric request delivered to a third-party provider.
 *
 * @param metricId exact advertised metric identity
 * @param readMode requested current or lifetime read
 * @param filters immutable dimension filter snapshot, at most 16 bounded entries
 */
@Stable
public record ProviderMetricRequest(MetricId metricId, MetricReadMode readMode, Map<String, String> filters) {
    public ProviderMetricRequest {
        metricId = Objects.requireNonNull(metricId, "metric ID");
        readMode = Objects.requireNonNull(readMode, "read mode");
        filters = Map.copyOf(Objects.requireNonNull(filters, "filters"));
        if (filters.size() > 16 || filters.entrySet().stream().anyMatch(entry -> !bounded(entry.getKey(), 32)
                || !bounded(entry.getValue(), 128))) {
            throw new IllegalArgumentException("Metric request is outside stable bounds");
        }
    }

    private static boolean bounded(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum
                && value.chars().noneMatch(Character::isISOControl);
    }
}
