package net.maddkraft.maddprestige.core.plan;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationAuthorizer;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationContext;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluator;
import net.maddkraft.maddprestige.core.requirement.RequirementMetricCollector;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.requirement.RequirementStateReader;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageDefinition;

/** The only production path that converts rank-up intent into an executable authorization-sealed plan. */
public final class RankUpAuthorizationService {
    private final Supplier<Optional<ActiveStageConfiguration>> activeConfiguration;
    private final PlayerStageStateSource playerStates;
    private final RankUpProgressContextSource progressContexts;
    private final RequirementStateReader requirementStates;
    private final ProviderRegistry providers;
    private final Clock clock;
    private final RankUpPlanner planner;

    public RankUpAuthorizationService(
            Supplier<Optional<ActiveStageConfiguration>> activeConfiguration,
            PlayerStageStateSource playerStates,
            RankUpProgressContextSource progressContexts,
            RequirementStateReader requirementStates,
            ProviderRegistry providers,
            Clock clock) {
        this.activeConfiguration = Objects.requireNonNull(activeConfiguration, "active configuration");
        this.playerStates = Objects.requireNonNull(playerStates, "player states");
        this.progressContexts = Objects.requireNonNull(progressContexts, "progress contexts");
        this.requirementStates = Objects.requireNonNull(requirementStates, "requirement states");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.planner = new RankUpPlanner(providers);
    }

