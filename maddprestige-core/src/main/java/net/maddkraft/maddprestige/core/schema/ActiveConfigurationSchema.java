package net.maddkraft.maddprestige.core.schema;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.FieldId;

public final class ActiveConfigurationSchema {
    private static final String INTEGER_COUNT = "INTEGER_COUNT";
    private static final String EXACT_DECIMAL = "EXACT_DECIMAL";
    private static final String DURATION = "DURATION";
    private static final String CURRENCY_AMOUNT = "CURRENCY_AMOUNT";
    private static final String BOOLEAN_TRUE = "true";
    private static final String BOOLEAN_FALSE = "false";
    private static final String ROUNDING_EXACT = "EXACT";
    private static final String ROUNDING_FLOOR = "FLOOR";
    private static final String ROUNDING_CEILING = "CEILING";
    private static final String ROUNDING_HALF_UP = "HALF_UP";
    private static final String EXPLICIT_BASE = "EXPLICIT_BASE";

    private ActiveConfigurationSchema() {
    }

    public static SchemaRegistry create() {
        SchemaRegistry registry = PrestigeLifecycleSchema.create();
        removeLegacyStageSchema(registry);
        extend(registry);
        return registry;
    }

    public static void extend(SchemaRegistry registry) {
        register(registry, "requirement_definition", "requirements.requirements.*", SchemaValueType.MAP,
                Optional.empty(), "One typed canonical requirement definition.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "requirement_provider", "requirements.requirements.*.provider",
                SchemaValueType.STRING, Optional.empty(), "Provider owning the canonical metric.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_metric", "requirements.requirements.*.metric",
                SchemaValueType.STRING, Optional.empty(), "Canonical provider metric ID.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_value_type", "requirements.requirements.*.value-type",
                SchemaValueType.ENUM, Optional.empty(), "Exact target value type.",
                AllowedValues.fixed(INTEGER_COUNT, EXACT_DECIMAL, DURATION, CURRENCY_AMOUNT),
                RiskLevel.HIGH);
        register(registry, "requirement_operator", "requirements.requirements.*.operator",
                SchemaValueType.ENUM, Optional.of("GREATER_OR_EQUAL"), "Typed target comparison operator.",
                AllowedValues.fixed("EQUAL", "GREATER_OR_EQUAL", "LESS_OR_EQUAL"), RiskLevel.HIGH);
        register(registry, "requirement_target", "requirements.requirements.*.target",
                SchemaValueType.STRING, Optional.empty(), "Base target before numeric Prestige scaling.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_scope", "requirements.requirements.*.scope",
                SchemaValueType.ENUM, Optional.of("ABSOLUTE"),
                "Controls which observation window counts: current/lifetime uses the provider value, while "
                        + "since-prestige-start and since-season-start count only change after "
                        + "the saved boundary. A provider must advertise support for the selected scope.",
                AllowedValues.fixed("ABSOLUTE", "LIFETIME", "SINCE_PRESTIGE_START",
                        "SINCE_SEASON_START"), RiskLevel.HIGH);
        register(registry, "requirement_completion", "requirements.requirements.*.completion",
                SchemaValueType.ENUM, Optional.of("LIVE"),
                "Live requirements follow the current value; latched requirements stay complete only inside the "
                        + "same saved scope after the canonical lifecycle records completion.",
                AllowedValues.fixed("LIVE", "LATCHED"), RiskLevel.HIGH);
        register(registry, "requirement_filters", "requirements.requirements.*.filters", SchemaValueType.MAP,
                Optional.empty(), "Provider-owned metric filter selections.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "requirement_filter", "requirements.requirements.*.filters.*", SchemaValueType.STRING,
                Optional.empty(), "One provider-owned metric filter value.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "requirement_display", "requirements.requirements.*.display", SchemaValueType.MAP,
                Optional.empty(), "Presentation metadata independent from metric semantics.",
                AllowedValues.unrestricted(), RiskLevel.MEDIUM);
        register(registry, "requirement_display_value", "requirements.requirements.*.display.*",
                SchemaValueType.STRING, Optional.empty(), "One presentation metadata value.",
                AllowedValues.unrestricted(), RiskLevel.MEDIUM);
        register(registry, "requirement_hidden", "requirements.requirements.*.hidden", SchemaValueType.BOOLEAN,
                Optional.of(BOOLEAN_FALSE), "Hides a requirement from normal presentation without disabling it.",
                AllowedValues.fixed(BOOLEAN_TRUE, BOOLEAN_FALSE), RiskLevel.MEDIUM);
        catchUpFamily(registry);
        costAndRewardFamilies(registry);
        lifecycleObjectFamilies(registry);
        register(registry, "requirement_tree", "requirements.trees.*", SchemaValueType.MAP,
                Optional.empty(), "One sparse canonical root requirement group.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "requirement_tree_mode", "requirements.trees.*.mode", SchemaValueType.ENUM,
                Optional.of("ALL"), "Recursive requirement group mode.",
                AllowedValues.fixed("ALL", "ANY", "X_OF_N", "WEIGHTED"), RiskLevel.HIGH);
        register(registry, "requirement_tree_threshold", "requirements.trees.*.threshold", SchemaValueType.DECIMAL,
                Optional.empty(), "X_OF_N count or WEIGHTED threshold.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "requirement_tree_children", "requirements.trees.*.children", SchemaValueType.LIST,
                Optional.empty(), "Ordered recursive requirement children.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "requirement_tree_child", "requirements.trees.*.children.*", SchemaValueType.MAP,
                Optional.empty(), "One requirement reference or nested requirement group.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_tree_child_id", "requirements.trees.*.children.*.id",
                SchemaValueType.STRING, Optional.empty(), "Stable nested requirement-group ID.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_tree_child_reference", "requirements.trees.*.children.*.requirement",
                SchemaValueType.STRING, Optional.empty(), "Reference to a canonical requirement definition.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_tree_child_weight", "requirements.trees.*.children.*.weight",
                SchemaValueType.DECIMAL, Optional.of("1"), "Optional WEIGHTED child contribution.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_tree_child_mode", "requirements.trees.*.children.*.mode",
                SchemaValueType.ENUM, Optional.of("ALL"), "Nested recursive requirement-group mode.",
                AllowedValues.fixed("ALL", "ANY", "X_OF_N", "WEIGHTED"), RiskLevel.HIGH);
        register(registry, "requirement_tree_child_threshold", "requirements.trees.*.children.*.threshold",
                SchemaValueType.DECIMAL, Optional.empty(), "Nested X_OF_N count or WEIGHTED threshold.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_tree_nested_children", "requirements.trees.*.children.*.children",
                SchemaValueType.LIST, Optional.empty(), "Ordered children of a nested requirement group.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
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

    private static void catchUpFamily(SchemaRegistry registry) {
        String path = "requirements.requirements.*.catch-up";
        register(registry, "requirement_catch_up", path, SchemaValueType.MAP, Optional.empty(),
                "Bounded optional catch-up policy.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "requirement_catch_up_enabled", path + ".enabled", SchemaValueType.BOOLEAN,
                Optional.of(BOOLEAN_FALSE), "Enables this explicit catch-up policy.",
                AllowedValues.fixed(BOOLEAN_TRUE, BOOLEAN_FALSE), RiskLevel.HIGH);
        for (String key : List.of("start-threshold", "reduction-rate", "maximum-reduction", "floor", "quantum")) {
            register(registry, "requirement_catch_up_" + key.replace('-', '_'), path + "." + key,
                    SchemaValueType.DECIMAL, Optional.empty(), "Exact bounded catch-up value.",
                    AllowedValues.unrestricted(), RiskLevel.HIGH);
        }
        register(registry, "requirement_catch_up_rounding", path + ".rounding", SchemaValueType.ENUM,
                Optional.of(ROUNDING_EXACT), "Catch-up result rounding policy.",
                AllowedValues.fixed(ROUNDING_EXACT, ROUNDING_FLOOR, ROUNDING_CEILING, ROUNDING_HALF_UP), RiskLevel.HIGH);
    }

    private static void costAndRewardFamilies(SchemaRegistry registry) {
        register(registry, "cost_definition", "requirements.costs.*", SchemaValueType.MAP, Optional.empty(),
                "One independent provider-owned Prestige cost.", AllowedValues.unrestricted(), RiskLevel.CRITICAL);
        valueDefinition(registry, "cost", "requirements.costs.*", "amount", RiskLevel.CRITICAL);
        register(registry, "reward_definition", "rewards.rewards.*", SchemaValueType.MAP, Optional.empty(),
                "One independent additive Prestige reward.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        valueDefinition(registry, "reward", "rewards.rewards.*", "value", RiskLevel.HIGH);
        register(registry, "reward_failure_policy", "rewards.rewards.*.failure-policy", SchemaValueType.ENUM,
                Optional.of("REQUIRED"), "Failure behavior for this reward.",
                AllowedValues.fixed("REQUIRED", "OPTIONAL"), RiskLevel.HIGH);
        register(registry, "reward_repeatability", "rewards.rewards.*.repeatability", SchemaValueType.ENUM,
                Optional.of("ONCE_PER_OPERATION"), "Canonical reward repeatability boundary.",
                AllowedValues.fixed("ONCE_PER_OPERATION", "REPEATABLE"), RiskLevel.HIGH);
    }

    private static void valueDefinition(
            SchemaRegistry registry,
            String id,
            String path,
            String valueKey,
            RiskLevel risk) {
        register(registry, id + "_provider", path + ".provider", SchemaValueType.STRING, Optional.empty(),
                "Provider selected for this " + id + ".", AllowedValues.unrestricted(), risk);
        register(registry, id + "_type", path + ".type", SchemaValueType.STRING, Optional.of("value"),
                "Provider-owned action type.", AllowedValues.unrestricted(), risk);
        register(registry, id + "_value_type", path + ".value-type", SchemaValueType.ENUM,
                Optional.of(EXACT_DECIMAL), "Exact provider value type.",
                AllowedValues.fixed(INTEGER_COUNT, EXACT_DECIMAL, DURATION, CURRENCY_AMOUNT), risk);
        register(registry, id + "_" + valueKey, path + "." + valueKey, SchemaValueType.STRING, Optional.empty(),
                "Canonical typed " + id + " " + valueKey + ".", AllowedValues.unrestricted(), risk);
        register(registry, id + "_display_name", path + ".display-name", SchemaValueType.STRING, Optional.empty(),
                "Operator-facing display name.", AllowedValues.unrestricted(), risk);
        register(registry, id + "_metadata", path + ".metadata", SchemaValueType.MAP, Optional.empty(),
                "Provider-owned scalar metadata.", AllowedValues.unrestricted(), risk);
        register(registry, id + "_metadata_value", path + ".metadata.*", SchemaValueType.STRING, Optional.empty(),
                "One provider-owned metadata value.", AllowedValues.unrestricted(), risk);
    }

    private static void lifecycleObjectFamilies(SchemaRegistry registry) {
        register(registry, "currency_definition", "currencies.*", SchemaValueType.MAP, Optional.empty(),
                "One exact-decimal internal currency definition.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "currency_display_name", "currencies.*.display-name", SchemaValueType.STRING,
                Optional.empty(), "Internal currency display name.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "currency_symbol", "currencies.*.symbol", SchemaValueType.STRING, Optional.empty(),
                "Optional internal currency symbol.", AllowedValues.unrestricted(), RiskLevel.MEDIUM);
        register(registry, "currency_scale", "currencies.*.scale", SchemaValueType.INTEGER, Optional.of("0"),
                "Exact internal currency decimal scale.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "currency_rounding", "currencies.*.rounding-mode", SchemaValueType.ENUM,
                Optional.of("UNNECESSARY"), "Exact internal currency rounding policy.",
                AllowedValues.fixed("UP", "DOWN", ROUNDING_CEILING, ROUNDING_FLOOR, ROUNDING_HALF_UP,
                        "HALF_DOWN", "HALF_EVEN",
                        "UNNECESSARY"), RiskLevel.HIGH);
        register(registry, "currency_maximum_precision", "currencies.*.maximum-precision",
                SchemaValueType.INTEGER, Optional.of("38"), "Maximum exact currency precision.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "currency_maximum_balance", "currencies.*.maximum-balance", SchemaValueType.DECIMAL,
                Optional.empty(), "Maximum exact internal balance.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "currency_prestige_scoped", "currencies.*.prestige-scoped", SchemaValueType.BOOLEAN,
                Optional.of(BOOLEAN_FALSE), "Whether Prestige reset policy may reset this currency.",
                AllowedValues.fixed(BOOLEAN_TRUE, BOOLEAN_FALSE), RiskLevel.CRITICAL);

        register(registry, "milestone_definition", "milestones.*", SchemaValueType.MAP, Optional.empty(),
                "One stable Prestige milestone definition.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "milestone_display_name", "milestones.*.display-name", SchemaValueType.STRING,
                Optional.empty(), "Milestone display name.", AllowedValues.unrestricted(), RiskLevel.MEDIUM);
        register(registry, "milestone_enabled", "milestones.*.enabled", SchemaValueType.BOOLEAN,
                Optional.of(BOOLEAN_TRUE), "Enables this milestone.", AllowedValues.fixed(BOOLEAN_TRUE, BOOLEAN_FALSE),
                RiskLevel.HIGH);
        register(registry, "milestone_trigger", "milestones.*.trigger", SchemaValueType.ENUM,
                Optional.of("CURRENT_PRESTIGE"), "Canonical numeric Prestige milestone trigger.",
                AllowedValues.fixed("CURRENT_PRESTIGE", "LIFETIME_PRESTIGE", "SEASON_PROGRESS",
                        "PROVIDER_METRIC"), RiskLevel.HIGH);
        register(registry, "milestone_value_type", "milestones.*.value-type", SchemaValueType.ENUM,
                Optional.of(INTEGER_COUNT), "Exact milestone threshold type.",
                AllowedValues.fixed(INTEGER_COUNT, EXACT_DECIMAL, DURATION, CURRENCY_AMOUNT),
                RiskLevel.HIGH);
        register(registry, "milestone_threshold", "milestones.*.threshold", SchemaValueType.STRING,
                Optional.empty(), "Canonical typed milestone threshold.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, "milestone_provider", "milestones.*.provider", SchemaValueType.STRING, Optional.empty(),
                "Provider selected for a provider-metric milestone.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "milestone_provider_metric", "milestones.*.provider-metric", SchemaValueType.STRING,
                Optional.empty(), "Provider-owned milestone metric.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "milestone_repeatability", "milestones.*.repeatability", SchemaValueType.ENUM,
                Optional.of("ONCE"), "Canonical milestone repeatability boundary.",
                AllowedValues.fixed("ONCE", "ONCE_PER_PRESTIGE", "ONCE_PER_SEASON"), RiskLevel.HIGH);
        register(registry, "milestone_rewards", "milestones.*.rewards", SchemaValueType.LIST, Optional.empty(),
                "Ordered additive reward references.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, "milestone_reward", "milestones.*.rewards.*", SchemaValueType.STRING, Optional.empty(),
                "One canonical reward reference.", AllowedValues.unrestricted(), RiskLevel.HIGH);
    }

    private static void removeLegacyStageSchema(SchemaRegistry registry) {
        List.of("integrations.rank.reconciliation-policy", "competitions.enabled",
                "integrations.quickshop.progression-income-weight", "database.credentials.password",
                "progression.active", "progression.baseline", "progression.order",
                "progression.stages.*.enabled", "progression.stages.*.display-name",
                "progression.stages.*.projection", "progression.stages.*.requirements",
                "progression.stages.*.costs", "progression.stages.*.rewards",
                "prestige.required-stages", "prestige.reset-stage", "prestige.current-count-increment",
                "prestige.lifetime-count-increment", "prestige.scaling-profile", "prestige.catch-up-profile",
                "prestige.reset-policy.progression-stage")
                .forEach(registry::unregister);
    }

    private static void scalingFamily(SchemaRegistry registry, String id, String path) {
        register(registry, id + "_compact", path, SchemaValueType.MAP, Optional.empty(),
                "One compact scaling range, or an advanced defaults plus segments profile.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        scalingValues(registry, id + "_compact", path, "compact range");
        register(registry, id + "_defaults", path + ".defaults", SchemaValueType.MAP, Optional.empty(),
                "Optional values inherited by every advanced segment unless overridden.",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        scalingValues(registry, id + "_defaults", path + ".defaults", "segment default");
        register(registry, id + "_segments", path + ".segments", SchemaValueType.LIST, Optional.empty(),
                "Contiguous scaling segments beginning at Prestige 1.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, id + "_segment", path + ".segments.*", SchemaValueType.MAP, Optional.empty(),
                "One canonical contiguous scaling segment.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_start", path + ".segments.*.start-prestige", SchemaValueType.INTEGER,
                Optional.empty(), "Inclusive positive segment start.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_end", path + ".segments.*.end-prestige", SchemaValueType.STRING,
                Optional.empty(), "Inclusive finite end or unlimited.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_mode", path + ".segments.*.mode", SchemaValueType.ENUM, Optional.of("FLAT"),
                "Bounded segment formula.", AllowedValues.fixed("FLAT", "LINEAR", "EXPONENTIAL", "MANUAL"),
                RiskLevel.HIGH);
        register(registry, id + "_transition", path + ".segments.*.transition", SchemaValueType.ENUM,
                Optional.of(EXPLICIT_BASE), "Explicit base or continuity from the preceding segment.",
                AllowedValues.fixed(EXPLICIT_BASE, "CONTINUE"), RiskLevel.HIGH);
        register(registry, id + "_base", path + ".segments.*.base", SchemaValueType.DECIMAL, Optional.of("1"),
                "Non-negative segment base.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_rate", path + ".segments.*.rate", SchemaValueType.DECIMAL, Optional.of("0"),
                "Non-negative linear delta or exponential factor.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_rounding", path + ".segments.*.rounding", SchemaValueType.ENUM,
                Optional.of(ROUNDING_EXACT), "Post-formula target rounding policy.",
                AllowedValues.fixed(ROUNDING_EXACT, ROUNDING_FLOOR, ROUNDING_CEILING, ROUNDING_HALF_UP), RiskLevel.HIGH);
        register(registry, id + "_quantum", path + ".segments.*.quantum", SchemaValueType.DECIMAL,
                Optional.of("1"), "Positive bounded rounding quantum.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
        register(registry, id + "_floor", path + ".segments.*.floor", SchemaValueType.DECIMAL, Optional.empty(),
                "Optional non-negative lower bound.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_cap", path + ".segments.*.cap", SchemaValueType.DECIMAL, Optional.empty(),
                "Optional non-negative upper bound.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_overrides", path + ".segments.*.overrides", SchemaValueType.MAP, Optional.empty(),
                "Explicit bounded per-Prestige values.", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_override", path + ".segments.*.overrides.*", SchemaValueType.DECIMAL,
                Optional.empty(), "One explicit bounded per-Prestige value.", AllowedValues.unrestricted(),
                RiskLevel.HIGH);
    }

    private static void scalingValues(SchemaRegistry registry, String id, String path, String label) {
        register(registry, id + "_mode", path + ".mode", SchemaValueType.ENUM, Optional.of("FLAT"),
                "Formula mode for this " + label + ".",
                AllowedValues.fixed("FLAT", "LINEAR", "EXPONENTIAL", "MANUAL"), RiskLevel.HIGH);
        register(registry, id + "_transition", path + ".transition", SchemaValueType.ENUM,
                Optional.of(EXPLICIT_BASE), "Base or continuity rule for this " + label + ".",
                AllowedValues.fixed(EXPLICIT_BASE, "CONTINUE"), RiskLevel.HIGH);
        register(registry, id + "_base", path + ".base", SchemaValueType.DECIMAL, Optional.of("1"),
                "Non-negative base for this " + label + ".", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_rate", path + ".rate", SchemaValueType.DECIMAL, Optional.of("0"),
                "Non-negative linear delta or exponential factor for this " + label + ".",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_rounding", path + ".rounding", SchemaValueType.ENUM, Optional.of(ROUNDING_EXACT),
                "Post-formula rounding for this " + label + ".",
                AllowedValues.fixed(ROUNDING_EXACT, ROUNDING_FLOOR, ROUNDING_CEILING, ROUNDING_HALF_UP), RiskLevel.HIGH);
        register(registry, id + "_quantum", path + ".quantum", SchemaValueType.DECIMAL, Optional.of("1"),
                "Positive rounding quantum for this " + label + ".", AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_floor", path + ".floor", SchemaValueType.DECIMAL, Optional.empty(),
                "Optional non-negative lower bound for this " + label + ".",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
        register(registry, id + "_cap", path + ".cap", SchemaValueType.DECIMAL, Optional.empty(),
                "Optional non-negative upper bound for this " + label + ".",
                AllowedValues.unrestricted(), RiskLevel.HIGH);
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
