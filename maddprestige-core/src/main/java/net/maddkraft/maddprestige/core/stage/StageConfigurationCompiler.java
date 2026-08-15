package net.maddkraft.maddprestige.core.stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public final class StageConfigurationCompiler {
    private static final String DOCUMENT = "progression.yml";
    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "schema-version", "active", "stages", "order", "baseline", "reconciliation-policy");
    private static final Set<String> STAGE_KEYS = Set.of(
            "enabled", "display-name", "display-metadata", "projection");
    private static final Set<String> DEFERRED_STAGE_KEYS = Set.of(
            "requirements", "costs", "rewards", "actions", "entry-actions", "completion-actions",
            "prestige", "scaling", "permission-predicates", "gui");
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setLabel(DOCUMENT)
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(50)
            .setCodePointLimit(4 * 1024 * 1024)
            .build();

    public StageConfigurationCompilation compile(CompiledConfiguration compiled) {
        String source = compiled.documents().get(DOCUMENT);
        if (source == null) {
            return failure("stage.document.missing", DOCUMENT, "The canonical progression document is missing.",
                    "Add progression.yml to the same configuration draft.");
        }
        Object loaded;
        try {
            loaded = new Load(SETTINGS).loadFromString(source);
        } catch (RuntimeException exception) {
            return failure("stage.yaml.invalid", DOCUMENT, "Could not parse progression.yml: " + exception.getMessage(),
                    "Correct the YAML syntax and duplicate keys before applying.");
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            return failure("stage.document.type", DOCUMENT, "progression.yml must contain one top-level mapping.",
                    "Use mapping keys such as active, stages, and order.");
        }

        ArrayList<ValidationFinding> findings = new ArrayList<>();
        reportUnknownKeys(root, TOP_LEVEL_KEYS, "progression", findings);
        int schemaVersion = integer(root.get("schema-version"), "progression.schema-version", 2, findings);
        boolean active = bool(root.get("active"), "progression.active", false, findings);
        ReconciliationPolicy reconciliation = reconciliation(root.get("reconciliation-policy"), findings);
        Map<StageId, StageDefinition> stages = stages(root.get("stages"), findings);
        List<StageId> order = order(root.get("order"), findings);
        Optional<StageId> baseline = optionalStageId(root.get("baseline"), "progression.baseline", findings);
        StageConfiguration configuration = new StageConfiguration(
                Math.max(1, schemaVersion), active, stages, order, baseline, reconciliation);
        validateSemantics(configuration, findings);
        return new StageConfigurationCompilation(Optional.of(configuration), ValidationReport.of(findings));
    }

    private static Map<StageId, StageDefinition> stages(
            Object value, List<ValidationFinding> findings) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> stageMap)) {
            findings.add(error("stage.definitions.type", "progression.stages",
                    "Stages must be a mapping keyed by immutable stage ID.", "Use stages: {stage_id: {...}}."));
            return Map.of();
        }
        LinkedHashMap<StageId, StageDefinition> stages = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : stageMap.entrySet()) {
            String rawId = scalarString(entry.getKey());
            if (rawId == null) {
                findings.add(error("stage.id.type", "progression.stages",
                        "Every stage key must be a scalar immutable ID.", "Use lowercase a-z0-9._- stage IDs."));
                continue;
            }
            StageId id;
            try {
                id = new StageId(rawId);
            } catch (IllegalArgumentException exception) {
                findings.add(error("stage.id.invalid", "progression.stages." + rawId,
                        exception.getMessage(), "Use a normalized immutable ID containing only a-z0-9._-."));
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> fields)) {
                findings.add(error("stage.definition.type", "progression.stages." + rawId,
                        "A stage definition must be a mapping.", "Define enabled, display-name, and projection."));
                continue;
            }
            reportStageKeys(fields, rawId, findings);
            boolean enabled = bool(fields.get("enabled"), path(rawId, "enabled"), true, findings);
            String displayName = requiredString(fields.get("display-name"), path(rawId, "display-name"), findings);
            Map<String, String> metadata = stringMap(
                    fields.get("display-metadata"), path(rawId, "display-metadata"), findings);
            StageProjection projection = projection(fields.get("projection"), rawId, findings);
            if (displayName == null) {
                displayName = rawId;
            }
            stages.put(id, new StageDefinition(id, enabled, displayName, metadata, projection));
        }
        return stages;
    }

    private static StageProjection projection(Object value, String stageId, List<ValidationFinding> findings) {
        String projectionPath = path(stageId, "projection");
        if (value instanceof String scalar) {
            if ("none".equals(scalar)) {
                return StageProjection.none();
            }
            findings.add(error("stage.projection.invalid", projectionPath,
                    "Scalar projection must be exactly 'none'.",
                    "Use projection: none or a provider/group mapping."));
            return StageProjection.none();
        }
        if (!(value instanceof Map<?, ?> projection)) {
            findings.add(error("stage.projection.missing", projectionPath,
                    "Every stage needs an explicit projection.",
                    "Use projection: none or configure an existing rank-provider group."));
            return StageProjection.none();
        }
        reportUnknownKeys(projection, Set.of("type", "provider", "group"), projectionPath, findings);
        String type = null;
        if (projection.containsKey("type")) {
            Object rawType = projection.get("type");
            if (!(rawType instanceof String scalarType) || scalarType.isBlank()) {
                findings.add(error("stage.projection.type.invalid", projectionPath + ".type",
                        "Projection type must be the scalar value 'none' or 'group'.",
                        "Use type: none without provider/group, or type: group with provider/group."));
                return StageProjection.none();
            }
            type = scalarType;
            if (!"none".equals(type) && !"group".equals(type)) {
                findings.add(error("stage.projection.type.invalid", projectionPath + ".type",
                        "Unsupported projection type '" + type + "'.",
                        "Use type: none or type: group; omit type only for the documented provider/group shorthand."));
                return StageProjection.none();
            }
        }
        if ("none".equals(type)) {
            if (projection.containsKey("provider") || projection.containsKey("group")) {
                findings.add(error("stage.projection.none.extra", projectionPath,
                        "Projection type none cannot also name a provider or group.",
                        "Remove provider/group or choose type: group."));
            }
            return StageProjection.none();
        }
        String provider = requiredString(projection.get("provider"), projectionPath + ".provider", findings);
        String group = requiredString(projection.get("group"), projectionPath + ".group", findings);
        if (provider == null || group == null) {
            return StageProjection.none();
        }
        try {
            return StageProjection.group(new ProviderId(provider), group);
        } catch (IllegalArgumentException exception) {
            findings.add(error("stage.projection.provider.invalid", projectionPath + ".provider",
                    exception.getMessage(), "Use a normalized registered provider ID."));
            return StageProjection.none();
        }
    }

    private static List<StageId> order(Object value, List<ValidationFinding> findings) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            findings.add(error("stage.order.type", "progression.order",
                    "Stage order must be a YAML list of immutable IDs.", "Use order: [first, second]."));
            return List.of();
        }
        ArrayList<StageId> order = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String raw = scalarString(values.get(index));
            if (raw == null) {
                findings.add(error("stage.order.entry.type", "progression.order[" + index + "]",
                        "Order entries must be scalar stage IDs.", "Use a defined normalized stage ID."));
                continue;
            }
            try {
                order.add(new StageId(raw));
            } catch (IllegalArgumentException exception) {
                findings.add(error("stage.order.entry.invalid", "progression.order[" + index + "]",
                        exception.getMessage(), "Use a normalized immutable stage ID."));
            }
        }
        return order;
    }

    private static void validateSemantics(StageConfiguration configuration, List<ValidationFinding> findings) {
        if (!configuration.active() && configuration.stages().isEmpty() && configuration.order().isEmpty()
                && configuration.baselineStage().isEmpty()) {
            return;
        }
        LinkedHashSet<StageId> uniqueOrder = new LinkedHashSet<>();
        for (StageId stageId : configuration.order()) {
            if (!uniqueOrder.add(stageId)) {
                findings.add(error("stage.order.duplicate", "progression.order",
                        "Stage order contains the duplicate ID '" + stageId.value() + "'.",
                        "List every enabled stage exactly once."));
            }
            StageDefinition stage = configuration.stages().get(stageId);
            if (stage == null) {
                findings.add(error("stage.order.undefined", "progression.order",
                        "Stage order references undefined ID '" + stageId.value() + "'.",
                        "Define the stage or remove it from order."));
            } else if (!stage.enabled()) {
                findings.add(error("stage.order.disabled", "progression.order",
                        "Disabled stage '" + stageId.value() + "' appears in active order.",
                        "Enable the stage or remove it from order."));
            }
        }
        for (StageDefinition stage : configuration.stages().values()) {
            if (stage.enabled() && !uniqueOrder.contains(stage.id())) {
                findings.add(error("stage.order.omits_enabled", "progression.order",
                        "Enabled stage '" + stage.id().value() + "' is omitted from order.",
                        "Add every enabled stage exactly once."));
            }
        }
        if (configuration.active() && configuration.baselineStage().isEmpty()) {
            findings.add(error("stage.baseline.missing", "progression.baseline",
                    "An active ladder requires an explicit baseline stage.",
                    "Set baseline to one enabled stage ID."));
        }
        configuration.baselineStage().ifPresent(baseline -> {
            StageDefinition definition = configuration.stages().get(baseline);
            if (definition == null || !definition.enabled() || !uniqueOrder.contains(baseline)) {
                findings.add(error("stage.baseline.invalid", "progression.baseline",
                        "Baseline must reference an enabled ordered stage.",
                        "Choose an enabled stage present in order."));
            }
        });
        Set<ProviderId> providers = configuration.stages().values().stream()
                .map(StageDefinition::projection)
                .map(StageProjection::providerId)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toSet());
        if (providers.size() > 1) {
            findings.add(error("stage.projection.multiple_providers", "progression.stages",
                    "Phase 2 supports one rank adapter per ordered ladder, but found " + providers.size() + ".",
                    "Use one rank provider for projected stages; projection: none remains available for baseline stages."));
        }
        Map<String, Long> groupUse = configuration.stages().values().stream()
                .filter(StageDefinition::enabled)
                .map(StageDefinition::projection)
                .map(StageProjection::groupName)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.groupingBy(group -> group, java.util.stream.Collectors.counting()));
        groupUse.forEach((group, count) -> {
            if (count > 1) {
                findings.add(error("stage.projection.group.duplicate", "progression.stages",
                        "External group '" + group + "' maps to multiple enabled stages.",
                        "Map each managed group to one immutable stage ID."));
            }
        });
    }

    private static ReconciliationPolicy reconciliation(Object value, List<ValidationFinding> findings) {
        String raw = value == null ? "warn-only" : scalarString(value);
        if (raw == null) {
            findings.add(error("stage.reconciliation.type", "progression.reconciliation-policy",
                    "Reconciliation policy must be a scalar value.", "Use warn-only, maddprestige-authoritative, or import-once."));
            return ReconciliationPolicy.WARN_ONLY;
        }
        return switch (raw) {
            case "warn-only" -> ReconciliationPolicy.WARN_ONLY;
            case "maddprestige-authoritative" -> ReconciliationPolicy.MADD_PRESTIGE_AUTHORITATIVE;
            case "import-once" -> ReconciliationPolicy.IMPORT_ONCE;
            default -> {
                findings.add(error("stage.reconciliation.invalid", "progression.reconciliation-policy",
                        "Unsupported reconciliation policy '" + raw + "'.",
                        "Use warn-only, maddprestige-authoritative, or import-once."));
                yield ReconciliationPolicy.WARN_ONLY;
            }
        };
    }

    private static Optional<StageId> optionalStageId(
            Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return Optional.empty();
        }
        String raw = scalarString(value);
        if (raw == null) {
            findings.add(error("stage.baseline.type", path, "Baseline must be a scalar stage ID.",
                    "Choose one defined enabled stage ID."));
            return Optional.empty();
        }
        try {
            return Optional.of(new StageId(raw));
        } catch (IllegalArgumentException exception) {
            findings.add(error("stage.baseline.id.invalid", path, exception.getMessage(),
                    "Use a normalized immutable stage ID."));
            return Optional.empty();
        }
    }

    private static Map<String, String> stringMap(
            Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            findings.add(error("stage.display_metadata.type", path,
                    "Display metadata must be a scalar key/value mapping.", "Use keys such as icon: stone."));
            return Map.of();
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> {
            String stringKey = scalarString(key);
            String stringValue = scalarString(entryValue);
            if (stringKey == null || stringValue == null) {
                findings.add(error("stage.display_metadata.entry.type", path,
                        "Display metadata keys and values must be scalar text.", "Replace nested values with scalar metadata."));
            } else {
                result.put(stringKey, stringValue);
            }
        });
        return result;
    }

    private static void reportStageKeys(Map<?, ?> fields, String stageId, List<ValidationFinding> findings) {
        for (Object rawKey : fields.keySet()) {
            String key = scalarString(rawKey);
            if (key == null) {
                findings.add(error("stage.field.key.type", "progression.stages." + stageId,
                        "Stage field keys must be scalar text.", "Use documented stage field names."));
            } else if (DEFERRED_STAGE_KEYS.contains(key)) {
                findings.add(error("stage.feature.unsupported_phase2", path(stageId, key),
                        "The configured '" + key + "' feature is not active in Phase 2.",
                        "Remove it from an active draft and configure it only after its owning phase is implemented."));
            } else if (!STAGE_KEYS.contains(key)) {
                findings.add(warning("stage.field.unknown", path(stageId, key),
                        "Unknown stage field '" + key + "' is preserved but not activated.",
                        "Confirm the field belongs to a supported schema before relying on it."));
            }
        }
    }

    private static void reportUnknownKeys(
            Map<?, ?> map, Set<String> allowed, String basePath, List<ValidationFinding> findings) {
        for (Object rawKey : map.keySet()) {
            String key = scalarString(rawKey);
            if (key == null) {
                findings.add(error("stage.field.key.type", basePath,
                        "Configuration keys must be scalar text.", "Use documented scalar field names."));
            } else if (!allowed.contains(key)) {
                findings.add(warning("stage.field.unknown", basePath + "." + key,
                        "Unknown field '" + key + "' is preserved but not activated.",
                        "Confirm the field belongs to a supported schema before relying on it."));
            }
        }
    }

    private static boolean bool(Object value, String path, boolean defaultValue, List<ValidationFinding> findings) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        findings.add(error("stage.boolean.invalid", path, "Expected true or false.", "Use an unquoted YAML boolean."));
        return defaultValue;
    }

    private static int integer(Object value, String path, int defaultValue, List<ValidationFinding> findings) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        findings.add(error("stage.integer.invalid", path, "Expected a whole number.", "Use a positive integer."));
        return defaultValue;
    }

    private static String requiredString(Object value, String path, List<ValidationFinding> findings) {
        String result = scalarString(value);
        if (result == null || result.isBlank()) {
            findings.add(error("stage.string.missing", path, "A nonblank scalar value is required.",
                    "Provide a nonblank text value."));
            return null;
        }
        return result;
    }

    private static String scalarString(Object value) {
        return value instanceof String string ? string : null;
    }

    private static String path(String stageId, String field) {
        return "progression.stages." + stageId + "." + field;
    }

    private static StageConfigurationCompilation failure(
            String code, String path, String explanation, String remediation) {
        return new StageConfigurationCompilation(Optional.empty(), ValidationReport.of(List.of(
                error(code, path, explanation, remediation))));
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return finding(code, ValidationSeverity.ERROR, path, explanation, remediation);
    }

    private static ValidationFinding warning(String code, String path, String explanation, String remediation) {
        return finding(code, ValidationSeverity.WARNING, path, explanation, remediation);
    }

    private static ValidationFinding finding(
            String code, ValidationSeverity severity, String path, String explanation, String remediation) {
        return new ValidationFinding(code, severity, path, explanation,
                "Unsupported or ambiguous configuration never becomes active silently.", remediation);
    }
}
