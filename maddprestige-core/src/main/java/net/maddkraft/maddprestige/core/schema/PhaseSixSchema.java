package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class PhaseSixSchema {
    private PhaseSixSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseFourSchema.create();
        removeLegacyStageSchema(registry);
        extend(registry);
        return registry;
    }

    public static void extend(SchemaRegistry registry) {
        register(registry, "requirement_provider", "requirements.requirements.*.provider",
                SchemaValueType.STRING, Optional.empty(), "Provider owning the canonical metric.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_metric", "requirements.requirements.*.metric",
                SchemaValueType.STRING, Optional.empty(), "Canonical provider metric ID.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_value_type", "requirements.requirements.*.value-type",
                SchemaValueType.ENUM, Optional.empty(), "Exact target value type.",
                AllowedValues.fixed("INTEGER_COUNT", "EXACT_DECIMAL", "DURATION", "CURRENCY_AMOUNT"),
                RiskLevel.HIGH);
        register(registry, "requirement_operator", "requirements.requirements.*.operator",
                SchemaValueType.ENUM, Optional.of("GREATER_OR_EQUAL"), "Typed target comparison operator.",
                AllowedValues.fixed("EQUAL", "GREATER_OR_EQUAL", "LESS_OR_EQUAL"), RiskLevel.HIGH);
        register(registry, "requirement_target", "requirements.requirements.*.target",
                SchemaValueType.STRING, Optional.empty(), "Base target before numeric Prestige scaling.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_measurement_scope", "requirements.requirements.*.measurement-scope",
                SchemaValueType.ENUM, Optional.of("absolute"),
                "Controls which observation window counts: current/lifetime uses the provider value, while "
                        + "since-prestige-start and since-season-start count only change after "
                        + "the saved boundary. A provider must advertise support for the selected scope.",
                AllowedValues.fixed("absolute", "lifetime", "since-prestige-start",
                        "since-season-start"), RiskLevel.HIGH);
        register(registry, "requirement_completion_mode", "requirements.requirements.*.completion-mode",
                SchemaValueType.ENUM, Optional.of("live"),
                "Live requirements follow the current value; latched requirements stay complete only inside the "
                        + "same saved scope after the canonical lifecycle records completion.",
                AllowedValues.fixed("live", "latched"), RiskLevel.HIGH);
        register(registry, "requirement_tree_mode", "requirements.trees.*.mode", SchemaValueType.ENUM,
                Optional.of("ALL"), "Recursive requirement group mode.",
                AllowedValues.fixed("ALL", "ANY", "X_OF_N", "WEIGHTED"), RiskLevel.HIGH);
        register(registry, "requirement_tree_threshold", "requirements.trees.*.threshold", SchemaValueType.DECIMAL,
                Optional.empty(), "X_OF_N count or WEIGHTED threshold.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "prestige_cost_scaling", "prestige.cost-scaling", SchemaValueType.MAP,
                Optional.empty(), "Per-cost numeric Prestige scaling profiles.", AllowedValues.unrestricted(),
                RiskLevel.CRITICAL);
        register(registry, "prestige_reward_scaling", "prestige.reward-scaling", SchemaValueType.MAP,
                Optional.empty(), "Per-reward numeric Prestige scaling profiles.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        scalingFamily(registry, "requirement_scaling", "requirements.requirements.*.scaling");
        scalingFamily(registry, "cost_scaling", "prestige.cost-scaling.*");
        scalingFamily(registry, "reward_scaling", "prestige.reward-scaling.*");
    }

    private static void removeLegacyStageSchema(SchemaRegistry registry) {
        List.of("progression.active", "progression.baseline", "progression.order",
                "progression.stages.*.enabled", "progression.stages.*.display-name",
                "progression.stages.*.projection", "progression.stages.*.requirements",
                "progression.stages.*.costs", "progression.stages.*.rewards",
                "prestige.required-stages", "prestige.reset-stage", "prestige.current-count-increment",
                "prestige.lifetime-count-increment", "prestige.scaling-profile", "prestige.catch-up-profile")
                .forEach(registry::unregister);
    }

    private static void scalingFamily(SchemaRegistry registry, String id, String path) {
        register(registry, id + "_segments", path + ".segments", SchemaValueType.LIST, Optional.empty(),
                "Contiguous scaling segments beginning at Prestige 1.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, id + "_start", path + ".segments.*.start-prestige", SchemaValueType.INTEGER,
                Optional.empty(), "Inclusive positive segment start.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_end", path + ".segments.*.end-prestige", SchemaValueType.STRING,
                Optional.empty(), "Inclusive finite end or unlimited.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_mode", path + ".segments.*.mode", SchemaValueType.ENUM, Optional.of("FLAT"),
                "Bounded segment formula.", AllowedValues.fixed("FLAT", "LINEAR", "EXPONENTIAL", "MANUAL"),
                RiskLevel.HIGH);
        register(registry, id + "_transition", path + ".segments.*.transition", SchemaValueType.ENUM,
                Optional.of("EXPLICIT_BASE"), "Explicit base or continuity from the preceding segment.",
                AllowedValues.fixed("EXPLICIT_BASE", "CONTINUE"), RiskLevel.HIGH);
        register(registry, id + "_base", path + ".segments.*.base", SchemaValueType.DECIMAL, Optional.of("0"),
                "Non-negative segment base.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_rate", path + ".segments.*.rate", SchemaValueType.DECIMAL, Optional.of("0"),
                "Non-negative linear delta or exponential factor.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_rounding", path + ".segments.*.rounding", SchemaValueType.ENUM,
                Optional.of("EXACT"), "Post-formula target rounding policy.",
                AllowedValues.fixed("EXACT", "FLOOR", "CEILING", "HALF_UP"), RiskLevel.HIGH);
        register(registry, id + "_quantum", path + ".segments.*.quantum", SchemaValueType.DECIMAL,
                Optional.of("1"), "Positive bounded rounding quantum.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, id + "_floor", path + ".segments.*.floor", SchemaValueType.DECIMAL, Optional.empty(),
                "Optional non-negative lower bound.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_cap", path + ".segments.*.cap", SchemaValueType.DECIMAL, Optional.empty(),
                "Optional non-negative upper bound.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_overrides", path + ".segments.*.overrides", SchemaValueType.MAP, Optional.empty(),
                "Explicit bounded per-Prestige values.", AllowedValues.unrestricted(), RiskLevel.HIGH);
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