    public CompletionStage<RankUpAuthorizationResult> authorize(RankUpIntent intent) {
        Objects.requireNonNull(intent, "rank-up intent");
        Optional<ActiveStageConfiguration> active = activeConfiguration.get();
        if (active.isEmpty()) {
            return rejected(AuthorizationBlockerKind.NO_ACTIVE_STAGE_SNAPSHOT,
                    "No canonical Stage + Phase 3 snapshot is active", "operation", "rank_up");
        }
        ActiveStageConfiguration snapshot = active.orElseThrow();
        if (!bindingsMatch(snapshot.phaseThree().providerGenerations())) {
            String provider = snapshot.phaseThree().providerGenerations().keySet().stream()
                    .filter(id -> !bindingMatches(snapshot.phaseThree().providerGenerations(), id))
                    .map(ProviderId::value).sorted().findFirst().orElse("unknown");
            return rejected(AuthorizationBlockerKind.STALE_PROVIDER_BINDING,
                    "The active snapshot has a stale provider-generation binding", "operation", "rank_up",
                    "provider", provider);
        }
        Optional<PlayerStageState> loaded;
        try {
            loaded = playerStates.find(intent.playerId());
        } catch (RuntimeException exception) {
            return rejected(AuthorizationBlockerKind.PLAYER_STAGE_STATE_LOAD_FAILED,
                    "Authoritative player stage state could not be loaded: " + rootMessage(exception),
                    "player", intent.playerId(), "operation", "rank_up");
        }
        if (loaded.isEmpty()) {
            return rejected(AuthorizationBlockerKind.PLAYER_STAGE_STATE_MISSING,
                    "Authoritative player stage state does not exist", "player", intent.playerId());
        }
        PlayerStageState state = loaded.orElseThrow();
        var stages = snapshot.stages().configuration();
        if (!stages.active()) {
            return rejected(AuthorizationBlockerKind.STAGE_LADDER_INACTIVE,
                    "The canonical stage ladder is inactive", "operation", "rank_up");
        }
        StageDefinition source = stages.stages().get(state.stageId());
        if (source == null) {
            return rejected(AuthorizationBlockerKind.CURRENT_STAGE_UNKNOWN,
                    "Authoritative player stage is unknown in the current canonical ladder",
                    "current_stage", state.stageId().value());
        }
        if (!source.enabled()) {
            return rejected(AuthorizationBlockerKind.CURRENT_STAGE_DISABLED,
                    "Authoritative player stage is disabled in the current canonical ladder",
                    "current_stage", state.stageId().value());
        }
        int sourceIndex = stages.order().indexOf(state.stageId());
        if (sourceIndex < 0 || sourceIndex + 1 >= stages.order().size()) {
            return rejected(AuthorizationBlockerKind.NO_LEGAL_NEXT_STAGE,
                    "Player stage is not an ordered source with a legal next stage",
                    "current_stage", state.stageId().value());
        }
        var legalTargetId = stages.order().get(sourceIndex + 1);
        if (intent.intendedTarget().isPresent() && !intent.intendedTarget().orElseThrow().equals(legalTargetId)) {
            return rejected(AuthorizationBlockerKind.ILLEGAL_OR_STALE_TARGET,
                    "Intended target is an illegal stage skip or stale target",
                    "current_stage", state.stageId().value(), "intended_target",
                    intent.intendedTarget().orElseThrow().value(), "target_stage", legalTargetId.value());
        }
        StageDefinition target = stages.stages().get(legalTargetId);
        if (target == null || !target.enabled()) {
            return rejected(AuthorizationBlockerKind.TARGET_STAGE_UNKNOWN_OR_DISABLED,
                    "Canonical next stage is unknown or disabled", "target_stage", legalTargetId.value());
        }
        var phaseThree = snapshot.phaseThree().configuration();
        List<CostDefinition> costs = exactCosts(target, phaseThree.costs());
        if (costs.size() != target.costIds().size()) {
            String missing = target.costIds().stream().filter(id -> !phaseThree.costs().containsKey(id))
                    .map(value -> value.value()).sorted().collect(java.util.stream.Collectors.joining(","));
            return rejected(AuthorizationBlockerKind.UNKNOWN_CONFIGURED_COST,
                    "Canonical target references an unknown configured cost", "configured_cost", missing,
                    "target_stage", target.id().value());
        }
        List<RewardDefinition> rewards = exactRewards(target, phaseThree.rewards());
        if (rewards.size() != target.rewardIds().size()) {
            String missing = target.rewardIds().stream().filter(id -> !phaseThree.rewards().containsKey(id))
                    .map(value -> value.value()).sorted().collect(java.util.stream.Collectors.joining(","));
            return rejected(AuthorizationBlockerKind.UNKNOWN_CONFIGURED_REWARD,
                    "Canonical target references an unknown configured reward", "configured_reward", missing,
                    "target_stage", target.id().value());
        }
        Optional<RequirementNode> tree = target.requirementTreeId().map(phaseThree.trees()::get);
        if (target.requirementTreeId().isPresent() && tree.isEmpty()) {
            return rejected(AuthorizationBlockerKind.UNKNOWN_REQUIREMENT_TREE,
                    "Canonical target references an unknown requirement tree", "requirement",
                    target.requirementTreeId().orElseThrow().value(), "target_stage", target.id().value());
        }
        RankUpProgressContext progress;
        try {
            progress = Objects.requireNonNull(progressContexts.load(intent.playerId(), state, snapshot),
                    "trusted progress context");
        } catch (RuntimeException exception) {
            return rejected(AuthorizationBlockerKind.TRUSTED_CONTEXT_LOAD_FAILED,
                    "Trusted progression context could not be loaded: " + rootMessage(exception),
                    "player", intent.playerId(), "operation", "rank_up");
        }
        if (!progress.playerId().equals(intent.playerId())
                || !progress.activeConfigRevision().equals(snapshot.stages().revisionId())) {
            return rejected(AuthorizationBlockerKind.TRUSTED_CONTEXT_MISMATCH,
                    "Trusted progression context does not match the player and active configuration",
                    "player", intent.playerId(), "revision", snapshot.stages().revisionId().value());
        }
        Map<MetricBinding, MetricDescriptor> descriptors = descriptors(snapshot.phaseThree().providerGenerations());
        RequirementEvaluationContext context = new RequirementEvaluationContext(intent.playerId(),
                snapshot.stages().revisionId(), snapshot.phaseThree().providerGenerations(), progress.scalingIndex(),
                progress.catchUpPosition(), progress.scopes(), Map.of(), requirementStates);
        CompletionStage<Map<net.maddkraft.maddprestige.api.id.RequirementId,
                net.maddkraft.maddprestige.api.metric.MetricSample>> samples = tree.isPresent()
                ? new RequirementMetricCollector(providers, clock).collect(intent.playerId(), tree.orElseThrow(),
                        snapshot.phaseThree().providerGenerations())
                : CompletableFuture.completedFuture(Map.of());
        return samples.thenCompose(collected -> {
            RequirementEvaluationContext populated = new RequirementEvaluationContext(context.playerId(),
                    context.configRevision(), context.providerGenerations(), context.scalingIndex(),
                    context.catchUpPosition(), context.scopes(), collected, context.stateReader());
            RequirementEvaluationAuthorizer authorizer = new RequirementEvaluationAuthorizer(
                    new RequirementEvaluator(descriptors));
            var evaluation = tree.isPresent() ? authorizer.evaluate(tree.orElseThrow(), populated)
                    : authorizer.noRequirements(populated);
            RankUpPlanningRequest request = RankUpPlanningRequest.canonical(intent.actor(), intent.playerId(), state,
                    target, snapshot.stages().revisionId(), snapshot.phaseThree().providerGenerations(), evaluation,
                    costs, rewards, intent.idempotencyKey(), stages, intent.requestId());
            return planner.plan(request).thenApply(plan -> new RankUpAuthorizationResult(
                    Optional.of(plan), List.of(), List.of()));
        });
    }

