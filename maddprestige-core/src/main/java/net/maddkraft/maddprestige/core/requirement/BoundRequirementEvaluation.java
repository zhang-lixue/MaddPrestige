package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;

/** Evaluation plus immutable provenance, issued only by RequirementEvaluationAuthorizer. */
public final class BoundRequirementEvaluation {
    private final RequirementEvaluationResult result;
    private final RequirementEvaluationBinding binding;

    BoundRequirementEvaluation(RequirementEvaluationResult result, RequirementEvaluationBinding binding) {
        this.result = Objects.requireNonNull(result, "result");
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    public RequirementEvaluationResult result() {
        return result;
    }

    public RequirementEvaluationBinding binding() {
        return binding;
    }
}
