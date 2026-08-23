package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.schema.ReloadBehavior;
import net.maddkraft.maddprestige.core.schema.RiskLevel;
import net.maddkraft.maddprestige.core.schema.SchemaValueType;

public record ConfigurationExplanation(
        String canonicalPath,
        String descriptionIdentity,
        SchemaValueType type,
        Optional<String> currentValue,
        Optional<String> defaultValue,
        String description,
        List<String> allowedValues,
        List<String> examples,
        RiskLevel risk,
        ReloadBehavior reloadBehavior,
        String editPermission,
        String applyPermission) {
    public ConfigurationExplanation {
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonical path");
        descriptionIdentity = Objects.requireNonNull(descriptionIdentity, "description identity");
        type = Objects.requireNonNull(type, "type");
        currentValue = Objects.requireNonNull(currentValue, "current value");
        defaultValue = Objects.requireNonNull(defaultValue, "default value");
        description = Objects.requireNonNull(description, "description");
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowed values"));
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        risk = Objects.requireNonNull(risk, "risk");
        reloadBehavior = Objects.requireNonNull(reloadBehavior, "reload behavior");
        editPermission = Objects.requireNonNull(editPermission, "edit permission");
        applyPermission = Objects.requireNonNull(applyPermission, "apply permission");
    }
}
