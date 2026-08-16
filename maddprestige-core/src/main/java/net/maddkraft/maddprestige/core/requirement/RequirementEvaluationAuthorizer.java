package net.maddkraft.maddprestige.core.requirement;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.explanation.ExplanationStatus;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public final class RequirementEvaluationAuthorizer {
    private final RequirementEvaluator evaluator;

    public RequirementEvaluationAuthorizer(RequirementEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public BoundRequirementEvaluation evaluate(RequirementNode tree, RequirementEvaluationContext context) {
        Objects.requireNonNull(tree, "requirement tree");
        RequirementEvaluationResult result = evaluator.evaluate(tree, context);
        return bind(result, Optional.of(tree.id()), RequirementTreeSemantics.fingerprint(tree), context);
    }

    public BoundRequirementEvaluation noRequirements(RequirementEvaluationContext context) {
        RequirementId id = new RequirementId("no_requirements");
        RequirementEvaluationResult result = new RequirementEvaluationResult(id,
                RequirementEvaluationStatus.SATISFIED, Optional.empty(), Optional.empty(), ExactDecimal.parse("1"),
                List.of(), new ExplanationNode("requirements.none", ExplanationStatus.SATISFIED,
                        "Target stage has no configured requirement tree.", Map.of(), List.of()), Optional.empty());
        return bind(result, Optional.empty(), RequirementTreeSemantics.none(), context);
    }

    private static BoundRequirementEvaluation bind(
            RequirementEvaluationResult result,
            Optional<RequirementId> treeId,
            String treeIdentity,
            RequirementEvaluationContext context) {
        RequirementEvaluationBinding binding = new RequirementEvaluationBinding(context.playerId(), treeId,
                treeIdentity, context.configRevision(), context.providerGenerations(), context.scopes().instances());
        return new BoundRequirementEvaluation(result, binding);
    }
}
