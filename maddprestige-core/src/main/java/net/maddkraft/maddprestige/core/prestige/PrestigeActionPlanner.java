package net.maddkraft.maddprestige.core.prestige;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.PreflightStatus;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.CostPreflight;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

final class PrestigeActionPlanner {
    private final ProviderRegistry providers;

    PrestigeActionPlanner(ProviderRegistry providers) {
        this.providers = java.util.Objects.requireNonNull(providers, "provider registry");
    }

    CompletionStage<PrestigeActionPreflight> preflight(
            OperationId operationId,
            UUID playerId,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> pins,
            List<CostDefinition> costDefinitions,
            List<RewardDefinition> rewardDefinitions) {
        ArrayList<String> blockers = new ArrayList<>();
        LinkedHashSet<ProviderId> unavailable = new LinkedHashSet<>();
        List<CostProposal> costs = proposeCosts(operationId, playerId, configRevision, pins, costDefinitions,
                blockers, unavailable);
        List<RewardProposal> rewards = proposeRewards(
                operationId, playerId, configRevision, pins, rewardDefinitions, blockers, unavailable);
        List<CompletableFuture<CostPreflight>> costFutures = costFutures(costs);
        List<CompletableFuture<RewardPreflight>> rewardFutures = rewardFutures(rewards);
        CompletableFuture<?>[] all = java.util.stream.Stream.concat(costFutures.stream(), rewardFutures.stream())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all).handle((ignored, failure) -> assemble(costs, rewards, costFutures,
                rewardFutures, blockers, unavailable));
    }

    private List<CostProposal> proposeCosts(
            OperationId operationId,
            UUID playerId,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> pins,
            List<CostDefinition> definitions,
            List<String> blockers,
            LinkedHashSet<ProviderId> unavailable) {
        ArrayList<CostProposal> result = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            CostDefinition definition = definitions.get(index);
            Provider provider = pinned(definition.providerId(), pins, blockers, unavailable, true).orElse(null);
            if (!(provider instanceof CostProvider costProvider)) {
                if (provider != null) {
                    blockers.add("Provider lacks cost contract: " + definition.providerId().value());
                }
                continue;
            }
            try {
                if (costProvider.validate(definition).hasErrors()) {
                    blockers.add("Invalid Prestige cost: " + definition.id().value());
                    continue;
                }
                PlannedCost plan = new PlannedCost(operationId, "cost-" + index, playerId, definition, configRevision,
                        pins.get(definition.providerId()), costProvider.characteristics(definition),
                        "consume " + definition.displayName());
                result.add(new CostProposal(costProvider, plan));
            } catch (RuntimeException exception) {
                blockers.add("Cost provider validation failed: " + rootMessage(exception));
            }
        }
        return List.copyOf(result);
    }

    private List<RewardProposal> proposeRewards(
            OperationId operationId,
            UUID playerId,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> pins,
            List<RewardDefinition> definitions,
            List<String> blockers,
            LinkedHashSet<ProviderId> unavailable) {
        ArrayList<RewardProposal> result = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            RewardDefinition definition = definitions.get(index);
            boolean required = definition.failurePolicy() == RewardFailurePolicy.REQUIRED;
            Provider provider = pinned(definition.providerId(), pins, blockers, unavailable, required).orElse(null);
            if (!(provider instanceof RewardProvider rewardProvider)) {
                if (required) {
                    blockers.add("Required provider lacks reward contract: " + definition.providerId().value());
                }
                continue;
            }
            try {
                if (rewardProvider.validate(definition).hasErrors()) {
                    if (required) {
                        blockers.add("Invalid required Prestige reward: " + definition.id().value());
                    }
                    continue;
                }
                PlannedReward plan = new PlannedReward(operationId, "reward-" + index, playerId, definition,
                        configRevision,
                        pins.get(definition.providerId()), rewardProvider.characteristics(definition),
                        "apply " + definition.displayName());
                result.add(new RewardProposal(rewardProvider, plan));
            } catch (RuntimeException exception) {
                unavailable.add(definition.providerId());
                if (required) {
                    blockers.add("Required reward provider validation failed: " + rootMessage(exception));
                }
            }
        }
        return List.copyOf(result);
    }

    private Optional<Provider> pinned(
            ProviderId id,
            Map<ProviderId, Long> pins,
            List<String> blockers,
            LinkedHashSet<ProviderId> unavailable,
            boolean required) {
        Long generation = pins.get(id);
        var snapshot = providers.find(id);
        if (generation == null || snapshot.isEmpty() || snapshot.orElseThrow().generation() != generation
                || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                || !healthy(snapshot.orElseThrow().health().state())) {
            unavailable.add(id);
            if (required) {
                blockers.add("Provider unavailable or stale: " + id.value());
            }
            return Optional.empty();
        }
        return providers.provider(id);
    }

    private static List<CompletableFuture<CostPreflight>> costFutures(List<CostProposal> proposals) {
        LinkedHashMap<CostProvider, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < proposals.size(); index++) {
            grouped.computeIfAbsent(proposals.get(index).provider(), ignored -> new ArrayList<>()).add(index);
        }
        ArrayList<CompletableFuture<CostPreflight>> result = new ArrayList<>(
                java.util.Collections.nCopies(proposals.size(), null));
        grouped.forEach((provider, indexes) -> {
            List<PlannedCost> plans = indexes.stream().map(index -> proposals.get(index).plan()).toList();
            CompletableFuture<List<CostPreflight>> batch;
            try {
                batch = requiredStage(provider.preflightBatch(plans), "Cost provider returned null preflight");
            } catch (RuntimeException exception) {
                batch = CompletableFuture.failedFuture(exception);
            }
            for (int position = 0; position < indexes.size(); position++) {
                int resultIndex = indexes.get(position);
                int batchIndex = position;
                result.set(resultIndex, batch.thenApply(values -> exactSize(values, plans.size()).get(batchIndex)));
            }
        });
        return List.copyOf(result);
    }

    private static List<CompletableFuture<RewardPreflight>> rewardFutures(List<RewardProposal> proposals) {
        LinkedHashMap<RewardProvider, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < proposals.size(); index++) {
            grouped.computeIfAbsent(proposals.get(index).provider(), ignored -> new ArrayList<>()).add(index);
        }
        ArrayList<CompletableFuture<RewardPreflight>> result = new ArrayList<>(
                java.util.Collections.nCopies(proposals.size(), null));
        grouped.forEach((provider, indexes) -> {
            List<PlannedReward> plans = indexes.stream().map(index -> proposals.get(index).plan()).toList();
            CompletableFuture<List<RewardPreflight>> batch;
            try {
                batch = requiredStage(provider.preflightBatch(plans), "Reward provider returned null preflight");
            } catch (RuntimeException exception) {
                batch = CompletableFuture.failedFuture(exception);
            }
            for (int position = 0; position < indexes.size(); position++) {
                int resultIndex = indexes.get(position);
                int batchIndex = position;
                result.set(resultIndex, batch.thenApply(values -> exactSize(values, plans.size()).get(batchIndex)));
            }
        });
        return List.copyOf(result);
    }

    private static PrestigeActionPreflight assemble(
            List<CostProposal> costProposals,
            List<RewardProposal> rewardProposals,
            List<CompletableFuture<CostPreflight>> costFutures,
            List<CompletableFuture<RewardPreflight>> rewardFutures,
            List<String> initialBlockers,
            LinkedHashSet<ProviderId> unavailable) {
        ArrayList<String> blockers = new ArrayList<>(initialBlockers);
        ArrayList<PlannedCost> costs = new ArrayList<>();
        ArrayList<PlannedReward> rewards = new ArrayList<>();
        for (int index = 0; index < costFutures.size(); index++) {
            try {
                CostPreflight result = costFutures.get(index).join();
                if (result.status() != PreflightStatus.READY) {
                    blockers.add("Cost preflight blocked: " + result.detail());
                    if (result.status() == PreflightStatus.UNAVAILABLE) {
                        unavailable.add(costProposals.get(index).plan().definition().providerId());
                    }
                } else if (!result.plannedCost().orElseThrow().equals(costProposals.get(index).plan())) {
                    blockers.add("Cost provider altered immutable proposal");
                } else {
                    costs.add(result.plannedCost().orElseThrow());
                }
            } catch (RuntimeException exception) {
                blockers.add("Cost preflight failed: " + rootMessage(exception));
            }
        }
        for (int index = 0; index < rewardFutures.size(); index++) {
            RewardDefinition definition = rewardProposals.get(index).plan().definition();
            try {
                RewardPreflight result = rewardFutures.get(index).join();
                if (result.status() == PreflightStatus.READY
                        && result.plannedReward().orElseThrow().equals(rewardProposals.get(index).plan())) {
                    rewards.add(result.plannedReward().orElseThrow());
                } else if (definition.failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    blockers.add("Required reward preflight blocked: " + result.detail());
                }
            } catch (RuntimeException exception) {
                unavailable.add(definition.providerId());
                if (definition.failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    blockers.add("Required reward preflight failed: " + rootMessage(exception));
                }
            }
        }
        return new PrestigeActionPreflight(costs, rewards, blockers, unavailable);
    }

    private static <T> CompletableFuture<T> requiredStage(CompletionStage<T> stage, String message) {
        if (stage == null) {
            throw new IllegalStateException(message);
        }
        return stage.toCompletableFuture();
    }

    private static <T> List<T> exactSize(List<T> values, int expected) {
        List<T> copy = List.copyOf(values);
        if (copy.size() != expected) {
            throw new IllegalStateException("Provider batch preflight result count differs from proposals");
        }
        return copy;
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

    private record CostProposal(CostProvider provider, PlannedCost plan) {
    }

    private record RewardProposal(RewardProvider provider, PlannedReward plan) {
    }
}
