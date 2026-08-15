package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class PhaseTwoSchema {
    private PhaseTwoSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseOneSchema.create();
        register(registry, "progression_active", "progression.active", SchemaValueType.BOOLEAN,
                Optional.of("false"), "Activates the revisioned ordered progression ladder.",
                AllowedValues.fixed("true", "false"), RiskLevel.HIGH);
        register(registry, "progression_baseline", "progression.baseline", SchemaValueType.STRING,
                Optional.empty(), "Immutable ID of the baseline stage.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "progression_order", "progression.order", SchemaValueType.LIST,
                Optional.empty(), "Explicit order containing every enabled stage exactly once.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "progression_stage_enabled", "progression.stages.*.enabled", SchemaValueType.BOOLEAN,
                Optional.of("true"), "Whether a configured stage participates in the ordered ladder.",
                AllowedValues.fixed("true", "false"), RiskLevel.HIGH);
        register(registry, "progression_stage_display", "progression.stages.*.display-name", SchemaValueType.STRING,
                Optional.empty(), "Display metadata independent from the immutable stage ID.",
                AllowedValues.unrestricted(), RiskLevel.LOW);
        register(registry, "progression_stage_projection", "progression.stages.*.projection", SchemaValueType.MAP,
                Optional.empty(), "Optional external rank-adapter projection; projection none is supported.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        return registry;
    }

    private static void register(
            SchemaRegistry registry,
            String id,
            String path,
            SchemaValueType type,
            Optional<String> defaultValue,
            String description,
            AllowedValues allowedValues,
            RiskLevel risk) {
        registry.register(new SchemaNode(new FieldId(id), path, type, defaultValue, description, List.of(), List.of(),
                allowedValues, false, risk, "maddprestige.admin.config.edit",
                "maddprestige.admin.config.apply", ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty()));
    }
}
