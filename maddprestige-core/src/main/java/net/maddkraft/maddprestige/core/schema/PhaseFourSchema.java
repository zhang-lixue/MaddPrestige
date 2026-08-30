package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class PhaseFourSchema {
    private PhaseFourSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PhaseThreeSchema.create();
        register(registry, "prestige_enabled", "prestige.enabled", SchemaValueType.BOOLEAN, Optional.of("false"),
                "Enables canonical Prestige planning and execution.", AllowedValues.fixed("true", "false"),
                RiskLevel.CRITICAL);
        register(registry, "prestige_required_stages", "prestige.required-stages", SchemaValueType.LIST,
                Optional.empty(), "Immutable stage IDs eligible to Prestige.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "prestige_reset_stage", "prestige.reset-stage", SchemaValueType.STRING, Optional.empty(),
                "Immutable stage ID committed by Prestige.", AllowedValues.unrestricted(), RiskLevel.CRITICAL);
        register(registry, "prestige_maximum", "prestige.maximum", SchemaValueType.STRING,
                Optional.of("unlimited"), "Finite positive maximum or unlimited.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "prestige_current_increment", "prestige.current-count-increment",
                SchemaValueType.INTEGER, Optional.of("1"), "Exact current-Prestige counter increment.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "prestige_lifetime_increment", "prestige.lifetime-count-increment",
                SchemaValueType.INTEGER, Optional.of("1"), "Exact lifetime-Prestige counter increment.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "prestige_requirement_tree", "prestige.requirement-tree", SchemaValueType.STRING,
                Optional.empty(), "Active Phase 4 requirement tree; reachable providers are health-checked/pinned.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "prestige_costs", "prestige.costs", SchemaValueType.LIST, Optional.empty(),
                "Ordered active Phase 3 cost IDs.", AllowedValues.unrestricted(), RiskLevel.CRITICAL);
        register(registry, "prestige_rewards", "prestige.rewards", SchemaValueType.LIST, Optional.empty(),
                "Ordered active Phase 3 reward IDs.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "prestige_scaling_profile", "prestige.scaling-profile", SchemaValueType.STRING,
                Optional.empty(), "Reserved and rejected as unsupported in Phase 4.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "prestige_catch_up_profile", "prestige.catch-up-profile", SchemaValueType.STRING,
                Optional.empty(), "Reserved and rejected as unsupported in Phase 4.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "prestige_cooldown", "prestige.cooldown", SchemaValueType.DURATION, Optional.of("PT0S"),
                "Minimum duration between successful Prestige operations.", AllowedValues.unrestricted(),
                RiskLevel.MEDIUM);
        register(registry, "prestige_reset_policy", "prestige.reset-policy", SchemaValueType.MAP, Optional.empty(),
                "RESET/PRESERVE overrides; omitted components inherit canonical safe defaults.",
                AllowedValues.fixed("RESET", "PRESERVE"), RiskLevel.CRITICAL);
        resetDisposition(registry, "prestige_reset_progression_stage", "progression-stage", "PRESERVE",
                "Preserves compatibility-only stage state.");
        resetDisposition(registry, "prestige_reset_active_requirement_progress", "active-requirement-progress",
                "RESET", "Resets active requirement progress.");
        resetDisposition(registry, "prestige_reset_latched_completions", "latched-completions", "RESET",
                "Resets latched requirement completions.");
        resetDisposition(registry, "prestige_reset_baselines", "baselines", "RESET",
                "Resets saved requirement baselines.");
        resetDisposition(registry, "prestige_reset_scoped_currency", "prestige-scoped-currency", "RESET",
                "Resets Prestige-scoped internal currency.");
        resetDisposition(registry, "prestige_reset_purchased_perks", "purchased-perks", "PRESERVE",
                "Preserves purchased perks.");
        resetDisposition(registry, "prestige_reset_milestone_history", "milestone-history", "PRESERVE",
                "Preserves milestone history.");
        resetDisposition(registry, "prestige_reset_season_progress", "season-progress", "PRESERVE",
                "Preserves season progress.");
        resetDisposition(registry, "prestige_reset_historical_statistics", "historical-statistics", "PRESERVE",
                "Preserves historical statistics.");
        register(registry, "prestige_external_resets", "prestige.external-resets.enabled",
                SchemaValueType.BOOLEAN, Optional.of("false"),
                "Dangerous provider reset authority; unsupported and disabled in Phase 4.",
                AllowedValues.fixed("true", "false"), RiskLevel.CRITICAL);
        register(registry, "internal_currencies", "currencies", SchemaValueType.MAP, Optional.empty(),
                "Stable exact-decimal internal currency definitions.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "entitlement_definitions", "entitlements", SchemaValueType.MAP, Optional.empty(),
                "Typed deterministic entitlement merge definitions.",
                AllowedValues.fixed("MAX", "SUM", "MIN", "OVERRIDE", "BOOLEAN_OR"), RiskLevel.HIGH);
        register(registry, "milestone_definitions", "milestones", SchemaValueType.MAP, Optional.empty(),
                "Stable repeatability-bound milestone triggers and rewards.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "season_definitions", "seasons", SchemaValueType.MAP, Optional.empty(),
                "Manual one-active-season lifecycle containers; starts/ends timestamps are metadata only.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "season_starts_at", "seasons.*.starts-at", SchemaValueType.STRING, Optional.empty(),
                "ISO-8601 metadata; Phase 4 does not automatically schedule season start.",
                AllowedValues.unrestricted(), RiskLevel.LOW);
        register(registry, "season_ends_at", "seasons.*.ends-at", SchemaValueType.STRING, Optional.empty(),
                "ISO-8601 metadata; Phase 4 does not automatically schedule season end.",
                AllowedValues.unrestricted(), RiskLevel.LOW);
        register(registry, "season_reset_policy", "seasons.*.reset-policy", SchemaValueType.MAP, Optional.empty(),
                "Season-specific policy containing only the required season-progress disposition.",
                AllowedValues.fixed("RESET", "PRESERVE"), RiskLevel.HIGH);
        register(registry, "season_progress_policy", "seasons.*.reset-policy.season-progress",
                SchemaValueType.STRING, Optional.empty(),
                "RESET starts player season progress at zero; PRESERVE carries the latest archived value.",
                AllowedValues.fixed("RESET", "PRESERVE"), RiskLevel.HIGH);
        register(registry, "season_requirement_overrides", "seasons.*.requirement-overrides",
                SchemaValueType.MAP, Optional.empty(), "Reserved and rejected as unsupported in Phase 4.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "season_catch_up_profiles", "seasons.*.catch-up-profiles", SchemaValueType.MAP,
                Optional.empty(), "Reserved and rejected as unsupported in Phase 4.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "competition_enabled", "competition.enabled", SchemaValueType.BOOLEAN,
                Optional.of("false"), "Unsupported Phase 4 competition engine boundary.",
                AllowedValues.fixed("false"), RiskLevel.CRITICAL);
        return registry;
    }

    private static void resetDisposition(
            SchemaRegistry registry,
            String id,
            String key,
            String defaultValue,
            String description) {
        register(registry, id, "prestige.reset-policy." + key, SchemaValueType.ENUM, Optional.of(defaultValue),
                description, AllowedValues.fixed("RESET", "PRESERVE"), RiskLevel.CRITICAL);
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
