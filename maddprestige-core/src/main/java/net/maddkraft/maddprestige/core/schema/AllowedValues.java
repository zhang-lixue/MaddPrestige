package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record AllowedValues(List<String> staticValues, Optional<ProviderId> dynamicProvider) {
    public AllowedValues {
        staticValues = List.copyOf(Objects.requireNonNull(staticValues, "static values"));
        dynamicProvider = Objects.requireNonNull(dynamicProvider, "dynamic provider");
    }

    public static AllowedValues fixed(String... values) {
        return new AllowedValues(List.of(values), Optional.empty());
    }

    public static AllowedValues unrestricted() {
        return new AllowedValues(List.of(), Optional.empty());
    }
}
