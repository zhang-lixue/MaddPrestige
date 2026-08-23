package net.maddkraft.maddprestige.core.requirement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public final class RequirementEvaluator {
    private final Map<MetricBinding, MetricDescriptor> descriptors;
    private final TargetTransformer targets = new TargetTransformer();

    public RequirementEvaluator(Map<MetricBinding, MetricDescriptor> descriptors) {
        this.descriptors = Map.copyOf(descriptors);
    }

    public RequirementEvaluationResult evaluate(RequirementNode root, RequirementEvaluationContext context) {
        return evaluateNode(root, context);
    }

    private RequirementEvaluationResult evaluateNode(
            RequirementNode node,
            RequirementEvaluationContext context) {
        if (node instanceof RequirementLeaf leaf) {
            return evaluateLeaf(leaf.definition(), context);
        }
        return evaluateGroup((RequirementGroup) node, context);
    }

    private RequirementEvaluationResult evaluateLeaf(
            RequirementDefinition definition,
            RequirementEvaluationContext context) {
        MetricDescriptor descriptor = descriptors.get(new MetricBinding(definition.providerId(), definition.metricId()));
        if (descriptor == null) {
            return leafFailure(definition, RequirementEvaluationStatus.INVALID,
                    "Metric capability was not present in the pinned configuration.", Optional.empty(), Optional.empty());
        }
        Long expectedGeneration = context.providerGenerations().get(definition.providerId());
        MetricSample sample = context.samples().get(definition.id());
        if (expectedGeneration == null || sample == null) {
            return leafFailure(definition, RequirementEvaluationStatus.UNAVAILABLE,
                    "Pinned provider generation or metric sample is missing.", Optional.empty(), Optional.empty());
        }
        if (sample.providerGeneration() != expectedGeneration) {
            return leafFailure(definition, RequirementEvaluationStatus.UNAVAILABLE,
                    "Metric sample provider generation is stale.", Optional.empty(), Optional.empty());
        }
        if (sample.status() != MetricSampleStatus.AVAILABLE) {
            RequirementEvaluationStatus status = switch (sample.status()) {
                case UNAVAILABLE -> RequirementEvaluationStatus.UNAVAILABLE;
                case INVALID -> RequirementEvaluationStatus.INVALID;
                case ERROR -> RequirementEvaluationStatus.ERROR;
                case AVAILABLE -> throw new IllegalStateException("Handled above");
            };
            return leafFailure(definition, status,
                    sample.detail().orElse("Metric provider did not return a usable sample."),
                    Optional.empty(), Optional.empty());
        }
        MetricValue current = sample.value().orElseThrow();
        if (current.type() != descriptor.valueType()) {
            return leafFailure(definition, RequirementEvaluationStatus.ERROR,
                    "Provider returned " + current.type() + " for a " + descriptor.valueType() + " metric.",
                    Optional.of(current), Optional.empty());
        }
        if (definition.scope().requiresBaseline()) {
            Optional<net.maddkraft.maddprestige.api.id.ScopeId> instance = context.scopes().instance(definition.scope());
            if (instance.isEmpty()) {
                return leafFailure(definition, RequirementEvaluationStatus.INVALID,
                        "No explicit scope-instance identity was supplied for " + definition.scope() + ".",
                        Optional.of(current), Optional.empty());
            }
            BaselineKey key = new BaselineKey(context.playerId(), definition.id(), definition.scope(),
                    instance.orElseThrow(), definition.semanticFingerprint());
            Optional<RequirementBaseline> baseline = context.stateReader().findBaseline(key);
            if (baseline.isEmpty()) {
                return leafFailure(definition, RequirementEvaluationStatus.UNAVAILABLE,
                        "Required scope baseline has not been initialized at the lifecycle boundary.",
                        Optional.of(current), Optional.empty());
            }
            RequirementBaseline stored = baseline.orElseThrow();
            if (!stored.key().equals(key)) {
                return leafFailure(definition, RequirementEvaluationStatus.ERROR,
                        "Requirement state store returned a baseline for a different key.",
                        Optional.of(current), Optional.empty());
            }
            if (stored.providerGeneration() != expectedGeneration) {
                return leafFailure(definition, RequirementEvaluationStatus.UNAVAILABLE,
                        "Baseline belongs to a stale provider generation and needs reconciliation.",
                        Optional.of(current), Optional.empty());
            }
            try {
                if (descriptor.monotonicity() == MetricMonotonicity.MONOTONIC
                        && current.compareTo(stored.value()) < 0
                        && descriptor.resetPolicy() == MetricResetPolicy.FAIL_RECONCILIATION) {
                    return leafFailure(definition, RequirementEvaluationStatus.UNAVAILABLE,
                            "Monotonic metric fell below its baseline; reconciliation is required.",
                            Optional.of(current), Optional.empty());
                }
                current = current.subtract(stored.value());
            } catch (RuntimeException exception) {
                return leafFailure(definition, RequirementEvaluationStatus.ERROR,
                        "Could not calculate scoped metric delta: " + exception.getMessage(),
                        Optional.of(current), Optional.empty());
            }
        }
        EffectiveTarget effective;
        try {
            effective = targets.transform(definition.target(), definition.scaling(), definition.catchUp(),
                    context.scalingIndex(), context.catchUpPosition());
        } catch (RuntimeException exception) {
            return leafFailure(definition, RequirementEvaluationStatus.INVALID,
                    "Could not derive an effective target: " + exception.getMessage(),
                    Optional.of(current), Optional.empty());
        }
        Optional<LatchKey> latchKey = latchKey(definition, context);
        if (definition.completionMode() == CompletionMode.LATCHED && latchKey.isEmpty()) {
            return leafFailure(definition, RequirementEvaluationStatus.INVALID,
                    "Latched completion requires an explicit scope-instance identity.",
                    Optional.of(current), Optional.of(effective.target()));
        }
        Optional<RequirementLatch> latch = latchKey.flatMap(context.stateReader()::findLatch);
        if (latch.isPresent() && !latch.orElseThrow().key().equals(latchKey.orElseThrow())) {
            return leafFailure(definition, RequirementEvaluationStatus.ERROR,
                    "Requirement state store returned a latch for a different key.",
                    Optional.of(current), Optional.of(effective.target()));
        }
        boolean storedLatch = latch.isPresent();
        boolean comparison;
        try {
            comparison = compare(current, definition.operator(), effective.target());
        } catch (RuntimeException exception) {
            return leafFailure(definition, RequirementEvaluationStatus.ERROR,
                    "Metric comparison failed: " + exception.getMessage(), Optional.of(current),
                    Optional.of(effective.target()));
        }
        boolean satisfied = storedLatch || comparison;
        Optional<LatchKey> eligible = definition.completionMode() == CompletionMode.LATCHED
                && comparison && !storedLatch ? latchKey : Optional.empty();
        RequirementEvaluationStatus status = satisfied
                ? RequirementEvaluationStatus.SATISFIED : RequirementEvaluationStatus.UNSATISFIED;
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("requirement", definition.id().value());
        facts.put("provider", definition.providerId().value());
        facts.put("metric", definition.metricId().value());
        facts.put("scope", definition.scope().name());
        facts.put("completion", definition.completionMode().name());
        facts.put("operator", definition.operator().name());
        facts.put("current", current.canonical());
        facts.put("target", effective.target().lower().canonical());
        facts.put("effective-formula", effective.formula());
        facts.put("latched", Boolean.toString(storedLatch));
        ExplanationNode explanation = new ExplanationNode("requirement.leaf", explanation(status),
                satisfied ? "Requirement is satisfied." : "Requirement is not satisfied.", facts, List.of());
        return new RequirementEvaluationResult(definition.id(), status, Optional.of(current),
                Optional.of(effective.target()), satisfied ? ExactDecimal.parse("1") : ExactDecimal.ZERO,
                List.of(), explanation, eligible);
    }

    private RequirementEvaluationResult evaluateGroup(
            RequirementGroup group,
            RequirementEvaluationContext context) {
        List<RequirementEvaluationResult> children = new ArrayList<>();
        for (RequirementChild child : group.children()) {
            children.add(evaluateNode(child.node(), context).withContribution(child.weight()));
        }
        RequirementEvaluationStatus severe = severeFailure(children);
        BigDecimal satisfiedCount = BigDecimal.valueOf(children.stream().filter(RequirementEvaluationResult::satisfied)
                .count());
        BigDecimal unavailableCount = BigDecimal.valueOf(children.stream()
                .filter(result -> result.status() == RequirementEvaluationStatus.UNAVAILABLE).count());
        BigDecimal satisfiedPoints = BigDecimal.ZERO;
        BigDecimal unavailablePoints = BigDecimal.ZERO;
        for (RequirementEvaluationResult child : children) {
            if (child.satisfied()) {
                satisfiedPoints = satisfiedPoints.add(child.contribution().asBigDecimal());
            } else if (child.status() == RequirementEvaluationStatus.UNAVAILABLE) {
                unavailablePoints = unavailablePoints.add(child.contribution().asBigDecimal());
            }
        }
        BigDecimal threshold;
        try {
            threshold = effectiveGroupThreshold(group, context.catchUpPosition());
        } catch (RuntimeException exception) {
            return groupResult(group, RequirementEvaluationStatus.INVALID, children, BigDecimal.ZERO,
                    group.threshold().asBigDecimal(), "Invalid group threshold: " + exception.getMessage());
        }
        RequirementEvaluationStatus status;
        BigDecimal progress;
        if (severe != null) {
            status = severe;
            progress = BigDecimal.ZERO;
        } else {
            switch (group.mode()) {
                case ALL -> {
                    progress = satisfiedCount;
                    boolean knownFailure = children.stream().anyMatch(child ->
                            child.status() == RequirementEvaluationStatus.UNSATISFIED);
                    status = knownFailure ? RequirementEvaluationStatus.UNSATISFIED
                            : unavailableCount.signum() > 0 ? RequirementEvaluationStatus.UNAVAILABLE
                            : RequirementEvaluationStatus.SATISFIED;
                    threshold = BigDecimal.valueOf(children.size());
                }
                case ANY -> {
                    progress = satisfiedCount;
                    threshold = BigDecimal.ONE;
                    if (satisfiedCount.signum() > 0) {
                        status = RequirementEvaluationStatus.SATISFIED;
                    } else if (unavailableCount.signum() > 0) {
                        status = RequirementEvaluationStatus.UNAVAILABLE;
                    } else {
                        status = RequirementEvaluationStatus.UNSATISFIED;
                    }
                }
                case ANY_X_OF_Y -> {
                    progress = satisfiedCount;
                    if (satisfiedCount.compareTo(threshold) >= 0) {
                        status = RequirementEvaluationStatus.SATISFIED;
                    } else if (satisfiedCount.add(unavailableCount).compareTo(threshold) >= 0) {
                        status = RequirementEvaluationStatus.UNAVAILABLE;
                    } else {
                        status = RequirementEvaluationStatus.UNSATISFIED;
                    }
                }
                case WEIGHTED -> {
                    progress = satisfiedPoints;
                    if (satisfiedPoints.compareTo(threshold) >= 0) {
                        status = RequirementEvaluationStatus.SATISFIED;
                    } else if (satisfiedPoints.add(unavailablePoints).compareTo(threshold) >= 0) {
                        status = RequirementEvaluationStatus.UNAVAILABLE;
                    } else {
                        status = RequirementEvaluationStatus.UNSATISFIED;
                    }
                }
                default -> throw new IllegalStateException("Unexpected group mode");
            }
        }
        return groupResult(group, status, children, progress, threshold,
                status == RequirementEvaluationStatus.SATISFIED
                        ? "Requirement group is satisfied." : "Requirement group is not satisfied.");
    }

    private RequirementEvaluationResult groupResult(
            RequirementGroup group,
            RequirementEvaluationStatus status,
            List<RequirementEvaluationResult> children,
            BigDecimal progress,
            BigDecimal threshold,
            String summary) {
        Map<String, String> facts = Map.of(
                "requirement", group.id().value(),
                "mode", group.mode().name(),
                "progress", progress.toPlainString(),
                "threshold", threshold.toPlainString());
        ExplanationNode explanation = new ExplanationNode("requirement.group", explanation(status), summary,
                facts, children.stream().map(RequirementEvaluationResult::explanation).toList());
        return new RequirementEvaluationResult(group.id(), status, Optional.empty(), Optional.empty(),
                status == RequirementEvaluationStatus.SATISFIED ? ExactDecimal.parse("1") : ExactDecimal.ZERO,
                children, explanation, Optional.empty());
    }

    private static RequirementEvaluationStatus severeFailure(List<RequirementEvaluationResult> children) {
        if (children.stream().anyMatch(child -> child.status() == RequirementEvaluationStatus.ERROR)) {
            return RequirementEvaluationStatus.ERROR;
        }
        if (children.stream().anyMatch(child -> child.status() == RequirementEvaluationStatus.INVALID)) {
            return RequirementEvaluationStatus.INVALID;
        }
        return null;
    }

    private static BigDecimal effectiveGroupThreshold(RequirementGroup group, ExactDecimal position) {
        BigDecimal threshold = group.threshold().asBigDecimal();
        if (!group.catchUp().enabled()) {
            return threshold;
        }
        BigDecimal reduced = threshold.multiply(BigDecimal.ONE.subtract(group.catchUp().reduction(position)));
        if (group.catchUp().floor().isPresent()) {
            reduced = reduced.max(group.catchUp().floor().orElseThrow().asBigDecimal());
        }
        return group.catchUp().rounding().apply(reduced, group.catchUp().roundingQuantum().asBigDecimal());
    }

    private static Optional<LatchKey> latchKey(
            RequirementDefinition definition,
            RequirementEvaluationContext context) {
        if (definition.completionMode() != CompletionMode.LATCHED) {
            return Optional.empty();
        }
        return context.scopes().instance(definition.scope()).map(scope -> new LatchKey(context.playerId(),
                definition.id(), definition.scope(), scope, definition.semanticFingerprint()));
    }

    private static boolean compare(MetricValue current, MetricOperator operator, RequirementTarget target) {
        int relation = current.compareTo(target.lower());
        return switch (operator) {
            case GREATER_THAN -> relation > 0;
            case GREATER_OR_EQUAL -> relation >= 0;
            case LESS_THAN -> relation < 0;
            case LESS_OR_EQUAL -> relation <= 0;
            case EQUAL -> relation == 0;
            case NOT_EQUAL -> relation != 0;
            case IN_RANGE -> relation >= 0 && current.compareTo(target.upper().orElseThrow()) <= 0;
        };
    }

    private static RequirementEvaluationResult leafFailure(
            RequirementDefinition definition,
            RequirementEvaluationStatus status,
            String summary,
            Optional<MetricValue> current,
            Optional<RequirementTarget> target) {
        ExplanationNode explanation = new ExplanationNode("requirement.leaf", explanation(status), summary,
                Map.of("requirement", definition.id().value(), "provider", definition.providerId().value(),
                        "metric", definition.metricId().value()),
                List.of());
        return new RequirementEvaluationResult(definition.id(), status, current, target, ExactDecimal.ZERO,
                List.of(), explanation, Optional.empty());
    }

    private static ExplanationStatus explanation(RequirementEvaluationStatus status) {
        return switch (status) {
            case SATISFIED -> ExplanationStatus.SATISFIED;
            case UNSATISFIED -> ExplanationStatus.UNSATISFIED;
            case UNAVAILABLE -> ExplanationStatus.UNAVAILABLE;
            case INVALID -> ExplanationStatus.INVALID;
            case ERROR -> ExplanationStatus.ERROR;
        };
    }
}
