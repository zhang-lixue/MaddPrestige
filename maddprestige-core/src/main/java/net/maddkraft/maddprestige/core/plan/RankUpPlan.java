package net.maddkraft.maddprestige.core.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationResult;
import net.maddkraft.maddprestige.core.stage.StageProjection;

/**
 * Immutable canonical rank-up result. Executable plans carry an authorization seal over every execution-relevant
 * component; blocked plans carry denied authority. {@code unavailableProviders} is diagnostic only and is not
 * consumed by execution. {@code expectedPlayerConfigRevision} binds source-row provenance while
 * {@code configRevision} is the current active configuration governing the operation and successful write.
 */
public record RankUpPlan(
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
        Set<ProviderId> unavailableProviders,
        List<String> blockers,
        boolean executionAllowed,
        OperationPlan operationPlan,
        RankUpAuthorization authorization) {
    public RankUpPlan {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        sourceStage = Objects.requireNonNull(sourceStage, "source stage");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        if (expectedStateRevision < 0) {
            throw new IllegalArgumentException("Expected state revision cannot be negative");
        }
        expectedPlayerConfigRevision = Objects.requireNonNull(expectedPlayerConfigRevision,
                "expected player configuration revision");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        requirementEvaluation = Objects.requireNonNull(requirementEvaluation, "requirement evaluation");
        costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
        rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        externalRankProjection = Objects.requireNonNull(externalRankProjection, "external rank projection");
        rankProjectionRequest = Objects.requireNonNull(rankProjectionRequest, "rank projection request");
        unavailableProviders = Set.copyOf(Objects.requireNonNull(unavailableProviders, "unavailable providers"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        operationPlan = Objects.requireNonNull(operationPlan, "operation plan");
        authorization = Objects.requireNonNull(authorization, "authorization");
        if (executionAllowed != blockers.isEmpty()) {
            throw new IllegalArgumentException("Execution allowance must agree with blockers");
        }
    }

    public RequirementEvaluationResult requirements() {
        return requirementEvaluation.result();
    }

    public static String compensationActionId(String costActionId) {
        return "compensate-" + Objects.requireNonNull(costActionId, "cost action ID");
    }
}
