package net.maddkraft.maddprestige.api.provider;

import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * A bounded filter dimension advertised by an owner-attested requirement provider.
 *
 * @param id provider-local canonical dimension identity, 1-32 characters
 * @param required whether every request must supply the dimension
 * @param allowedValues immutable allowed-value set, at most 128 bounded values; empty means provider-defined values
 * @param descriptionKey canonical localization key, 1-128 characters
 */
@Stable
public record ProviderMetricDimension(
        String id,
        boolean required,
        Set<String> allowedValues,
        String descriptionKey) {
    public ProviderMetricDimension {
        id = bounded(id, 32, "dimension ID");
        descriptionKey = bounded(descriptionKey, 128, "dimension description key");
        allowedValues = Set.copyOf(Objects.requireNonNull(allowedValues, "allowed values"));
        if (!id.matches("[a-z0-9][a-z0-9_-]{0,31}") || allowedValues.size() > 128
                || !descriptionKey.matches("[a-z0-9][a-z0-9._-]*")
                || allowedValues.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 128
                        || value.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Metric dimension is outside stable bounds");
        }
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is outside stable bounds");
        }
        return value;
    }
}
