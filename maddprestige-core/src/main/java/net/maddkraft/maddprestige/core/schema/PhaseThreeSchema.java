package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class PhaseThreeSchema {
    private PhaseThreeSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseTwoSchema.create();
        register(registry, "stage_requirement_tree", "progression.stages.*.requirements", SchemaValueType.STRING,
                Optional.empty(), "Stable reference to one reusable recursive requirement tree.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "stage_costs", "progression.stages.*.costs", SchemaValueType.LIST,
                Optional.empty(), "Stable cost IDs consumed only by a consequential operation.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "stage_rewards", "progression.stages.*.rewards", SchemaValueType.LIST,
                Optional.empty(), "Stable provider-driven reward IDs planned after preflight.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_definitions", "requirements.requirements", SchemaValueType.MAP,
                Optional.empty(), "Typed metric requirements with scopes, completion, scaling, and catch-up.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_trees", "requirements.trees", SchemaValueType.MAP,
                Optional.empty(), "Bounded recursive ALL/ANY/X_OF_N/WEIGHTED requirement trees.",
                AllowedValues.fixed("ALL", "ANY", "X_OF_N", "WEIGHTED"), RiskLevel.HIGH);
        register(registry, "cost_definitions", "requirements.costs", SchemaValueType.MAP,
                Optional.empty(), "Provider cost definitions; preflight and evaluation never consume them.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "reward_definitions", "rewards.rewards", SchemaValueType.MAP,
                Optional.empty(), "Provider reward definitions with failure/repeatability policies.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "command_actions_enabled", "rewards.command-actions.enabled", SchemaValueType.BOOLEAN,
                Optional.of("false"), "Enables reviewed command templates; disabled by default.",
                AllowedValues.fixed("true", "false"), RiskLevel.CRITICAL);
        register(registry, "command_action_templates", "rewards.command-actions.templates", SchemaValueType.MAP,
                Optional.empty(), "Allowlisted single-line command templates with structured tokens.",
                AllowedValues.unrestricted(), RiskLevel.CRITICAL);
        return registry;
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
                values, false, risk, "maddprestige.admin.config.edit",
                "maddprestige.admin.config.apply", ReloadBehavior.HOT_RELOAD, List.of(), Optional.empty()));
    }
}
