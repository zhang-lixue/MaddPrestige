package net.maddkraft.maddprestige.core.config.phase4;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.EntitlementId;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationCompiler;
import net.maddkraft.maddprestige.core.currency.CurrencyDefinition;
import net.maddkraft.maddprestige.core.entitlement.EntitlementDefinition;
import net.maddkraft.maddprestige.core.entitlement.EntitlementMergeStrategy;
import net.maddkraft.maddprestige.core.milestone.MilestoneDefinition;
import net.maddkraft.maddprestige.core.milestone.MilestoneRepeatability;
import net.maddkraft.maddprestige.core.milestone.MilestoneTriggerType;
import net.maddkraft.maddprestige.core.season.SeasonDefinition;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Compiles the canonical {@code lifecycle.yml} document without mutating the lossless source document. */
public final class PhaseFourConfigurationCompiler {
    private static final String DOCUMENT = "lifecycle.yml";
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setLabel("Phase 4 canonical lifecycle document")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(50)
            .setCodePointLimit(2 * 1024 * 1024)
            .build();

    public PhaseFourConfigurationCompilation compile(CompiledConfiguration compiled) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        if (!compiled.documents().containsKey(DOCUMENT)) {
            return new PhaseFourConfigurationCompilation(PhaseFourConfiguration.empty(),
                    ValidationReport.of(findings));
        }
        Map<?, ?> root = load(compiled.documents().get(DOCUMENT), findings);
        int schemaVersion = integer(root.get("schema-version"), 4, "lifecycle.schema-version", findings);
        if (schemaVersion != 4) {
            findings.add(error("phase4.schema.version", "lifecycle.schema-version",
                    "Phase 4 lifecycle schema version must be 4.", "Use schema-version: 4."));
        }
        PrestigeConfiguration prestige = prestige(root.get("prestige"), findings);
        PrestigeValueScalingConfiguration valueScaling = valueScaling(root.get("prestige"), findings);
        Map<CurrencyId, CurrencyDefinition> currencies = currencies(root.get("currencies"), findings);
        Map<EntitlementId, EntitlementDefinition> entitlements = entitlements(root.get("entitlements"), findings);
        Map<MilestoneId, MilestoneDefinition> milestones = milestones(root.get("milestones"), findings);
        Map<SeasonId, SeasonDefinition> seasons = seasons(root.get("seasons"), findings);
        CompetitionConfiguration competition = competition(root.get("competition"), findings);
        PhaseFourConfiguration configuration;
        try {
            configuration = new PhaseFourConfiguration(4, prestige, currencies, entitlements, milestones, seasons,
                    valueScaling, competition);
        } catch (IllegalArgumentException exception) {
            findings.add(error("phase4.configuration.invalid", "lifecycle", exception.getMessage(),
                    "Correct the lifecycle configuration before apply."));
            configuration = PhaseFourConfiguration.empty();
        }
        return new PhaseFourConfigurationCompilation(configuration, ValidationReport.of(findings));
    }

    private static PrestigeValueScalingConfiguration valueScaling(
            Object value,
            List<ValidationFinding> findings) {
        Map<?, ?> prestige = mapping(value, "prestige", findings);
        LinkedHashMap<CostId, net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile> costs =
                scalingMap(prestige.get("cost-scaling"), CostId::new, "prestige.cost-scaling", findings);
        LinkedHashMap<RewardId, net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile> rewards =
                scalingMap(prestige.get("reward-scaling"), RewardId::new, "prestige.reward-scaling", findings);
        return new PrestigeValueScalingConfiguration(costs, rewards);
    }

    private static <T> LinkedHashMap<T, net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile> scalingMap(
            Object value,
            Function<String, T> idFactory,
            String path,
            List<ValidationFinding> findings) {
        LinkedHashMap<T, net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile> result =
                new LinkedHashMap<>();
        for (var entry : mapping(value, path, findings).entrySet()) {
            T id = id(entry.getKey(), idFactory, path, findings, null);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                findings.add(error("phase4.scaling.invalid", path,
                        "Each scaling profile must be a mapping.",
                        "Configure one compact mode or an advanced segments list."));
                continue;
            }
            try {
                result.put(id, PhaseThreeConfigurationCompiler.segmentedScaling(fields));
            } catch (IllegalArgumentException exception) {
                findings.add(error(PhaseThreeConfigurationCompiler.scalingCode("phase4.scaling", exception),
                        path + "." + id,
                        exception.getMessage(), "Use one compact mode or bounded contiguous ranges."));
            }
        }
        return result;
    }

    private static PrestigeConfiguration prestige(Object value, List<ValidationFinding> findings) {
        Map<?, ?> fields = mapping(value, "prestige", findings);
        boolean enabled = bool(fields.get("enabled"), false, "prestige.enabled", findings);
        Set<StageId> requiredStages = idSet(fields.get("required-stages"), StageId::new,
                "prestige.required-stages", findings);
        StageId resetStage = fields.containsKey("reset-stage")
                ? id(fields.get("reset-stage"), StageId::new, "prestige.reset-stage", findings,
                        new StageId("disabled"))
                : new StageId("disabled");
        long currentIncrement = longValue(fields.get("current-count-increment"), 1,
                "prestige.current-count-increment", findings);
        long lifetimeIncrement = longValue(fields.get("lifetime-count-increment"), 1,
                "prestige.lifetime-count-increment", findings);
        PrestigeLimit limit = prestigeLimit(fields.get("maximum"), findings);
        Duration cooldown = duration(fields.get("cooldown"), Duration.ZERO, "prestige.cooldown", findings);
        Optional<RequirementId> requirement = optionalId(fields.get("requirement-tree"), RequirementId::new,
                "prestige.requirement-tree", findings);
        List<CostId> costs = idList(fields.get("costs"), CostId::new, "prestige.costs", findings);
        List<RewardId> rewards = idList(fields.get("rewards"), RewardId::new, "prestige.rewards", findings);
        Optional<String> scaling = optionalString(fields.get("scaling-profile"), "prestige.scaling-profile",
                findings);
        Optional<String> catchUp = optionalString(fields.get("catch-up-profile"), "prestige.catch-up-profile",
                findings);
        ResetPreservePolicy resetPolicy = resetPolicy(fields.get("reset-policy"), "prestige.reset-policy",
                findings);
        Map<?, ?> external = mapping(fields.get("external-resets"), "prestige.external-resets", findings);
        boolean externalEnabled = bool(external.get("enabled"), false, "prestige.external-resets.enabled",
                findings);
        try {
            return new PrestigeConfiguration(enabled, requiredStages, resetStage, currentIncrement,
                    lifetimeIncrement, limit, cooldown, requirement, costs, rewards, scaling, catchUp, resetPolicy,
                    externalEnabled);
        } catch (IllegalArgumentException exception) {
            findings.add(error("phase4.prestige.invalid", "prestige", exception.getMessage(),
                    "Use positive increments, valid limits/cooldowns, and a complete safe reset policy."));
            return PrestigeConfiguration.disabled();
        }
    }

    private static Map<CurrencyId, CurrencyDefinition> currencies(
            Object value,
            List<ValidationFinding> findings) {
        LinkedHashMap<CurrencyId, CurrencyDefinition> result = new LinkedHashMap<>();
        for (var entry : mapping(value, "currencies", findings).entrySet()) {
            CurrencyId id = id(entry.getKey(), CurrencyId::new, "currencies", findings, null);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                findings.add(error("phase4.currency.type", "currencies", "Currency definition must be a mapping.",
                        "Configure fields beneath a stable currency ID."));
                continue;
            }
            String path = "currencies." + id.value();
            try {
                result.put(id, new CurrencyDefinition(id,
                        string(fields.get("display-name"), id.value(), path + ".display-name", findings),
                        optionalString(fields.get("symbol"), path + ".symbol", findings),
                        integer(fields.get("scale"), 0, path + ".scale", findings),
                        enumValue(fields.get("rounding-mode"), RoundingMode.class, RoundingMode.UNNECESSARY,
                                path + ".rounding-mode", findings),
                        integer(fields.get("maximum-precision"), 38, path + ".maximum-precision", findings),
                        decimal(fields.get("maximum-balance"), "999999999999999999", path + ".maximum-balance",
                                findings),
                        bool(fields.get("prestige-scoped"), false, path + ".prestige-scoped", findings)));
            } catch (IllegalArgumentException exception) {
                findings.add(error("phase4.currency.invalid", path, exception.getMessage(),
                        "Use a nonblank display name and bounded exact decimal policy."));
            }
        }
        return Map.copyOf(result);
    }

    private static Map<EntitlementId, EntitlementDefinition> entitlements(
            Object value,
            List<ValidationFinding> findings) {
        LinkedHashMap<EntitlementId, EntitlementDefinition> result = new LinkedHashMap<>();
        for (var entry : mapping(value, "entitlements", findings).entrySet()) {
            EntitlementId id = id(entry.getKey(), EntitlementId::new, "entitlements", findings, null);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                findings.add(error("phase4.entitlement.type", "entitlements",
                        "Entitlement definition must be a mapping.", "Configure typed merge fields."));
                continue;
            }
            String path = "entitlements." + id.value();
            try {
                result.put(id, new EntitlementDefinition(id,
                        enumValue(fields.get("value-type"),
                                net.maddkraft.maddprestige.core.entitlement.EntitlementValueType.class,
                                net.maddkraft.maddprestige.core.entitlement.EntitlementValueType.INTEGER,
                                path + ".value-type", findings),
                        enumValue(fields.get("merge-strategy"), EntitlementMergeStrategy.class,
                                EntitlementMergeStrategy.MAX, path + ".merge-strategy", findings)));
            } catch (IllegalArgumentException exception) {
                findings.add(error("phase4.entitlement.invalid", path, exception.getMessage(),
                        "Choose a merge strategy compatible with the configured value type."));
            }
        }
        return Map.copyOf(result);
    }

    private static Map<MilestoneId, MilestoneDefinition> milestones(
            Object value,
            List<ValidationFinding> findings) {
        LinkedHashMap<MilestoneId, MilestoneDefinition> result = new LinkedHashMap<>();
        for (var entry : mapping(value, "milestones", findings).entrySet()) {
            MilestoneId id = id(entry.getKey(), MilestoneId::new, "milestones", findings, null);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                findings.add(error("phase4.milestone.type", "milestones", "Milestone must be a mapping.",
                        "Configure a stable trigger and reward list."));
                continue;
            }
            String path = "milestones." + id.value();
            MetricValue threshold = metricValue(fields.get("value-type"), fields.get("threshold"), path,
                    findings);
            if (threshold == null) {
                continue;
            }
            try {
                result.put(id, new MilestoneDefinition(id,
                        string(fields.get("display-name"), id.value(), path + ".display-name", findings),
                        bool(fields.get("enabled"), true, path + ".enabled", findings),
                        enumValue(fields.get("trigger"), MilestoneTriggerType.class,
                                MilestoneTriggerType.CURRENT_PRESTIGE, path + ".trigger", findings),
                        threshold,
                        optionalId(fields.get("provider"), ProviderId::new, path + ".provider", findings),
                        optionalString(fields.get("provider-metric"), path + ".provider-metric", findings),
                        enumValue(fields.get("repeatability"), MilestoneRepeatability.class,
                                MilestoneRepeatability.ONCE, path + ".repeatability", findings),
                        idList(fields.get("rewards"), RewardId::new, path + ".rewards", findings)));
            } catch (IllegalArgumentException exception) {
                findings.add(error("phase4.milestone.invalid", path, exception.getMessage(),
                        "Use a compatible trigger, provider binding, threshold, and unique rewards."));
            }
        }
        return Map.copyOf(result);
    }

    private static Map<SeasonId, SeasonDefinition> seasons(Object value, List<ValidationFinding> findings) {
        LinkedHashMap<SeasonId, SeasonDefinition> result = new LinkedHashMap<>();
        for (var entry : mapping(value, "seasons", findings).entrySet()) {
            SeasonId id = id(entry.getKey(), SeasonId::new, "seasons", findings, null);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                findings.add(error("phase4.season.type", "seasons", "Season must be a mapping.",
                        "Configure lifecycle fields beneath a stable season ID."));
                continue;
            }
            String path = "seasons." + id.value();
            try {
                result.put(id, new SeasonDefinition(id,
                        string(fields.get("display-name"), id.value(), path + ".display-name", findings),
                        instant(fields.get("starts-at"), path + ".starts-at", findings),
                        instant(fields.get("ends-at"), path + ".ends-at", findings),
                        seasonProgressPolicy(fields.get("reset-policy"), path + ".reset-policy", findings),
                        idMap(fields.get("requirement-overrides"), RequirementId::new,
                                path + ".requirement-overrides", findings),
                        stringValueIdMap(fields.get("catch-up-profiles"), RequirementId::new,
                                path + ".catch-up-profiles", findings)));
            } catch (IllegalArgumentException exception) {
                findings.add(error("phase4.season.invalid", path, exception.getMessage(),
                        "Use ordered timestamps, a complete policy, and valid immutable references."));
            }
        }
        return Map.copyOf(result);
    }

    private static ResetDisposition seasonProgressPolicy(
            Object value,
            String path,
            List<ValidationFinding> findings) {
        Map<?, ?> fields = mapping(value, path, findings);
        String supported = "season-progress";
        for (Object configured : fields.keySet()) {
            String component = scalar(configured);
            if (!supported.equals(component)) {
                String diagnostic = component == null ? "<non-scalar>" : component;
                findings.add(error("phase4.season.reset_policy.unsupported_component",
                        path + "." + diagnostic,
                        "Season runtime supports only the season-progress reset disposition; "
                                + diagnostic + " would be inert.",
                        "Remove the unsupported component and configure only season-progress: RESET or PRESERVE."));
            }
        }
        if (!fields.containsKey(supported)) {
            findings.add(error("phase4.season.reset_policy.missing", path + "." + supported,
                    "Season progress reset/preserve behavior must be explicit.",
                    "Set season-progress to RESET or PRESERVE."));
        }
        return enumValue(fields.get(supported), ResetDisposition.class, ResetDisposition.RESET,
                path + "." + supported, findings);
    }

    private static CompetitionConfiguration competition(Object value, List<ValidationFinding> findings) {
        Map<?, ?> fields = mapping(value, "competition", findings);
        boolean enabled = bool(fields.get("enabled"), false, "competition.enabled", findings);
        if (enabled) {
            findings.add(error("phase4.competition.unsupported", "competition.enabled",
                    "The generic competition engine is deferred and cannot activate in Phase 4.",
                    "Keep competition.enabled false."));
        }
        return new CompetitionConfiguration(enabled);
    }

    private static ResetPreservePolicy resetPolicy(Object value, String path, List<ValidationFinding> findings) {
        Map<?, ?> fields = mapping(value, path, findings);
        EnumMap<ResetComponent, ResetDisposition> dispositions = new EnumMap<>(ResetComponent.class);
        for (ResetComponent component : ResetComponent.values()) {
            String key = component.name().toLowerCase(Locale.ROOT).replace('_', '-');
            if (!fields.containsKey(key)) {
                dispositions.put(component, ResetPreservePolicy.safeDefaults().disposition(component));
            } else {
                dispositions.put(component, enumValue(fields.get(key), ResetDisposition.class,
                        ResetPreservePolicy.safeDefaults().disposition(component), path + "." + key, findings));
            }
        }
        return new ResetPreservePolicy(dispositions);
    }

    private static PrestigeLimit prestigeLimit(Object value, List<ValidationFinding> findings) {
        String text = value == null ? "unlimited" : scalar(value);
        if (text != null && "unlimited".equalsIgnoreCase(text)) {
            return PrestigeLimit.unlimited();
        }
        try {
            return new PrestigeLimit(OptionalLong.of(Long.parseLong(requiredScalar(value))));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.prestige.maximum", "prestige.maximum",
                    "Maximum must be a positive integer or unlimited.", "Use unlimited or a positive integer."));
            return PrestigeLimit.unlimited();
        }
    }

    private static MetricValue metricValue(
            Object typeValue,
            Object thresholdValue,
            String path,
            List<ValidationFinding> findings) {
        MetricValueType type = enumValue(typeValue, MetricValueType.class, MetricValueType.COUNT,
                path + ".value-type", findings);
        try {
            return MetricValue.parse(type, requiredScalar(thresholdValue));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.milestone.threshold", path + ".threshold", exception.getMessage(),
                    "Use a canonical threshold matching value-type."));
            return null;
        }
    }

    private static Map<?, ?> load(String source, List<ValidationFinding> findings) {
        try {
            Object loaded = new Load(SETTINGS).loadFromString(source);
            if (loaded == null) {
                return Map.of();
            }
            if (loaded instanceof Map<?, ?> map) {
                return map;
            }
            findings.add(error("phase4.document.type", DOCUMENT, "Lifecycle document must be a mapping.",
                    "Use documented top-level keys."));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.yaml.invalid", DOCUMENT, exception.getMessage(),
                    "Correct YAML syntax and duplicate keys."));
        }
        return Map.of();
    }

    private static Map<?, ?> mapping(Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        findings.add(error("phase4.mapping.expected", path, "Expected a YAML mapping.",
                "Use stable IDs as mapping keys."));
        return Map.of();
    }

    private static <T> List<T> idList(
            Object value,
            Function<String, T> constructor,
            String path,
            List<ValidationFinding> findings) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            findings.add(error("phase4.list.expected", path, "Expected a YAML list.", "Use a list of stable IDs."));
            return List.of();
        }
        ArrayList<T> result = new ArrayList<>();
        for (Object item : list) {
            T parsed = id(item, constructor, path, findings, null);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        if (new LinkedHashSet<>(result).size() != result.size()) {
            findings.add(error("phase4.list.duplicate", path, "Stable ID lists cannot contain duplicates.",
                    "Remove duplicate references."));
        }
        return List.copyOf(result);
    }

    private static <T> Set<T> idSet(
            Object value,
            Function<String, T> constructor,
            String path,
            List<ValidationFinding> findings) {
        return Set.copyOf(idList(value, constructor, path, findings));
    }

    private static <T> Optional<T> optionalId(
            Object value,
            Function<String, T> constructor,
            String path,
            List<ValidationFinding> findings) {
        return value == null ? Optional.empty() : Optional.ofNullable(id(value, constructor, path, findings, null));
    }

    private static <T> T id(
            Object value,
            Function<String, T> constructor,
            String path,
            List<ValidationFinding> findings,
            T fallback) {
        String text = scalar(value);
        if (text == null) {
            findings.add(error("phase4.id.missing", path, "A stable scalar ID is required.",
                    "Use lowercase a-z0-9._-."));
            return fallback;
        }
        try {
            return constructor.apply(text);
        } catch (IllegalArgumentException exception) {
            findings.add(error("phase4.id.invalid", path, exception.getMessage(), "Use lowercase a-z0-9._-."));
            return fallback;
        }
    }

    private static <K> Map<K, K> idMap(
            Object value,
            Function<String, K> constructor,
            String path,
            List<ValidationFinding> findings) {
        LinkedHashMap<K, K> result = new LinkedHashMap<>();
        for (var entry : mapping(value, path, findings).entrySet()) {
            K key = id(entry.getKey(), constructor, path, findings, null);
            K item = id(entry.getValue(), constructor, path, findings, null);
            if (key != null && item != null) {
                result.put(key, item);
            }
        }
        return Map.copyOf(result);
    }

    private static <K> Map<K, String> stringValueIdMap(
            Object value,
            Function<String, K> constructor,
            String path,
            List<ValidationFinding> findings) {
        LinkedHashMap<K, String> result = new LinkedHashMap<>();
        for (var entry : mapping(value, path, findings).entrySet()) {
            K key = id(entry.getKey(), constructor, path, findings, null);
            String item = scalar(entry.getValue());
            if (key == null || item == null || item.isBlank()) {
                findings.add(error("phase4.reference.invalid", path, "Reference map entries require scalar IDs.",
                        "Use stable requirement keys and nonblank profile IDs."));
            } else {
                result.put(key, item);
            }
        }
        return Map.copyOf(result);
    }

    private static Optional<Instant> instant(Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(requiredScalar(value)));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.instant.invalid", path, "Expected an ISO-8601 instant.",
                    "Use a value such as 2026-08-15T12:00:00Z."));
            return Optional.empty();
        }
    }

    private static Duration duration(
            Object value,
            Duration fallback,
            String path,
            List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        try {
            return Duration.parse(requiredScalar(value));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.duration.invalid", path, "Expected an ISO-8601 duration.",
                    "Use a value such as PT1H."));
            return fallback;
        }
    }

    private static ExactDecimal decimal(
            Object value,
            String fallback,
            String path,
            List<ValidationFinding> findings) {
        try {
            return ExactDecimal.parse(value == null ? fallback : requiredScalar(value));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.decimal.invalid", path, exception.getMessage(),
                    "Use canonical exact decimal text."));
            return ExactDecimal.parse(fallback);
        }
    }

    private static Optional<String> optionalString(
            Object value,
            String path,
            List<ValidationFinding> findings) {
        if (value == null) {
            return Optional.empty();
        }
        String result = string(value, "", path, findings);
        if (result.isBlank()) {
            findings.add(error("phase4.string.blank", path, "Configured value cannot be blank.",
                    "Remove the field or provide a nonblank value."));
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private static <E extends Enum<E>> E enumValue(
            Object value,
            Class<E> type,
            E fallback,
            String path,
            List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, requiredScalar(value).toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.enum.invalid", path, "Invalid " + type.getSimpleName() + " value.",
                    "Use one of " + java.util.Arrays.toString(type.getEnumConstants()) + "."));
            return fallback;
        }
    }

    private static boolean bool(Object value, boolean fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean result) {
            return result;
        }
        findings.add(error("phase4.boolean.invalid", path, "Expected true or false.", "Use a YAML boolean."));
        return fallback;
    }

    private static int integer(Object value, int fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(requiredScalar(value));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.integer.invalid", path, "Expected a bounded integer.",
                    "Use a whole number within the documented range."));
            return fallback;
        }
    }

    private static long longValue(Object value, long fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(requiredScalar(value));
        } catch (RuntimeException exception) {
            findings.add(error("phase4.long.invalid", path, "Expected a bounded whole number.",
                    "Use a whole number within signed 64-bit bounds."));
            return fallback;
        }
    }

    private static String string(Object value, String fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        String result = scalar(value);
        if (result == null) {
            findings.add(error("phase4.string.invalid", path, "Expected a scalar string.", "Use text."));
            return fallback;
        }
        return result;
    }

    private static String scalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                ? String.valueOf(value) : null;
    }

    private static String requiredScalar(Object value) {
        String result = scalar(value);
        if (result == null) {
            throw new IllegalArgumentException("Expected a scalar value");
        }
        return result;
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "Invalid Phase 4 configuration cannot activate.", remediation);
    }
}
