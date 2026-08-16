package net.maddkraft.maddprestige.core.requirement;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;

public final class RequirementTreeValidator {
    public static final int ABSOLUTE_MAX_DEPTH = 64;

    public ValidationReport validate(
            RequirementNode root,
            Map<MetricBinding, MetricDescriptor> descriptors,
            int maximumDepth) {
        if (maximumDepth < 1 || maximumDepth > ABSOLUTE_MAX_DEPTH) {
            return ValidationReport.of(List.of(error("requirement.depth.limit", "requirements",
                    "Maximum depth must be between 1 and " + ABSOLUTE_MAX_DEPTH,
                    "Choose a bounded maximum nesting depth.")));
        }
        java.util.ArrayList<ValidationFinding> findings = new java.util.ArrayList<>();
        visit(root, descriptors, maximumDepth, 1, new HashSet<>(), findings);
        return ValidationReport.of(findings);
    }

    private static void visit(
            RequirementNode node,
            Map<MetricBinding, MetricDescriptor> descriptors,
            int maximumDepth,
            int depth,
            Set<String> ids,
            List<ValidationFinding> findings) {
        String path = "requirements." + node.id().value();
        if (depth > maximumDepth) {
            findings.add(error("requirement.depth.exceeded", path,
                    "Requirement tree exceeds configured maximum depth " + maximumDepth,
                    "Reduce nesting or intentionally raise the safe maximum."));
            return;
        }
        if (!ids.add(node.id().value())) {
            findings.add(error("requirement.id.duplicate", path,
                    "Requirement and group IDs must be unique within one tree.",
                    "Assign a distinct immutable ID."));
        }
        if (node instanceof RequirementLeaf leaf) {
            validateLeaf(leaf.definition(), descriptors, path, findings);
            return;
        }
        RequirementGroup group = (RequirementGroup) node;
        if (group.children().isEmpty()) {
            findings.add(error("requirement.group.empty", path, "Requirement group cannot be empty.",
                    "Add at least one child or remove the group."));
        }
        if (group.threshold().asBigDecimal().signum() <= 0) {
            findings.add(error("requirement.group.threshold", path,
                    "Group threshold must be positive.", "Use a positive threshold."));
        }
        if (group.mode() == RequirementGroupMode.ANY_X_OF_Y) {
            try {
                int threshold = group.threshold().asBigDecimal().intValueExact();
                if (threshold < 1 || threshold > group.children().size()) {
                    findings.add(error("requirement.group.any_x.impossible", path,
                            "ANY_X threshold must be between 1 and the number of children.",
                            "Choose a reachable integer threshold."));
                }
            } catch (ArithmeticException exception) {
                findings.add(error("requirement.group.any_x.integer", path,
                        "ANY_X threshold must be an integer.", "Use a whole-number threshold."));
            }
        }
        if (group.mode() == RequirementGroupMode.WEIGHTED) {
            BigDecimal maximum = group.children().stream().map(child -> child.weight().asBigDecimal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (group.threshold().asBigDecimal().compareTo(maximum) > 0) {
                findings.add(error("requirement.group.weighted.impossible", path,
                        "Weighted threshold exceeds all available points.",
                        "Lower the threshold or increase explicit child weights."));
            }
        }
        if (group.catchUp().enabled()
                && group.mode() != RequirementGroupMode.ANY_X_OF_Y
                && group.mode() != RequirementGroupMode.WEIGHTED) {
            findings.add(error("requirement.group.catch_up.unsupported", path,
                    "Group catch-up is supported only for threshold-based ANY_X or WEIGHTED groups.",
                    "Move catch-up to leaves or use a threshold-based group."));
        }
        for (RequirementChild child : group.children()) {
            if (group.mode() == RequirementGroupMode.WEIGHTED && child.weight().asBigDecimal().signum() <= 0) {
                findings.add(error("requirement.group.weight.invalid", path,
                        "Weighted children must contribute positive points.", "Use a positive child weight."));
            }
            visit(child.node(), descriptors, maximumDepth, depth + 1, ids, findings);
        }
    }

    private static void validateLeaf(
            RequirementDefinition definition,
            Map<MetricBinding, MetricDescriptor> descriptors,
            String path,
            List<ValidationFinding> findings) {
        MetricDescriptor descriptor = descriptors.get(new MetricBinding(definition.providerId(), definition.metricId()));
        if (descriptor == null) {
            // Provider availability is stage-reference-sensitive and is validated against the active ladder.
            // The definition remains strongly typed by its explicit target while dormant.
            return;
        }
        if (descriptor.valueType() != definition.target().lower().type()) {
            findings.add(error("requirement.target.type", path,
                    "Target type " + definition.target().lower().type() + " does not match metric type "
                            + descriptor.valueType(), "Use a target with the advertised metric type."));
        }
        if (!definition.operator().supports(descriptor.valueType())
                || !descriptor.supportedOperators().contains(definition.operator())) {
            findings.add(error("requirement.operator.unsupported", path,
                    "Operator " + definition.operator() + " is not supported for this metric.",
                    "Choose one of " + descriptor.supportedOperators() + "."));
        }
        if ((definition.operator() == MetricOperator.IN_RANGE) != definition.target().upper().isPresent()) {
            findings.add(error("requirement.target.range", path,
                    "IN_RANGE requires two targets and every other operator requires one.",
                    "Correct the target shape."));
        }
        MetricReadMode read = definition.scope().readMode();
        if (!descriptor.supportedReads().contains(read)) {
            findings.add(error("requirement.scope.unsupported", path,
                    "Metric does not support the " + definition.scope() + " measurement scope.",
                    "Use a supported scope or another metric."));
        }
        if (definition.scope().requiresBaseline() && !descriptor.snapshotDeltaSupported()) {
            findings.add(error("requirement.scope.delta_unsupported", path,
                    "Metric cannot provide snapshot/delta measurement.",
                    "Use absolute/lifetime measurement or a delta-capable metric."));
        }
        Set<String> unknownFilters = new HashSet<>(definition.filters().keySet());
        unknownFilters.removeAll(descriptor.dimensions().keySet());
        if (!unknownFilters.isEmpty()) {
            findings.add(error("requirement.filters.unknown", path,
                    "Metric does not advertise filters " + unknownFilters + ".",
                    "Remove unsupported filters or choose a capable metric."));
        }
        for (var dimension : descriptor.dimensions().values()) {
            String value = definition.filters().get(dimension.id());
            if (dimension.required() && value == null) {
                findings.add(error("requirement.filter.required", path,
                        "Required metric filter is missing: " + dimension.id(),
                        "Configure a valid " + dimension.id() + " value."));
            } else if (value != null && !dimension.allowedValues().isEmpty()
                    && !dimension.allowedValues().contains(value)) {
                findings.add(error("requirement.filter.invalid", path,
                        "Unsupported " + dimension.id() + " filter value: " + value,
                        "Choose one of the provider-advertised values."));
            }
        }
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "Invalid requirement configuration fails closed.", remediation);
    }
}
