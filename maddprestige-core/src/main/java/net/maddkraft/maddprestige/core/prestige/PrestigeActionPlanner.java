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
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
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
        ArrayList<AuthorizationBlocker> blockers = new ArrayList<>();
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
            List<AuthorizationBlocker> blockers,
            LinkedHashSet<ProviderId> unavailable) {
        ArrayList<CostProposal> result = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            CostDefinition definition = definitions.get(index);
            Provider provider = pinned(definition.providerId(), pins, blockers, unavailable, true).orElse(null);
            if (!(provider instanceof CostProvider costProvider)) {
                if (provider != null) {
                    blockers.add(b(AuthorizationBlockerKind.COST_PROVIDER_CONTRACT_MISSING,
                            "Provider lacks cost contract: " + definition.providerId().value(),
                            "id", definition.id().value(), "provider", definition.providerId().value()));
                }
                continue;
            }
            try {
                if (costProvider.validate(definition).hasErrors()) {
                    blockers.add(b(AuthorizationBlockerKind.COST_DEFINITION_INVALID,
                            "Invalid Prestige cost: " + definition.id().value(),
                            "id", definition.id().value(), "provider", definition.providerId().value()));
                    continue;
                }
                PlannedCost plan = new PlannedCost(operationId, "cost-" + index, playerId, definition, configRevision,
                        pins.get(definition.providerId()), costProvider.characteristics(definition),
                        "consume " + definition.displayName());
                result.add(new CostProposal(costProvider, plan));
            } catch (RuntimeException exception) {
                blockers.add(b(AuthorizationBlockerKind.COST_PROVIDER_VALIDATION_FAILED,
                        "Cost provider validation failed: " + rootMessage(exception),
                        "id", definition.id().value(), "provider", definition.providerId().value()));
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
            List<AuthorizationBlocker> blockers,
            LinkedHashSet<ProviderId> unavailable) {
        ArrayList<RewardProposal> result = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            RewardDefinition definition = definitions.get(index);
            boolean required = definition.failurePolicy() == RewardFailurePolicy.REQUIRED;
            Provider provider = pinned(definition.providerId(), pins, blockers, unavailable, required).orElse(null);
            if (!(provider instanceof RewardProvider rewardProvider)) {
                if (required) {
                    blockers.add(b(AuthorizationBlockerKind.REWARD_PROVIDER_CONTRACT_MISSING,
                            "Required provider lacks reward contract: " + definition.providerId().value(),
                            "id", definition.id().value(), "provider",
                            definition.providerId().value()));
                }
                continue;
            }
            try {
                if (rewardProvider.validate(definition).hasErrors()) {
                    if (required) {
                        blockers.add(b(AuthorizationBlockerKind.REWARD_DEFINITION_INVALID,
                                "Invalid required Prestige reward: " + definition.id().value(),
                                "id", definition.id().value(), "provider",
                                definition.providerId().value()));
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
                    blockers.add(b(AuthorizationBlockerKind.REWARD_PROVIDER_VALIDATION_FAILED,
                            "Required reward provider validation failed: " + rootMessage(exception),
                            "id", definition.id().value(), "provider",
                            definition.providerId().value()));
                }
            }
        }
        return List.copyOf(result);
    }

    private Optional<Provider> pinned(
            ProviderId id,
            Map<ProviderId, Long> pins,
            List<AuthorizationBlocker> blockers,
            LinkedHashSet<ProviderId> unavailable,
            boolean required) {
        Long generation = pins.get(id);
        var snapshot = providers.find(id);
        if (generation == null || snapshot.isEmpty() || snapshot.orElseThrow().generation() != generation
                || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                || !healthy(snapshot.orElseThrow().health().state())) {
            unavailable.add(id);
            if (required) {
                blockers.add(b(AuthorizationBlockerKind.REQUIRED_PROVIDER_UNAVAILABLE,
                        "Provider unavailable or stale: " + id.value(), "provider", id.value()));
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
            List<AuthorizationBlocker> initialBlockers,
            LinkedHashSet<ProviderId> unavailable) {
        ArrayList<AuthorizationBlocker> blockers = new ArrayList<>(initialBlockers);
        ArrayList<PlannedCost> costs = new ArrayList<>();
        ArrayList<PlannedReward> rewards = new ArrayList<>();
        for (int index = 0; index < costFutures.size(); index++) {
            try {
                CostPreflight result = costFutures.get(index).join();
                if (result.status() != PreflightStatus.READY) {
                    PlannedCost proposal = costProposals.get(index).plan();
                    blockers.add(b(AuthorizationBlockerKind.COST_PREFLIGHT_BLOCKED,
                            "Cost preflight blocked: " + result.detail(),
                            "id", proposal.definition().id().value(), "provider",
                            proposal.definition().providerId().value(), "amount",
                            proposal.definition().amount().canonical(),
                            "type", proposal.definition().type(), "status", result.status(),
                            "detail", result.detail()));
                    if (result.status() == PreflightStatus.UNAVAILABLE) {
                        unavailable.add(costProposals.get(index).plan().definition().providerId());
                    }
                } else if (!result.plannedCost().orElseThrow().equals(costProposals.get(index).plan())) {
                    PlannedCost proposal = costProposals.get(index).plan();
                    blockers.add(b(AuthorizationBlockerKind.COST_PLAN_INTEGRITY_VIOLATION,
                            "Cost provider altered immutable proposal", "id",
                            proposal.definition().id().value(), "provider",
                            proposal.definition().providerId().value()));
                } else {
                    costs.add(result.plannedCost().orElseThrow());
                }
            } catch (RuntimeException exception) {
                PlannedCost proposal = costProposals.get(index).plan();
                blockers.add(b(AuthorizationBlockerKind.COST_PREFLIGHT_FAILED,
                        "Cost preflight failed: " + rootMessage(exception), "id",
                        proposal.definition().id().value(), "provider", proposal.definition().providerId().value()));
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
                    AuthorizationBlockerKind kind = result.status() == PreflightStatus.READY
                            ? AuthorizationBlockerKind.REWARD_PLAN_INTEGRITY_VIOLATION
                            : AuthorizationBlockerKind.REWARD_PREFLIGHT_BLOCKED;
                    String diagnostic = result.status() == PreflightStatus.READY
                            ? "Reward provider altered immutable proposal"
                            : "Required reward preflight blocked: " + result.detail();
                    blockers.add(b(kind, diagnostic, "id", definition.id().value(), "provider",
                            definition.providerId().value(), "amount", definition.value().canonical(), "type",
                            definition.type(),
                            "status", result.status(), "detail", result.detail()));
                }
            } catch (RuntimeException exception) {
                unavailable.add(definition.providerId());
                if (definition.failurePolicy() == RewardFailurePolicy.REQUIRED) {
                    blockers.add(b(AuthorizationBlockerKind.REWARD_PREFLIGHT_FAILED,
                            "Required reward preflight failed: " + rootMessage(exception), "id",
                            definition.id().value(), "provider", definition.providerId().value()));
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

    private static AuthorizationBlocker b(
            AuthorizationBlockerKind kind,
            String diagnostic,
            Object... facts) {
        return AuthorizationBlocker.of(kind, diagnostic, facts);
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