    private boolean bindingsMatch(Map<ProviderId, Long> generations) {
        return generations.keySet().stream().allMatch(id -> bindingMatches(generations, id));
    }

    private boolean bindingMatches(Map<ProviderId, Long> generations, ProviderId id) {
        var snapshot = providers.find(id);
        return snapshot.isPresent() && providers.provider(id).isPresent()
                && snapshot.orElseThrow().generation() == generations.get(id)
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE;
    }

    private Map<MetricBinding, MetricDescriptor> descriptors(Map<ProviderId, Long> generations) {
        LinkedHashMap<MetricBinding, MetricDescriptor> result = new LinkedHashMap<>();
        for (ProviderId id : generations.keySet()) {
            providers.provider(id).filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast)
                    .ifPresent(provider -> {
                        try {
                            provider.metrics().forEach(metric -> result.put(
                                    new MetricBinding(metric.providerId(), metric.metricId()), metric));
                        } catch (RuntimeException ignored) {
                            // Missing descriptors make the affected canonical evaluation INVALID, never satisfied.
                        }
                    });
        }
        return Map.copyOf(result);
    }

    private static List<CostDefinition> exactCosts(
            StageDefinition target,
            Map<net.maddkraft.maddprestige.api.id.CostId, CostDefinition> definitions) {
        ArrayList<CostDefinition> result = new ArrayList<>();
        target.costIds().forEach(id -> Optional.ofNullable(definitions.get(id)).ifPresent(result::add));
        return List.copyOf(result);
    }

    private static List<RewardDefinition> exactRewards(
            StageDefinition target,
            Map<net.maddkraft.maddprestige.api.id.RewardId, RewardDefinition> definitions) {
        ArrayList<RewardDefinition> result = new ArrayList<>();
        target.rewardIds().forEach(id -> Optional.ofNullable(definitions.get(id)).ifPresent(result::add));
        return List.copyOf(result);
    }

    private static CompletionStage<RankUpAuthorizationResult> rejected(
            AuthorizationBlockerKind kind,
            String diagnostic,
            Object... facts) {
        return CompletableFuture.completedFuture(RankUpAuthorizationResult.rejected(
                AuthorizationBlocker.of(kind, diagnostic, facts)));
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
