package net.maddkraft.maddprestige.core.requirement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record RequirementEvaluationResult(
        RequirementId id,
        RequirementEvaluationStatus status,
        Optional<MetricValue> currentValue,
        Optional<RequirementTarget> effectiveTarget,
        ExactDecimal contribution,
        List<RequirementEvaluationResult> children,
        ExplanationNode explanation,
        Optional<LatchKey> latchEligible) {
    public RequirementEvaluationResult {
        id = Objects.requireNonNull(id, "requirement ID");
        status = Objects.requireNonNull(status, "status");
        currentValue = Objects.requireNonNull(currentValue, "current value");
        effectiveTarget = Objects.requireNonNull(effectiveTarget, "effective target");
        contribution = Objects.requireNonNull(contribution, "contribution");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        explanation = Objects.requireNonNull(explanation, "explanation");
        latchEligible = Objects.requireNonNull(latchEligible, "latch eligibility");
    }

    public boolean satisfied() {
        return status == RequirementEvaluationStatus.SATISFIED;
    }

    public List<LatchKey> eligibleLatches() {
        return java.util.stream.Stream.concat(latchEligible.stream(),
                children.stream().flatMap(child -> child.eligibleLatches().stream())).toList();
    }

    public RequirementEvaluationResult withContribution(ExactDecimal replacement) {
        return new RequirementEvaluationResult(id, status, currentValue, effectiveTarget, replacement,
                children, explanation, latchEligible);
    }
}
