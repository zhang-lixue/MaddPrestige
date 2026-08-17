package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class PhaseSixSchema {
    private PhaseSixSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseFourSchema.create();
        extend(registry);
        return registry;
    }

    public static void extend(SchemaRegistry registry) {
        register(registry, "requirement_measurement_scope", "requirements.requirements.*.measurement-scope",
                SchemaValueType.ENUM, Optional.of("absolute"),
                "Controls which observation window counts: current/lifetime uses the provider value, while "
                        + "since-stage-start, since-prestige-start, and since-season-start count only change after "
                        + "the saved boundary. A provider must advertise support for the selected scope.",
                AllowedValues.fixed("absolute", "lifetime", "since-stage-start", "since-prestige-start",
                        "since-season-start"), RiskLevel.HIGH);
        register(registry, "requirement_completion_mode", "requirements.requirements.*.completion-mode",
                SchemaValueType.ENUM, Optional.of("live"),
                "Live requirements follow the current value; latched requirements stay complete only inside the "
                        + "same saved scope after the canonical lifecycle records completion.",
                AllowedValues.fixed("live", "latched"), RiskLevel.HIGH);
    }

    private static void register(
            SchemaRegistry registry,
            String id,
            String path,
            SchemaValueType type,
            Optional<String> defaultValue,
            String description,
            AllowedValues values,
            RiskLevel risk) {
        registry.register(new SchemaNode(new FieldId(id), path, type, defaultValue, description, List.of(), List.of(),
                values, false, risk, "maddprestige.admin.config.edit", "maddprestige.admin.config.apply",
                ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty()));
    }
}
