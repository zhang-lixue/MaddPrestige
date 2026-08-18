package net.maddkraft.maddprestige.core.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.PreflightStatus;
import net.maddkraft.maddprestige.api.cost.CostPreflight;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.OperationActionPlan;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ProjectionPolicy;
import net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation;

public final class RankUpPlanner {
    private static final ProviderId INTERNAL_PROVIDER = new ProviderId("maddprestige.internal");
    private final ProviderRegistry providers;

    public RankUpPlanner(ProviderRegistry providers) {
        this.providers = java.util.Objects.requireNonNull(providers, "provider registry");
    }

    public CompletionStage<RankUpPlan> plan(RankUpPlanningRequest request) {
        if (!request.canonical()) {
            return CompletableFuture.failedFuture(new SecurityException(
                    "Caller-composed rank-up requests are not an authorization boundary"));
        }
        OperationId operationId = distinctOperationId(request.requestId());
        ArrayList<String> blockers = new ArrayList<>();
        LinkedHashSet<ProviderId> unavailable = new LinkedHashSet<>();
        validateRequirementBinding(request, blockers);
        if (!request.requirements().satisfied()) {
            blockers.add("Requirement tree is " + request.requirements().status());
        }
        List<ProposedCost> costs = proposeCosts(operationId, request, blockers, unavailable);
        List<ProposedReward> rewards = proposeRewards(operationId, request, blockers, unavailable);
        List<CompletableFuture<CostPreflight>> costFutures = preflightCosts(costs);
        List<CompletableFuture<RewardPreflight>> rewardFutures = preflightRewards(rewards);
        CompletableFuture<?>[] all = java.util.stream.Stream.concat(costFutures.stream(), rewardFutures.stream())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all).handle((ignored, failure) -> assemble(operationId, request, costs,
                rewards, costFutures, rewardFutures, blockers, unavailable));
    }

    private static OperationId distinctOperationId(java.util.UUID requestId) {
        OperationId candidate;
        do {
            candidate = OperationId.random();
        } while (candidate.value().equals(requestId));
        return candidate;
    }

    private static List<CompletableFuture<CostPreflight>> preflightCosts(List<ProposedCost> proposals) {
        LinkedHashMap<CostProvider, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < proposals.size(); index++) {
            grouped.computeIfAbsent(proposals.get(index).provider, ignored -> new ArrayList<>()).add(index);
        }
        ArrayList<CompletableFuture<CostPreflight>> results = new ArrayList<>(
                java.util.Collections.nCopies(proposals.size(), null));
        grouped.forEach((provider, indexes) -> {
            List<PlannedCost> plans = indexes.stream().map(index -> proposals.get(index).plan).toList();
            CompletableFuture<List<CostPreflight>> batch;
            try {
                CompletionStage<List<CostPreflight>> stage = provider.preflightBatch(plans);
                batch = requireStage(stage, "Cost provider returned a null preflight stage");
            } catch (RuntimeException exception) {
                batch = CompletableFuture.failedFuture(exception);
            }
            for (int position = 0; position < indexes.size(); position++) {
                int resultIndex = indexes.get(position);
                int batchIndex = position;
                results.set(resultIndex, batch.thenApply(preflights -> requireBatchSize(preflights, plans.size())
                        .get(batchIndex)));
            }
        });
        return List.copyOf(results);
    }

    private static List<CompletableFuture<RewardPreflight>> preflightRewards(List<ProposedReward> proposals) {
        LinkedHashMap<RewardProvider, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < proposals.size(); index++) {
            grouped.computeIfAbsent(proposals.get(index).provider, ignored -> new ArrayList<>()).add(index);
        }
        ArrayList<CompletableFuture<RewardPreflight>> results = new ArrayList<>(
                java.util.Collections.nCopies(proposals.size(), null));
        grouped.forEach((provider, indexes) -> {
            List<PlannedReward> plans = indexes.stream().map(index -> proposals.get(index).plan).toList();
            CompletableFuture<List<RewardPreflight>> batch;
            try {
                CompletionStage<List<RewardPreflight>> stage = provider.preflightBatch(plans);
                batch = requireStage(stage, "Reward provider returned a null preflight stage");
            } catch (RuntimeException exception) {
                batch = CompletableFuture.failedFuture(exception);
            }
            for (int position = 0; position < indexes.size(); position++) {
                int resultIndex = indexes.get(position);
                int batchIndex = position;
                results.set(resultIndex, batch.thenApply(preflights -> requireBatchSize(preflights, plans.size())
                        .get(batchIndex)));
            }
        });
        return List.copyOf(results);
    }

    private static <T> List<T> requireBatchSize(List<T> values, int expected) {
        List<T> immutable = List.copyOf(values);
        if (immutable.size() != expected) {
            throw new IllegalStateException("Provider batch preflight returned " + immutable.size()
                    + " results for " + expected + " immutable proposals");
        }
        return immutable;
    }

    private static <T> CompletableFuture<T> requireStage(CompletionStage<T> stage, String message) {
        if (stage == null) {
            throw new IllegalStateException(message);
        }
        return stage.toCompletableFuture();
    }

    private List<ProposedCost> proposeCosts(
            OperationId operationId,
            RankUpPlanningRequest request,
            List<String> blockers,
            Set<ProviderId> unavailable) {
        ArrayList<ProposedCost> proposals = new ArrayList<>();
        for (int index = 0; index < request.costs().size(); index++) {
            var definition = request.costs().get(index);
            Provider provider = usablePinned(definition.providerId(), request, blockers, unavailable, true)
                    .orElse(null);
            if (!(provider instanceof CostProvider costProvider)) {
                if (provider != null) {
                    blockers.add("Provider does not implement cost contract: " + definition.providerId().value());
                }
                continue;
            }
            try {
                if (costProvider.validate(definition).hasErrors()) {
                    blockers.add("Cost definition is invalid: " + definition.id().value());
                    continue;
                }
            } catch (RuntimeException exception) {
                blockers.add("Cost provider validation failed: " + rootMessage(exception));
                continue;
            }
            long generation = request.pinnedProviderGenerations().get(definition.providerId());
            PlannedCost planned = new PlannedCost(operationId, "cost-" + index, request.playerId(), definition,
                    request.configRevision(), generation, costProvider.characteristics(definition),
                    "consume " + definition.displayName());
            proposals.add(new ProposedCost(costProvider, planned));
        }
        return List.copyOf(proposals);
    }

    private List<ProposedReward> proposeRewards(
            OperationId operationId,
            RankUpPlanningRequest request,
            List<String> blockers,
            Set<ProviderId> unavailable) {
        ArrayList<ProposedReward> proposals = new ArrayList<>();
        for (int index = 0; index < request.rewards().size(); index++) {
            var definition = request.rewards().get(index);
            boolean required = definition.failurePolicy() == RewardFailurePolicy.REQUIRED;
            Provider provider = usablePinned(definition.providerId(), request, blockers, unavailable, required)
                    .orElse(null);
            if (!(provider instanceof RewardProvider rewardProvider)) {
                if (provider != null || required) {
                    blockers.add("Required provider does not implement reward contract: "
                            + definition.providerId().value());
                }
                continue;
            }
            try {
                if (rewardProvider.validate(definition).hasErrors()) {
                    blockers.add("Reward definition is invalid: " + definition.id().value());
                    continue;
                }
            } catch (RuntimeException exception) {
                unavailable.add(definition.providerId());
                if (required) {
                    blockers.add("Required reward provider validation failed: " + rootMessage(exception));
                }
                continue;
            }
            long generation = request.pinnedProviderGenerations().get(definition.providerId());
            PlannedReward planned = new PlannedReward(operationId, "reward-" + index, request.playerId(), definition,
                    request.configRevision(), generation, rewardProvider.characteristics(definition),
                    "apply " + definition.displayName());
            proposals.add(new ProposedReward(rewardProvider, planned));
        }
        return List.copyOf(proposals);
    }

    private Optional<Provider> usablePinned(
            ProviderId providerId,
            RankUpPlanningRequest request,
            List<String> blockers,
            Set<ProviderId> unavailable,
            boolean required) {
        Long generation = request.pinnedProviderGenerations().get(providerId);
        providers.refreshHealth(providerId);
        var snapshot = providers.find(providerId);
        if (generation == null || snapshot.isEmpty() || snapshot.orElseThrow().generation() != generation
                || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                || !healthy(snapshot.orElseThrow().health().state())) {
            unavailable.add(providerId);
            if (required) {
                blockers.add("Provider is unavailable or stale: " + providerId.value());
            }
            return Optional.empty();
        }
        return providers.provider(providerId);
    }

    private RankUpPlan assemble(
            OperationId operationId,
            RankUpPlanningRequest request,
            List<ProposedCost> proposedCosts,
            List<ProposedReward> proposedRewards,
            List<CompletableFuture<CostPreflight>> costFutures,
            List<CompletableFuture<RewardPreflight>> rewardFutures,
            List<String> initialBlockers,
            Set<ProviderId> unavailable) {
        ArrayList<String> blockers = new ArrayList<>(initialBlockers);
        ArrayList<PlannedCost> costs = new ArrayList<>();
        ArrayList<PlannedReward> rewards = new ArrayList<>();
        for (int index = 0; index < costFutures.size(); index++) {
            CompletableFuture<CostPreflight> future = costFutures.get(index);
            try {
                CostPreflight preflight = future.join();
                if (preflight.status() != PreflightStatus.READY) {
                    blockers.add("Cost preflight blocked: " + preflight.detail());
                    if (preflight.status() == PreflightStatus.UNAVAILABLE) {
                        unavailable.add(proposedCosts.get(index).plan.definition().providerId());
                    }
                } else if (!preflight.plannedCost().orElseThrow().equals(proposedCosts.get(index).plan)) {
                    blockers.add("Cost provider altered the immutable proposed plan: "
                            + proposedCosts.get(index).plan.definition().id().value());
                } else {
                    costs.add(preflight.plannedCost().orElseThrow());
                }
            } catch (RuntimeException exception) {
                blockers.add("Cost provider preflight failed: " + rootMessage(exception));
            }
        }
        for (int index = 0; index < rewardFutures.size(); index++) {
            try {
                RewardPreflight preflight = rewardFutures.get(index).join();
                var definition = proposedRewards.get(index).plan.definition();
                if (preflight.status() != PreflightStatus.READY) {
                    if (preflight.status() == PreflightStatus.UNAVAILABLE) {
                        unavailable.add(definition.providerId());
                    }
                    if (definition.failurePolicy() == RewardFailurePolicy.REQUIRED) {
                        blockers.add("Required reward preflight blocked: " + preflight.detail());
                    }
                } else if (!preflight.plannedReward().orElseThrow().equals(proposedRewards.get(index).plan)) {
                    blockers.add("Reward provider altered the immutable proposed plan: " + definition.id().value());
                } else {
                    rewards.add(preflight.plannedReward().orElseThrow());
                }
            } catch (RuntimeException exception) {
                var definition = proposedRewards.get(index).plan.definition();
                unavailable.add(definition.providerId());
                if (definition.failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    blockers.add("Required reward provider preflight failed: " + rootMessage(exception));
                }
            }
        }
        BoundRequirementEvaluation evaluation = request.boundRequirements().orElseThrow();
        Optional<RankProjectionRequest> projectionRequest = projectionRequest(
                operationId, request, blockers, unavailable);
        List<OperationActionPlan> actions = new ArrayList<>();
        costs.forEach(cost -> actions.add(new OperationActionPlan(cost.actionId(), cost.definition().providerId(),
                "cost", cost.redactedPreview(), cost.characteristics().reversible(),
                cost.characteristics().idempotent())));
        projectionRequest.ifPresent(projection -> actions.add(new OperationActionPlan("rank-projection",
                request.targetStage().projection().providerId().orElseThrow(), "managed-direct-membership",
                "Project pinned managed progression membership", false, true)));
        actions.add(new OperationActionPlan("stage-transition", INTERNAL_PROVIDER, "stage-transition",
                request.playerState().stageId().value() + " -> " + request.targetStage().id().value(), false, true));
        rewards.forEach(reward -> actions.add(new OperationActionPlan(reward.actionId(),
                reward.definition().providerId(), "reward", reward.redactedPreview(),
                reward.characteristics().reversible(), reward.characteristics().idempotent())));
        costs.stream().filter(cost -> cost.characteristics().reversible()).forEach(cost -> actions.add(
                new OperationActionPlan(RankUpPlan.compensationActionId(cost.actionId()),
                        cost.definition().providerId(), "cost-compensation",
                        "Compensate " + cost.redactedPreview(), false, cost.characteristics().idempotent())));
        OperationPlan operationPlan = new OperationPlan(operationId, "rank-up", request.actor(), request.playerId(),
                request.playerState().stateRevision(), request.configRevision(), request.pinnedProviderGenerations(),
                request.idempotencyKey(), actions, "rank-up " + request.playerState().stageId().value() + " -> "
                        + request.targetStage().id().value());
        boolean executionAllowed = blockers.isEmpty();
        RankUpAuthorization authorization = executionAllowed
                ? new RankUpAuthorization(operationId, request.playerId(), request.playerState().stageId(),
                        request.targetStage().id(), request.playerState().stateRevision(),
                        request.playerState().configRevision(), request.configRevision(),
                        request.pinnedProviderGenerations(), evaluation, costs, rewards,
                        Optional.of(request.targetStage().projection()), projectionRequest, operationPlan)
                : RankUpAuthorization.denied();
        return new RankUpPlan(operationId, request.requestId(), request.playerId(), request.playerState().stageId(),
                request.targetStage().id(), request.playerState().stateRevision(),
                request.playerState().configRevision(), request.configRevision(), request.pinnedProviderGenerations(),
                evaluation, costs, rewards,
                Optional.of(request.targetStage().projection()), projectionRequest, unavailable, blockers,
                executionAllowed, operationPlan, authorization);
    }

    private Optional<RankProjectionRequest> projectionRequest(
            OperationId operationId,
            RankUpPlanningRequest request,
            List<String> blockers,
            Set<ProviderId> unavailable) {
        if (request.targetStage().projection().policy() == ProjectionPolicy.NONE) {
            return Optional.empty();
        }
        ProviderId providerId = request.targetStage().projection().providerId().orElse(null);
        var stages = request.canonicalStages().orElse(null);
        if (providerId == null || stages == null) {
            blockers.add("Canonical rank projection binding is incomplete");
            return Optional.empty();
        }
        Provider provider = usablePinned(providerId, request, blockers, unavailable, true).orElse(null);
        if (provider == null) {
            return Optional.empty();
        }
        if (!(provider instanceof RankAdapter)) {
            unavailable.add(providerId);
            blockers.add("Provider does not implement rank projection contract: " + providerId.value());
            return Optional.empty();
        }
        long generation = request.pinnedProviderGenerations().get(providerId);
        try {
            return Optional.of(new RankProjectionRequest(request.playerId(), operationId, request.configRevision(),
                    generation, stages.managedGroups(providerId), request.targetStage().projection().groupName()));
        } catch (IllegalArgumentException exception) {
            blockers.add("Canonical rank projection is invalid: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static void validateRequirementBinding(
            RankUpPlanningRequest request,
            List<String> blockers) {
        var bound = request.boundRequirements().orElse(null);
        if (bound == null) {
            blockers.add("Requirement result lacks canonical provenance");
            return;
        }
        var binding = bound.binding();
        if (!binding.playerId().equals(request.playerId())) {
            blockers.add("Requirement result belongs to another player");
        }
        if (!binding.configRevision().equals(request.configRevision())) {
            blockers.add("Requirement result belongs to another configuration revision");
        }
        if (!binding.providerGenerations().equals(request.pinnedProviderGenerations())) {
            blockers.add("Requirement result provider generations do not match the active snapshot");
        }
        if (!binding.treeId().equals(request.targetStage().requirementTreeId())) {
            blockers.add("Requirement result belongs to another requirement tree");
        }
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ProposedCost(CostProvider provider, PlannedCost plan) {
    }

    private record ProposedReward(RewardProvider provider, PlannedReward plan) {
    }
}
