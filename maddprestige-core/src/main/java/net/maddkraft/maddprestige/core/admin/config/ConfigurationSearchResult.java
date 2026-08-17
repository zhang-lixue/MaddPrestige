package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.core.schema.ReloadBehavior;
import net.maddkraft.maddprestige.core.schema.SchemaValueType;

public record ConfigurationSearchResult(
        String canonicalPath,
        SchemaValueType type,
        String currentValue,
        String description,
        ReloadBehavior reloadBehavior,
        List<String> validationCodes) {
    public ConfigurationSearchResult {
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonical path");
        type = Objects.requireNonNull(type, "type");
        currentValue = Objects.requireNonNull(currentValue, "current value");
        description = Objects.requireNonNull(description, "description");
        reloadBehavior = Objects.requireNonNull(reloadBehavior, "reload behavior");
        validationCodes = List.copyOf(Objects.requireNonNull(validationCodes, "validation codes"));
    }
}
