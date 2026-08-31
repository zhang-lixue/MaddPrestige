package net.maddkraft.maddprestige.core.config.phase3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.command.CommandActionValidator;
import net.maddkraft.maddprestige.core.command.CommandTemplate;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.requirement.CatchUpProfile;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementChild;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementGroupMode;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.requirement.RequirementTarget;
import net.maddkraft.maddprestige.core.requirement.RequirementTreeValidator;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.requirement.ScalingStrategy;
import net.maddkraft.maddprestige.core.requirement.TargetRounding;
import net.maddkraft.maddprestige.core.scaling.PrestigeScalingSegment;
import net.maddkraft.maddprestige.core.scaling.SegmentScalingMode;
import net.maddkraft.maddprestige.core.scaling.SegmentTransition;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

public final class PhaseThreeConfigurationCompiler {
    private static final String REQUIREMENTS_DOCUMENT = "requirements.yml";
    private static final String REWARDS_DOCUMENT = "rewards.yml";
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setLabel("Phase 3 canonical documents")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(50)
            .setCodePointLimit(4 * 1024 * 1024)
            .build();

    public PhaseThreeConfigurationCompilation compile(
            CompiledConfiguration compiled,
            Map<MetricBinding, MetricDescriptor> descriptors) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        Map<?, ?> requirementRoot = loadDocument(compiled, REQUIREMENTS_DOCUMENT, findings);
        Map<?, ?> rewardRoot = loadDocument(compiled, REWARDS_DOCUMENT, findings);
        if (requirementRoot.isEmpty() && rewardRoot.isEmpty()
                && !compiled.documents().containsKey(REQUIREMENTS_DOCUMENT)
                && !compiled.documents().containsKey(REWARDS_DOCUMENT)) {
            return new PhaseThreeConfigurationCompilation(PhaseThreeConfiguration.empty(),
                    ValidationReport.of(findings));
        }
        int schema = integer(requirementRoot.get("schema-version"), 3, "requirements.schema-version", findings);
        validateSchema(compiled, REQUIREMENTS_DOCUMENT, schema, "requirements.schema-version", findings);
        int rewardSchema = integer(rewardRoot.get("schema-version"), 3, "rewards.schema-version", findings);
        validateSchema(compiled, REWARDS_DOCUMENT, rewardSchema, "rewards.schema-version", findings);
        int maximumDepth = integer(requirementRoot.get("maximum-depth"), 16,
                "requirements.maximum-depth", findings);
        Map<RequirementId, RequirementDefinition> definitions = definitions(
                requirementRoot.get("requirements"), descriptors, findings);
        Map<RequirementId, RequirementNode> trees = trees(
                requirementRoot.get("trees"), definitions, maximumDepth, findings);
        Map<CostId, CostDefinition> costs = costs(requirementRoot.get("costs"), findings);
        Map<RewardId, RewardDefinition> rewards = rewards(rewardRoot.get("rewards"), findings);
        CommandActionPolicy commandPolicy = commandPolicy(rewardRoot.get("command-actions"), findings);
        var treeValidator = new RequirementTreeValidator();
        for (RequirementNode tree : trees.values()) {
            findings.addAll(treeValidator.validate(tree, descriptors, maximumDepth).findings());
        }
        findings.addAll(new CommandActionValidator().validateConfiguration(commandPolicy).findings());
        PhaseThreeConfiguration configuration = new PhaseThreeConfiguration(Math.max(1, schema),
                Math.max(1, maximumDepth), definitions, trees, costs, rewards, commandPolicy);
        return new PhaseThreeConfigurationCompilation(configuration, ValidationReport.of(findings));
    }

    private static Map<RequirementId, RequirementDefinition> definitions(
            Object value,
            Map<MetricBinding, MetricDescriptor> descriptors,
            List<ValidationFinding> findings) {
        Map<?, ?> map = mapping(value, "requirements.requirements", findings);
        LinkedHashMap<RequirementId, RequirementDefinition> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            RequirementId id = id(entry.getKey(), RequirementId::new, "requirements.requirements", findings);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                if (id != null) {
                    findings.add(error("requirement.definition.type", "requirements." + id.value(),
                            "Requirement definition must be a mapping.", "Configure typed requirement fields."));
                }
                continue;
            }
            String path = "requirements.requirements." + id.value();
            ProviderId provider = id(fields.get("provider"), ProviderId::new, path + ".provider", findings);
            MetricId metric = id(fields.get("metric"), MetricId::new, path + ".metric", findings);
            MetricOperator operator = enumValue(fields.get("operator"), MetricOperator.class,
                    MetricOperator.GREATER_OR_EQUAL, path + ".operator", findings);
            if (provider == null || metric == null) {
                continue;
            }
            MetricDescriptor descriptor = descriptors.get(new MetricBinding(provider, metric));
            MetricValueType valueType = requirementValueType(fields, descriptor, path, findings);
            if (valueType == null) {
                continue;
            }
            RequirementTarget target = target(fields.get("target"), valueType, operator, path, findings);
            if (target == null) {
                continue;
            }
            MeasurementScope scope = enumValue(fields.get("scope"), MeasurementScope.class,
                    MeasurementScope.ABSOLUTE, path + ".scope", findings);
            CompletionMode completion = enumValue(fields.get("completion"), CompletionMode.class,
                    CompletionMode.LIVE, path + ".completion", findings);
            ScalingProfile scaling = scaling(fields.get("scaling"), path + ".scaling", findings);
            CatchUpProfile catchUp = catchUp(fields.get("catch-up"), path + ".catch-up", findings);
            Map<String, String> filters = stringMap(fields.get("filters"), path + ".filters", findings);
            Map<String, String> display = stringMap(fields.get("display"), path + ".display", findings);
            boolean hidden = bool(fields.get("hidden"), false, path + ".hidden", findings);
            try {
                result.put(id, RequirementDefinition.create(id, provider, metric, operator, target, scope,
                        completion, scaling, catchUp, filters, display, hidden));
            } catch (IllegalArgumentException exception) {
                findings.add(error("requirement.definition.invalid", path, exception.getMessage(),
                        "Use bounded, control-free semantic fields."));
            }
        }
        return Map.copyOf(result);
    }

    private static MetricValueType requirementValueType(
            Map<?, ?> fields,
            MetricDescriptor descriptor,
            String path,
        List<ValidationFinding> findings) {
        if (descriptor != null) {
            Object configuredValue = fields.get("value-type");
            MetricValueType configured = fields.containsKey("value-type")
                    && isIntegerCountAlias(configuredValue)
                    && (descriptor.valueType() == MetricValueType.INTEGER
                            || descriptor.valueType() == MetricValueType.COUNT)
                    ? descriptor.valueType()
                    : fields.containsKey("value-type")
                            ? enumValue(configuredValue, MetricValueType.class, descriptor.valueType(),
                                    path + ".value-type", findings)
                            : descriptor.valueType();
            if (configured != descriptor.valueType()) {
                findings.add(error("requirement.value_type.mismatch", path + ".value-type",
                        "Configured value type does not match the provider-advertised metric type.",
                        "Use " + descriptor.valueType() + " or omit value-type while the provider is available."));
                return null;
            }
            return configured;
        }
        if (!fields.containsKey("value-type")) {
            findings.add(error("requirement.value_type.required", path + ".value-type",
                    "A dormant requirement whose provider is unavailable still needs an explicit target type.",
                    "Set value-type so the immutable definition remains typed without activating its provider."));
            return null;
        }
        return enumValue(fields.get("value-type"), MetricValueType.class, MetricValueType.EXACT_DECIMAL,
                path + ".value-type", findings);
    }

    private static boolean isIntegerCountAlias(Object value) {
        try {
            return scalarRequired(value).toUpperCase(Locale.ROOT).replace('-', '_').equals("INTEGER_COUNT");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Map<RequirementId, RequirementNode> trees(
            Object value,
            Map<RequirementId, RequirementDefinition> definitions,
            int maximumDepth,
            List<ValidationFinding> findings) {
        Map<?, ?> map = mapping(value, "requirements.trees", findings);
        LinkedHashMap<RequirementId, RequirementNode> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            RequirementId treeId = id(entry.getKey(), RequirementId::new, "requirements.trees", findings);
            if (treeId == null) {
                continue;
            }
            Object configuredRoot = rootWithInferredId(entry.getValue(), treeId);
            RequirementNode node = parseNode(configuredRoot, definitions, 1, maximumDepth,
                    "requirements.trees." + treeId.value(), findings);
            if (node != null) {
                if (!node.id().equals(treeId)) {
                    findings.add(error("requirement.tree.id", "requirements.trees." + treeId.value(),
                            "Tree root ID must match its stable map key.", "Use id: " + treeId.value() + "."));
                }
                result.put(treeId, node);
            }
        }
        return Map.copyOf(result);
    }

    private static Object rootWithInferredId(Object value, RequirementId treeId) {
        if (!(value instanceof Map<?, ?> fields) || fields.containsKey("requirement") || fields.containsKey("id")) {
            return value;
        }
        LinkedHashMap<Object, Object> inferred = new LinkedHashMap<>(fields);
        inferred.put("id", treeId.value());
        return inferred;
    }

    private static RequirementNode parseNode(
            Object value,
            Map<RequirementId, RequirementDefinition> definitions,
            int depth,
            int maximumDepth,
            String path,
            List<ValidationFinding> findings) {
        if (depth > Math.max(1, maximumDepth)) {
            findings.add(error("requirement.depth.exceeded", path, "Tree exceeds configured maximum depth.",
                    "Reduce recursive nesting."));
            return null;
        }
        if (!(value instanceof Map<?, ?> fields)) {
            findings.add(error("requirement.node.type", path, "Requirement node must be a mapping.",
                    "Use requirement: id or a group mapping."));
            return null;
        }
        if (fields.containsKey("requirement")) {
            RequirementId reference = id(fields.get("requirement"), RequirementId::new, path, findings);
            RequirementDefinition definition = reference == null ? null : definitions.get(reference);
            if (definition == null) {
                findings.add(error("requirement.reference.unknown", path, "Unknown requirement reference.",
                        "Reference a defined immutable requirement ID."));
                return null;
            }
            return new RequirementLeaf(definition);
        }
        RequirementId id = id(fields.get("id"), RequirementId::new, path + ".id", findings);
        if (id == null) {
            return null;
        }
        RequirementGroupMode mode = enumValue(fields.get("mode"), RequirementGroupMode.class,
                RequirementGroupMode.ALL, path + ".mode", findings);
        ExactDecimal threshold = decimal(fields.get("threshold"), "1", path + ".threshold", findings);
        CatchUpProfile catchUp = catchUp(fields.get("catch-up"), path + ".catch-up", findings);
        String display = string(fields.get("display-name"), id.value(), path + ".display-name", findings);
        ArrayList<RequirementChild> children = new ArrayList<>();
        if (fields.get("children") instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                Object item = list.get(index);
                String childPath = path + ".children[" + index + "]";
                ExactDecimal weight = ExactDecimal.parse("1");
                Object nodeValue = item;
                if (item instanceof Map<?, ?> child && child.containsKey("node")) {
                    weight = decimal(child.get("weight"), "1", childPath + ".weight", findings);
                    nodeValue = child.get("node");
                }
                RequirementNode childNode = parseNode(nodeValue, definitions, depth + 1, maximumDepth,
                        childPath, findings);
                if (childNode != null) {
                    try {
                        children.add(new RequirementChild(childNode, weight));
                    } catch (IllegalArgumentException exception) {
                        findings.add(error("requirement.weight.invalid", childPath, exception.getMessage(),
                                "Use a non-negative explicit weight."));
                    }
                }
            }
        } else {
            findings.add(error("requirement.children.type", path + ".children",
                    "Group children must be a YAML list.", "Add one or more child nodes."));
        }
        return new RequirementGroup(id, mode, children, threshold, catchUp, display);
    }

    private static Map<CostId, CostDefinition> costs(Object value, List<ValidationFinding> findings) {
        Map<?, ?> map = mapping(value, "requirements.costs", findings);
        LinkedHashMap<CostId, CostDefinition> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            CostId id = id(entry.getKey(), CostId::new, "requirements.costs", findings);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                continue;
            }
            String path = "requirements.costs." + id.value();
            ProviderId provider = id(fields.get("provider"), ProviderId::new, path + ".provider", findings);
            MetricValue valueAmount = typedValue(fields, "amount", path, findings);
            if (provider != null && valueAmount != null) {
                result.put(id, new CostDefinition(id, provider,
                        string(fields.get("type"), "value", path + ".type", findings), valueAmount,
                        stringMap(fields.get("metadata"), path + ".metadata", findings),
                        string(fields.get("display-name"), id.value(), path + ".display-name", findings)));
            }
        }
        return Map.copyOf(result);
    }

    private static Map<RewardId, RewardDefinition> rewards(Object value, List<ValidationFinding> findings) {
        Map<?, ?> map = mapping(value, "rewards.rewards", findings);
        LinkedHashMap<RewardId, RewardDefinition> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            RewardId id = id(entry.getKey(), RewardId::new, "rewards.rewards", findings);
            if (id == null || !(entry.getValue() instanceof Map<?, ?> fields)) {
                continue;
            }
            String path = "rewards.rewards." + id.value();
            ProviderId provider = id(fields.get("provider"), ProviderId::new, path + ".provider", findings);
            MetricValue rewardValue = typedValue(fields, "value", path, findings);
            if (provider != null && rewardValue != null) {
                result.put(id, new RewardDefinition(id, provider,
                        string(fields.get("type"), "value", path + ".type", findings), rewardValue,
                        stringMap(fields.get("metadata"), path + ".metadata", findings),
                        string(fields.get("display-name"), id.value(), path + ".display-name", findings),
                        enumValue(fields.get("failure-policy"), RewardFailurePolicy.class,
                                RewardFailurePolicy.REQUIRED, path + ".failure-policy", findings),
                        enumValue(fields.get("repeatability"), RewardRepeatability.class,
                                RewardRepeatability.ONCE_PER_OPERATION, path + ".repeatability", findings)));
            }
        }
        return Map.copyOf(result);
    }

    private static CommandActionPolicy commandPolicy(Object value, List<ValidationFinding> findings) {
        if (value == null) {
            return CommandActionPolicy.safeDefaults();
        }
        if (!(value instanceof Map<?, ?> fields)) {
            findings.add(error("command.policy.type", "rewards.command-actions",
                    "Command action policy must be a mapping.", "Use explicit enabled, allowlist, and templates."));
            return CommandActionPolicy.safeDefaults();
        }
        LinkedHashMap<String, CommandTemplate> templates = new LinkedHashMap<>();
        Map<?, ?> templateMap = mapping(fields.get("templates"), "rewards.command-actions.templates", findings);
        for (var entry : templateMap.entrySet()) {
            String id = scalar(entry.getKey());
            if (id == null || !(entry.getValue() instanceof Map<?, ?> template)) {
                findings.add(error("command.template.type", "rewards.command-actions.templates",
                        "Command template must be a mapping keyed by safe ID.", "Configure command and tokens."));
                continue;
            }
            try {
                templates.put(id, new CommandTemplate(id,
                        string(template.get("command"), "", "command", findings),
                        stringSet(template.get("tokens"), "command.tokens", findings)));
            } catch (IllegalArgumentException exception) {
                findings.add(error("command.template.invalid", "rewards.command-actions.templates." + id,
                        exception.getMessage(), "Use a safe immutable template ID."));
            }
        }
        try {
            return new CommandActionPolicy(bool(fields.get("enabled"), false, "command.enabled", findings),
                    stringSet(fields.get("allowed-roots"), "command.allowed-roots", findings),
                    stringSet(fields.get("blocked-roots"), "command.blocked-roots", findings),
                    stringSet(fields.get("allowed-tokens"), "command.allowed-tokens", findings), templates,
                    integer(fields.get("maximum-commands"), 5, "command.maximum-commands", findings),
                    integer(fields.get("maximum-length"), 256, "command.maximum-length", findings),
                    integer(fields.get("maximum-depth"), 0, "command.maximum-depth", findings));
        } catch (IllegalArgumentException exception) {
            findings.add(error("command.policy.invalid", "rewards.command-actions", exception.getMessage(),
                    "Use bounded command policy values."));
            return CommandActionPolicy.safeDefaults();
        }
    }

    private static RequirementTarget target(
            Object value,
            MetricValueType type,
            MetricOperator operator,
            String path,
            List<ValidationFinding> findings) {
        try {
            if (operator == MetricOperator.IN_RANGE) {
                if (!(value instanceof List<?> list) || list.size() != 2) {
                    throw new IllegalArgumentException("Range target must contain exactly two values");
                }
                return RequirementTarget.range(MetricValue.parse(type, scalarRequired(list.get(0))),
                        MetricValue.parse(type, scalarRequired(list.get(1))));
            }
            return RequirementTarget.single(MetricValue.parse(type, scalarRequired(value)));
        } catch (RuntimeException exception) {
            findings.add(error("requirement.target.invalid", path + ".target", exception.getMessage(),
                    "Use a target valid for metric type " + type + "."));
            return null;
        }
    }

    private static ScalingProfile scaling(Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return ScalingProfile.none();
        }
        if (!(value instanceof Map<?, ?> fields)) {
            findings.add(error("scaling.type", path, "Scaling profile must be a mapping.",
                    "Configure strategy and exact parameters."));
            return ScalingProfile.none();
        }
        if (fields.containsKey("segments") || fields.containsKey("mode") || fields.containsKey("defaults")) {
            try {
                return ScalingProfile.segmented(segmentedScaling(fields).segments());
            } catch (IllegalArgumentException exception) {
                findings.add(error(scalingCode("scaling", exception), path, exception.getMessage(),
                        "Use one compact mode or contiguous positive Prestige ranges."));
                return ScalingProfile.none();
            }
        }
        ScalingStrategy strategy = enumValue(fields.get("strategy"), ScalingStrategy.class,
                ScalingStrategy.NONE, path + ".strategy", findings);
        TargetRounding rounding = enumValue(fields.get("rounding"), TargetRounding.class,
                TargetRounding.EXACT, path + ".rounding", findings);
        ExactDecimal quantum = decimal(fields.get("quantum"), "1", path + ".quantum", findings);
        ExactDecimal parameter = switch (strategy) {
            case LINEAR -> decimal(fields.get("rate"), "0", path + ".rate", findings);
            case EXPONENTIAL -> decimal(fields.get("base"), "1", path + ".base", findings);
            case NONE, STEPPED -> ExactDecimal.ZERO;
        };
        NavigableMap<Long, ExactDecimal> steps = new TreeMap<>();
        if (fields.get("steps") instanceof Map<?, ?> stepMap) {
            for (var entry : stepMap.entrySet()) {
                try {
                    steps.put(Long.parseLong(scalarRequired(entry.getKey())),
                            ExactDecimal.parse(scalarRequired(entry.getValue())));
                } catch (RuntimeException exception) {
                    findings.add(error("scaling.step.invalid", path + ".steps", exception.getMessage(),
                            "Use non-negative integer thresholds and exact multipliers."));
                }
            }
        }
        try {
            return new ScalingProfile(strategy, parameter, steps, rounding, quantum);
        } catch (IllegalArgumentException exception) {
            findings.add(error("scaling.invalid", path, exception.getMessage(),
                    "Correct the scaling domain and bounds."));
            return ScalingProfile.none();
        }
    }

    /** Shared strict parser for compact and advanced Phase 9B requirement/cost/reward scaling. */
    public static net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile segmentedScaling(Object value) {
        Object configuredSegments = value;
        Map<?, ?> defaults = Map.of();
        if (value instanceof Map<?, ?> profile) {
            if (profile.containsKey("defaults")) {
                if (!(profile.get("defaults") instanceof Map<?, ?> configuredDefaults)) {
                    throw new IllegalArgumentException("Scaling defaults must be a mapping");
                }
                defaults = configuredDefaults;
            }
            if (profile.containsKey("segments")) {
                configuredSegments = profile.get("segments");
            } else {
                LinkedHashMap<Object, Object> shorthand = new LinkedHashMap<>();
                profile.forEach((key, configured) -> {
                    if (!"defaults".equals(key)) {
                        shorthand.put(key, configured);
                    }
                });
                configuredSegments = List.of(shorthand);
            }
        }
        return new net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile(
                scalingSegments(configuredSegments, "segments", defaults));
    }

    private static List<PrestigeScalingSegment> scalingSegments(Object value, String path, Map<?, ?> defaults) {
        if (!(value instanceof List<?> configured) || configured.isEmpty()) {
            throw new IllegalArgumentException("Segmented scaling requires a non-empty segment list");
        }
        ArrayList<Map<?, ?>> segmentFields = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            if (!(configured.get(index) instanceof Map<?, ?> fields)) {
                throw new IllegalArgumentException("Scaling segment " + index + " must be a mapping");
            }
            segmentFields.add(fields);
        }
        ArrayList<PrestigeScalingSegment> result = new ArrayList<>();
        for (int index = 0; index < segmentFields.size(); index++) {
            Map<?, ?> fields = segmentFields.get(index);
            String itemPath = path + "[" + index + "]";
            long start = fields.containsKey("start-prestige")
                    ? boundary(fields.get("start-prestige"), itemPath + ".start-prestige")
                    : inferredStart(result, index);
            String endText = fields.containsKey("end-prestige")
                    ? scalarRequired(fields.get("end-prestige"))
                    : inferredEnd(segmentFields, index);
            OptionalLong end = "unlimited".equalsIgnoreCase(endText)
                    ? OptionalLong.empty() : OptionalLong.of(boundary(endText, itemPath + ".end-prestige"));
            if (start < 1 || end.isPresent() && end.getAsLong() < start) {
                throw scaling(ScalingFailure.INVALID_RANGE,
                        "Scaling " + range(start, end) + " is not a positive inclusive range");
            }
            SegmentScalingMode mode = Enum.valueOf(SegmentScalingMode.class,
                    scalarRequired(inherited(fields, defaults, "mode", "FLAT")).toUpperCase(Locale.ROOT)
                            .replace('-', '_'));
            SegmentTransition transition = Enum.valueOf(SegmentTransition.class,
                    scalarRequired(inherited(fields, defaults, "transition", "EXPLICIT_BASE"))
                            .toUpperCase(Locale.ROOT)
                            .replace('-', '_'));
            ExactDecimal base = ExactDecimal.parse(scalarRequired(inherited(fields, defaults, "base", "1")));
            ExactDecimal rate = ExactDecimal.parse(scalarRequired(inherited(fields, defaults, "rate", "0")));
            TargetRounding rounding = Enum.valueOf(TargetRounding.class,
                    scalarRequired(inherited(fields, defaults, "rounding", "EXACT"))
                            .toUpperCase(Locale.ROOT)
                            .replace('-', '_'));
            ExactDecimal quantum = ExactDecimal.parse(scalarRequired(inherited(fields, defaults, "quantum", "1")));
            Optional<ExactDecimal> floor = optionalInheritedDecimal(fields, defaults, "floor");
            Optional<ExactDecimal> cap = optionalInheritedDecimal(fields, defaults, "cap");
            if (floor.isPresent() && cap.isPresent()
                    && floor.orElseThrow().asBigDecimal().compareTo(cap.orElseThrow().asBigDecimal()) > 0) {
                throw scaling(ScalingFailure.INVALID_BOUNDS,
                        "Scaling " + range(start, end) + ": floor exceeds cap");
            }
            TreeMap<Long, ExactDecimal> overrides = new TreeMap<>();
            if (fields.get("overrides") instanceof Map<?, ?> values) {
                for (var override : values.entrySet()) {
                    long level = boundary(override.getKey(), itemPath + ".overrides");
                    ExactDecimal amount = ExactDecimal.parse(scalarRequired(override.getValue()));
                    if (level < start || end.isPresent() && level > end.getAsLong()
                            || amount.asBigDecimal().signum() < 0) {
                        throw scaling(ScalingFailure.INVALID_OVERRIDE, "Override P" + level
                                + " is outside " + range(start, end) + " or negative");
                    }
                    overrides.put(level, amount);
                }
            } else if (fields.containsKey("overrides")) {
                throw scaling(ScalingFailure.INVALID_OVERRIDE,
                        itemPath + ".overrides must be a level-to-value mapping");
            }
            if (mode == SegmentScalingMode.MANUAL) {
                long expectedOverrides;
                try {
                    expectedOverrides = end.isPresent()
                            ? Math.addExact(Math.subtractExact(end.getAsLong(), start), 1) : -1;
                } catch (ArithmeticException exception) {
                    throw scaling(ScalingFailure.INVALID_OVERRIDE,
                            "Manual scaling " + range(start, end) + " exceeds the safe override domain");
                }
                if (expectedOverrides < 0 || expectedOverrides != overrides.size()) {
                    throw scaling(ScalingFailure.INVALID_OVERRIDE,
                            "Manual scaling " + range(start, end) + " requires one override per Prestige level");
                }
            }
            try {
                result.add(new PrestigeScalingSegment(start, end, mode, transition, base, rate, rounding, quantum,
                        floor, cap, overrides));
            } catch (IllegalArgumentException exception) {
                throw scaling(ScalingFailure.INVALID_BOUNDS,
                        "Scaling " + range(start, end) + ": " + exception.getMessage());
            }
        }
        validateSegmentTopology(result);
        return List.copyOf(result);
    }

    private static long inferredStart(List<PrestigeScalingSegment> previous, int index) {
        if (index == 0) {
            return 1;
        }
        OptionalLong priorEnd = previous.getLast().endLevel();
        if (priorEnd.isEmpty()) {
            throw scaling(ScalingFailure.AMBIGUOUS_BOUNDARY,
                    "A segment after an open-ended range needs an explicit boundary");
        }
        try {
            return Math.addExact(priorEnd.getAsLong(), 1);
        } catch (ArithmeticException exception) {
            throw scaling(ScalingFailure.AMBIGUOUS_BOUNDARY,
                    "Scaling segment start exceeds the safe level domain");
        }
    }

    private static String inferredEnd(List<Map<?, ?>> segments, int index) {
        if (index == segments.size() - 1) {
            return "unlimited";
        }
        Map<?, ?> next = segments.get(index + 1);
        if (!next.containsKey("start-prestige")) {
            throw scaling(ScalingFailure.AMBIGUOUS_BOUNDARY,
                    "A non-final segment needs end-prestige or the next segment needs start-prestige");
        }
        try {
            return Long.toString(Math.subtractExact(Long.parseLong(
                    scalarRequired(next.get("start-prestige"))), 1));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw scaling(ScalingFailure.AMBIGUOUS_BOUNDARY,
                    "Scaling segment end is not a safe whole-number boundary");
        }
    }

    private static void validateSegmentTopology(List<PrestigeScalingSegment> segments) {
        ArrayList<PrestigeScalingSegment> ordered = new ArrayList<>(segments);
        ordered.sort(Comparator.comparingLong(PrestigeScalingSegment::startLevel));
        if (ordered.getFirst().startLevel() != 1) {
            throw scaling(ScalingFailure.GAP,
                    "Scaling: gap before P" + ordered.getFirst().startLevel() + "; coverage must begin at P1");
        }
        for (int index = 0; index < ordered.size() - 1; index++) {
            PrestigeScalingSegment current = ordered.get(index);
            PrestigeScalingSegment next = ordered.get(index + 1);
            if (current.endLevel().isEmpty()) {
                throw scaling(ScalingFailure.OVERLAP,
                        "Scaling " + range(current.startLevel(), current.endLevel()) + " overlaps "
                                + range(next.startLevel(), next.endLevel()));
            }
            long expected;
            try {
                expected = Math.addExact(current.endLevel().getAsLong(), 1);
            } catch (ArithmeticException exception) {
                throw scaling(ScalingFailure.INVALID_RANGE,
                        "Scaling finite range exceeds the safe Prestige domain");
            }
            if (next.startLevel() < expected) {
                throw scaling(ScalingFailure.OVERLAP,
                        "Scaling " + range(current.startLevel(), current.endLevel()) + " overlaps "
                                + range(next.startLevel(), next.endLevel()));
            }
            if (next.startLevel() > expected) {
                throw scaling(ScalingFailure.GAP, "Scaling: gap P" + expected + "–P"
                        + (next.startLevel() - 1) + " after P" + current.endLevel().getAsLong());
            }
        }
    }

    private static long boundary(Object value, String path) {
        try {
            return Long.parseLong(scalarRequired(value));
        } catch (NumberFormatException exception) {
            throw scaling(ScalingFailure.AMBIGUOUS_BOUNDARY,
                    "Scaling boundary " + path + " must be a whole Prestige level");
        }
    }

    private static String range(long start, OptionalLong end) {
        return "P" + start + "–" + (end.isPresent() ? "P" + end.getAsLong() : "P∞");
    }

    private static ScalingConfigurationException scaling(ScalingFailure failure, String message) {
        return new ScalingConfigurationException(failure, message);
    }

    public static String scalingCode(String prefix, IllegalArgumentException exception) {
        return exception instanceof ScalingConfigurationException scaling
                ? prefix + "." + scaling.failure().code() : prefix + ".invalid";
    }

    public enum ScalingFailure {
        GAP("gap"),
        OVERLAP("overlap"),
        INVALID_RANGE("invalid_range"),
        AMBIGUOUS_BOUNDARY("ambiguous_boundary"),
        INVALID_OVERRIDE("invalid_override"),
        INVALID_BOUNDS("invalid_bounds");

        private final String code;

        ScalingFailure(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static final class ScalingConfigurationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final ScalingFailure failure;

        private ScalingConfigurationException(ScalingFailure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public ScalingFailure failure() {
            return failure;
        }
    }

    private static Object inherited(Map<?, ?> fields, Map<?, ?> defaults, String key, Object fallback) {
        if (fields.containsKey(key)) {
            return fields.get(key);
        }
        return defaults.containsKey(key) ? defaults.get(key) : fallback;
    }

    private static Optional<ExactDecimal> optionalInheritedDecimal(
            Map<?, ?> fields,
            Map<?, ?> defaults,
            String key) {
        Object value = inherited(fields, defaults, key, null);
        return value == null ? Optional.empty() : Optional.of(ExactDecimal.parse(scalarRequired(value)));
    }

    private static CatchUpProfile catchUp(Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return CatchUpProfile.disabled();
        }
        if (!(value instanceof Map<?, ?> fields)) {
            findings.add(error("catch_up.type", path, "Catch-up profile must be a mapping.",
                    "Configure an explicit enabled policy."));
            return CatchUpProfile.disabled();
        }
        try {
            Object floor = fields.get("floor");
            return new CatchUpProfile(bool(fields.get("enabled"), false, path + ".enabled", findings),
                    decimal(fields.get("start-threshold"), "0", path + ".start-threshold", findings),
                    decimal(fields.get("reduction-rate"), "0", path + ".reduction-rate", findings),
                    decimal(fields.get("maximum-reduction"), "0", path + ".maximum-reduction", findings),
                    floor == null ? Optional.empty() : Optional.of(ExactDecimal.parse(scalarRequired(floor))),
                    enumValue(fields.get("rounding"), TargetRounding.class, TargetRounding.EXACT,
                            path + ".rounding", findings),
                    decimal(fields.get("quantum"), "1", path + ".quantum", findings));
        } catch (RuntimeException exception) {
            findings.add(error("catch_up.invalid", path, exception.getMessage(),
                    "Use exact reductions between zero and one with a positive quantum."));
            return CatchUpProfile.disabled();
        }
    }

    private static MetricValue typedValue(
            Map<?, ?> fields,
            String valueKey,
            String path,
            List<ValidationFinding> findings) {
        MetricValueType type = enumValue(fields.get("value-type"), MetricValueType.class,
                MetricValueType.EXACT_DECIMAL, path + ".value-type", findings);
        try {
            return MetricValue.parse(type, scalarRequired(fields.get(valueKey)));
        } catch (RuntimeException exception) {
            findings.add(error("typed_value.invalid", path + "." + valueKey, exception.getMessage(),
                    "Use a canonical value matching " + type + "."));
            return null;
        }
    }

    private static Map<?, ?> loadDocument(
            CompiledConfiguration compiled,
            String name,
            List<ValidationFinding> findings) {
        String source = compiled.documents().get(name);
        if (source == null) {
            return Map.of();
        }
        try {
            Object loaded = new Load(SETTINGS).loadFromString(source);
            if (loaded == null) {
                return Map.of();
            }
            if (loaded instanceof Map<?, ?> map) {
                return map;
            }
            findings.add(error("phase3.document.type", name, "Canonical document must be a mapping.",
                    "Use documented top-level keys."));
        } catch (RuntimeException exception) {
            findings.add(error("phase3.yaml.invalid", name, exception.getMessage(),
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
        findings.add(error("phase3.mapping.expected", path, "Expected a YAML mapping.",
                "Use stable IDs as mapping keys."));
        return Map.of();
    }

    private static Map<String, String> stringMap(Object value, String path, List<ValidationFinding> findings) {
        Map<?, ?> map = mapping(value, path, findings);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            String key = scalar(entry.getKey());
            String item = scalar(entry.getValue());
            if (key == null || item == null) {
                findings.add(error("phase3.string_map", path, "Metadata/filter entries must be scalar strings.",
                        "Use simple key/value entries."));
            } else {
                result.put(key, item);
            }
        }
        return Map.copyOf(result);
    }

    private static Set<String> stringSet(Object value, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof List<?> list)) {
            findings.add(error("phase3.list.expected", path, "Expected a YAML list.", "Use a list of strings."));
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            String scalar = scalar(item);
            if (scalar == null || !result.add(scalar)) {
                findings.add(error("phase3.list.invalid", path, "List values must be unique scalar strings.",
                        "Remove duplicates and non-scalar values."));
            }
        }
        return Set.copyOf(result);
    }

    private static <T> T id(
            Object value,
            Function<String, T> constructor,
            String path,
            List<ValidationFinding> findings) {
        String text = scalar(value);
        if (text == null) {
            findings.add(error("phase3.id.missing", path, "A stable scalar ID is required.",
                    "Use lowercase a-z0-9._-."));
            return null;
        }
        try {
            return constructor.apply(text);
        } catch (IllegalArgumentException exception) {
            findings.add(error("phase3.id.invalid", path, exception.getMessage(),
                    "Use lowercase a-z0-9._-."));
            return null;
        }
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
            String normalized = scalarRequired(value).toUpperCase(Locale.ROOT).replace('-', '_');
            if (type == MetricValueType.class && normalized.equals("INTEGER_COUNT")) {
                return type.cast(MetricValueType.COUNT);
            }
            return Enum.valueOf(type, normalized);
        } catch (RuntimeException exception) {
            findings.add(error("phase3.enum.invalid", path, "Invalid " + type.getSimpleName() + " value.",
                    "Use one of " + java.util.Arrays.toString(type.getEnumConstants()) + "."));
            return fallback;
        }
    }

    private static int integer(Object value, int fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(scalarRequired(value));
        } catch (RuntimeException exception) {
            findings.add(error("phase3.integer.invalid", path, "Expected an integer.", "Use a whole number."));
            return fallback;
        }
    }

    private static void validateSchema(
            CompiledConfiguration compiled,
            String document,
            int schema,
            String path,
            List<ValidationFinding> findings) {
        if (compiled.documents().containsKey(document) && schema != 3) {
            findings.add(error("phase3.schema.version", path,
                    "Supported schema version is 3, but found " + schema + ".",
                    "Migrate " + document + " to schema-version: 3 before activation."));
        }
    }

    private static ExactDecimal decimal(
            Object value,
            String fallback,
            String path,
            List<ValidationFinding> findings) {
        try {
            return ExactDecimal.parse(value == null ? fallback : scalarRequired(value));
        } catch (RuntimeException exception) {
            findings.add(error("phase3.decimal.invalid", path, exception.getMessage(),
                    "Use canonical exact decimal text."));
            return ExactDecimal.parse(fallback);
        }
    }

    private static boolean bool(Object value, boolean fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        findings.add(error("phase3.boolean.invalid", path, "Expected true or false.", "Use a YAML boolean."));
        return fallback;
    }

    private static String string(Object value, String fallback, String path, List<ValidationFinding> findings) {
        if (value == null) {
            return fallback;
        }
        String text = scalar(value);
        if (text == null) {
            findings.add(error("phase3.string.invalid", path, "Expected a scalar string.", "Use text."));
            return fallback;
        }
        return text;
    }

    private static String scalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                ? String.valueOf(value) : null;
    }

    private static String scalarRequired(Object value) {
        String result = scalar(value);
        if (result == null) {
            throw new IllegalArgumentException("Expected a scalar value");
        }
        return result;
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "Invalid Phase 3 configuration cannot activate.", remediation);
    }
}
