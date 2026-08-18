package net.maddkraft.maddprestige.api.provider;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.metric.MetricValue;

/**
 * Generation-free provider result; MaddPrestige supplies authoritative registry provenance.
 *
 * @param status coarse availability outcome
 * @param value exact metric value if and only if status is {@code AVAILABLE}
 * @param observedAt non-null source observation time
 * @param code canonical machine outcome code, 1-64 characters
 * @param messageKey canonical localization key, 1-128 characters
 * @param arguments immutable localization arguments, at most 16 bounded entries
 */
@Stable
public record ProviderMetricResult(
        ProviderMetricStatus status,
        Optional<MetricValue> value,
        Instant observedAt,
        String code,
        String messageKey,
        Map<String, String> arguments) {
    public ProviderMetricResult {
        status = Objects.requireNonNull(status, "status");
        value = Objects.requireNonNull(value, "value");
        observedAt = Objects.requireNonNull(observedAt, "observed at");
        code = bounded(code, 64, "code");
        messageKey = bounded(messageKey, 128, "message key");
        arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if ((status == ProviderMetricStatus.AVAILABLE) != value.isPresent()
                || !code.matches("[a-z0-9][a-z0-9._-]{0,63}")
                || !messageKey.matches("[a-z0-9][a-z0-9._-]{0,127}") || arguments.size() > 16
                || arguments.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                        || !entry.getKey().matches("[a-z0-9][a-z0-9._-]{0,63}")
                        || entry.getValue().length() > 256
                        || entry.getValue().chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Metric result is inconsistent or outside stable bounds");
        }
    }

    /**
     * Creates an available result with canonical success codes.
     *
     * @param value non-null exact observed value
     * @param observedAt non-null source observation time
     * @return immutable available result
     */
    public static ProviderMetricResult available(MetricValue value, Instant observedAt) {
        return new ProviderMetricResult(ProviderMetricStatus.AVAILABLE, Optional.of(value), observedAt,
                "provider.available", "provider.available", Map.of());
    }

    /**
     * Creates an unavailable structured result without rendered text.
     *
     * @param observedAt non-null source observation time
     * @param code canonical machine failure code
     * @param messageKey canonical localization key
     * @param arguments immutable bounded localization arguments
     * @return immutable unavailable result
     */
    public static ProviderMetricResult unavailable(
            Instant observedAt,
            String code,
            String messageKey,
            Map<String, String> arguments) {
        return new ProviderMetricResult(ProviderMetricStatus.UNAVAILABLE, Optional.empty(), observedAt, code,
                messageKey, arguments);
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is outside stable bounds");
        }
        return value;
    }
}
