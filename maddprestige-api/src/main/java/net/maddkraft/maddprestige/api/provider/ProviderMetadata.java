package net.maddkraft.maddprestige.api.provider;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Immutable callback metadata validated and cached before a provider enters the registry.
 *
 * @param localId provider-local canonical identity, 1-31 characters; every accepted value can be owner-qualified
 * @param displayNameKey canonical localization key, 1-128 characters
 * @param implementationVersion bounded non-rendered implementation version, 1-64 characters
 * @param metrics immutable advertised definitions, at most 256 with unique exact and normalized IDs
 */
@Stable
public record ProviderMetadata(
        String localId,
        String displayNameKey,
        String implementationVersion,
        List<ProviderMetricDefinition> metrics) {
    public ProviderMetadata {
        localId = bounded(localId, 31, "local ID");
        displayNameKey = machineKey(displayNameKey, 128, "display name key");
        implementationVersion = bounded(implementationVersion, 64, "implementation version");
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));
        if (!localId.matches("[a-z0-9][a-z0-9_-]{0,30}") || metrics.size() > 256) {
            throw new IllegalArgumentException("Provider metadata is outside stable bounds");
        }
        HashSet<String> exact = new HashSet<>();
        HashSet<String> normalized = new HashSet<>();
        for (ProviderMetricDefinition metric : metrics) {
            String id = Objects.requireNonNull(metric, "metric definition").metricId().value();
            if (!exact.add(id)) {
                throw new IllegalArgumentException("Provider metadata contains duplicate metric ID: " + id);
            }
            if (!normalized.add(normalized(id))) {
                throw new IllegalArgumentException("Provider metric IDs are ambiguous after normalization");
            }
        }
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is outside stable bounds");
        }
        return value;
    }

    private static String normalized(String value) {
        return value.replace('-', '_').replace('.', '_');
    }

    private static String machineKey(String value, int maximum, String name) {
        value = bounded(value, maximum, name);
        if (!value.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(name + " is outside the stable grammar");
        }
        return value;
    }
}
