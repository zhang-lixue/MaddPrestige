package net.maddkraft.maddprestige.core.prestige;

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
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.reward.PlannedReward;

public record PrestigePlan(
        OperationId operationId,
        UUID requestId,
        UUID playerId,
        long expectedStageRevision,
        long expectedPrestigeRevision,
        ConfigRevisionId configRevision,
        Map<ProviderId, Long> providerGenerations,
        PrestigeSimulation simulation,
        List<PlannedCost> costs,
        List<PlannedReward> rewards,
        Optional<ProviderId> rankProviderId,
        Optional<RankProjectionRequest> rankProjectionRequest,
        Set<ProviderId> unavailableProviders,
        List<String> blockers,
        boolean executionAllowed,
        OperationPlan operationPlan,
        PrestigeAuthorization authorization) {
    public PrestigePlan {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        requestId = Objects.requireNonNull(requestId, "request ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        if (expectedStageRevision < 0 || expectedPrestigeRevision < 0) {
            throw new IllegalArgumentException("Expected revisions cannot be negative");
        }
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        simulation = Objects.requireNonNull(simulation, "simulation");
        costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
        rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        rankProviderId = Objects.requireNonNull(rankProviderId, "rank provider ID");
        rankProjectionRequest = Objects.requireNonNull(rankProjectionRequest, "rank projection request");
        unavailableProviders = Set.copyOf(Objects.requireNonNull(unavailableProviders, "unavailable providers"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        operationPlan = Objects.requireNonNull(operationPlan, "operation plan");
        authorization = Objects.requireNonNull(authorization, "authorization");
        if (executionAllowed != blockers.isEmpty()) {
            throw new IllegalArgumentException("Execution allowance must agree with blockers");
        }
        if (rankProviderId.isPresent() != rankProjectionRequest.isPresent()) {
            throw new IllegalArgumentException("Rank provider and projection request must be present together");
        }
    }

    public PrestigePlan(
            OperationId operationId,
            UUID playerId,
            long expectedStageRevision,
            long expectedPrestigeRevision,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> providerGenerations,
            PrestigeSimulation simulation,
            List<PlannedCost> costs,
            List<PlannedReward> rewards,
            Optional<ProviderId> rankProviderId,
            Optional<RankProjectionRequest> rankProjectionRequest,
            Set<ProviderId> unavailableProviders,
            List<String> blockers,
            boolean executionAllowed,
            OperationPlan operationPlan,
            PrestigeAuthorization authorization) {
        this(operationId, UUID.randomUUID(), playerId, expectedStageRevision, expectedPrestigeRevision,
                configRevision, providerGenerations, simulation, costs, rewards, rankProviderId,
                rankProjectionRequest, unavailableProviders, blockers, executionAllowed, operationPlan,
                authorization);
    }
}
