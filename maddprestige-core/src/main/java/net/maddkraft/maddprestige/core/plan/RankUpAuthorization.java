package net.maddkraft.maddprestige.core.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationResult;
import net.maddkraft.maddprestige.core.stage.StageProjection;

/** Opaque seal binding the exact canonical authorization inputs to one immutable plan. */
public final class RankUpAuthorization {
    private final OperationId operationId;
    private final UUID playerId;
    private final StageId sourceStage;
    private final StageId targetStage;
    private final long expectedStateRevision;
    private final ConfigRevisionId expectedPlayerConfigRevision;
    private final ConfigRevisionId configRevision;
    private final Map<ProviderId, Long> providerGenerations;
    private final RequirementEvaluationBinding requirementBinding;
    private final RequirementEvaluationResult requirementResult;
    private final List<PlannedCost> costs;
    private final List<PlannedReward> rewards;
    private final Optional<StageProjection> externalRankProjection;
    private final Optional<RankProjectionRequest> rankProjectionRequest;
    private final List<String> blockers;
    private final boolean executionAllowed;
    private final OperationPlan operationPlan;
    private final boolean issued;

    RankUpAuthorization(
            OperationId operationId,
            UUID playerId,
            StageId sourceStage,
            StageId targetStage,
            long expectedStateRevision,
            ConfigRevisionId expectedPlayerConfigRevision,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> providerGenerations,
            BoundRequirementEvaluation requirementEvaluation,
            List<PlannedCost> costs,
            List<PlannedReward> rewards,
            Optional<StageProjection> externalRankProjection,
            Optional<RankProjectionRequest> rankProjectionRequest,
            OperationPlan operationPlan) {
        this.operationId = Objects.requireNonNull(operationId, "operation ID");
        this.playerId = Objects.requireNonNull(playerId, "player ID");
        this.sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        this.targetStage = Objects.requireNonNull(targetStage, "target stage");
        this.expectedStateRevision = expectedStateRevision;
        this.expectedPlayerConfigRevision = Objects.requireNonNull(expectedPlayerConfigRevision,
                "expected player configuration revision");
        this.configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        this.providerGenerations = Map.copyOf(providerGenerations);
        BoundRequirementEvaluation evaluation = Objects.requireNonNull(requirementEvaluation,
                "requirement evaluation");
        this.requirementBinding = evaluation.binding();
        this.requirementResult = evaluation.result();
        this.costs = List.copyOf(costs);
        this.rewards = List.copyOf(rewards);
        this.externalRankProjection = Objects.requireNonNull(externalRankProjection, "external rank projection");
        this.rankProjectionRequest = Objects.requireNonNull(rankProjectionRequest, "rank projection request");
        this.blockers = List.of();
        this.executionAllowed = true;
        this.operationPlan = Objects.requireNonNull(operationPlan, "operation plan");
        this.issued = true;
    }

    private RankUpAuthorization() {
        operationId = null;
        playerId = null;
        sourceStage = null;
        targetStage = null;
        expectedStateRevision = -1;
        expectedPlayerConfigRevision = null;
        configRevision = null;
        providerGenerations = Map.of();
        requirementBinding = null;
        requirementResult = null;
        costs = List.of();
        rewards = List.of();
        externalRankProjection = Optional.empty();
        rankProjectionRequest = Optional.empty();
        blockers = List.of();
        executionAllowed = false;
        operationPlan = null;
        issued = false;
    }

    static RankUpAuthorization denied() {
        return new RankUpAuthorization();
    }

    public boolean matches(RankUpPlan plan) {
        return issued && operationId.equals(plan.operationId()) && playerId.equals(plan.playerId())
                && sourceStage.equals(plan.sourceStage()) && targetStage.equals(plan.targetStage())
                && expectedStateRevision == plan.expectedStateRevision()
                && expectedPlayerConfigRevision.equals(plan.expectedPlayerConfigRevision())
                && configRevision.equals(plan.configRevision())
                && providerGenerations.equals(plan.providerGenerations())
                && requirementBinding.equals(plan.requirementEvaluation().binding())
                && requirementResult.equals(plan.requirementEvaluation().result())
                && costs.equals(plan.costs()) && rewards.equals(plan.rewards())
                && externalRankProjection.equals(plan.externalRankProjection())
                && rankProjectionRequest.equals(plan.rankProjectionRequest())
                && blockers.equals(plan.blockers()) && executionAllowed == plan.executionAllowed()
                && operationPlan.equals(plan.operationPlan());
    }
}
