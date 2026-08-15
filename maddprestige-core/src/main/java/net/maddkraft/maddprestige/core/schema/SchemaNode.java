package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public record SchemaNode(
        FieldId id,
        String canonicalPath,
        SchemaValueType type,
        Optional<String> defaultValue,
        String description,
        List<String> examples,
        List<SchemaConstraint> constraints,
        AllowedValues allowedValues,
        boolean sensitive,
        RiskLevel risk,
        String editPermission,
        String applyPermission,
        ReloadBehavior reloadBehavior,
        List<Deprecation> deprecations,
        Optional<String> migrationMetadata) {
    public SchemaNode {
        id = Objects.requireNonNull(id, "field ID");
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonical path");
        type = Objects.requireNonNull(type, "type");
        defaultValue = Objects.requireNonNull(defaultValue, "default value");
        description = Objects.requireNonNull(description, "description");
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        allowedValues = Objects.requireNonNull(allowedValues, "allowed values");
        risk = Objects.requireNonNull(risk, "risk");
        editPermission = Objects.requireNonNull(editPermission, "edit permission");
        applyPermission = Objects.requireNonNull(applyPermission, "apply permission");
        reloadBehavior = Objects.requireNonNull(reloadBehavior, "reload behavior");
        deprecations = List.copyOf(Objects.requireNonNull(deprecations, "deprecations"));
        migrationMetadata = Objects.requireNonNull(migrationMetadata, "migration metadata");
        if (canonicalPath.isBlank() || canonicalPath.startsWith(".") || canonicalPath.endsWith(".")) {
            throw new IllegalArgumentException("Canonical schema path is invalid: " + canonicalPath);
        }
    }
}
