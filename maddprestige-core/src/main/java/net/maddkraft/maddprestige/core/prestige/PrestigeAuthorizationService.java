package net.maddkraft.maddprestige.core.prestige;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;
import net.maddkraft.maddprestige.api.operation.OperationActionPlan;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.core.config.lifecycle.ActiveLifecycleConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.PrestigeConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleRequirementReachability;
import net.maddkraft.maddprestige.core.config.lifecycle.ResetComponent;
import net.maddkraft.maddprestige.core.config.lifecycle.ResetDisposition;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind;
import net.maddkraft.maddprestige.core.currency.CurrencyBalanceSource;
import net.maddkraft.maddprestige.core.milestone.MilestoneDefinition;
import net.maddkraft.maddprestige.core.milestone.MilestoneRepeatability;
import net.maddkraft.maddprestige.core.milestone.MilestoneStateReader;
import net.maddkraft.maddprestige.core.milestone.MilestoneTriggerType;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationAuthorizer;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationContext;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluator;
import net.maddkraft.maddprestige.core.requirement.RequirementMetricCollector;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementStateReader;
import net.maddkraft.maddprestige.core.season.ActiveSeasonContext;
import net.maddkraft.maddprestige.core.season.ActiveSeasonSource;

/** Sole production path from consequence-free Prestige intent to an authorization-sealed confirmation plan. */
public final class PrestigeAuthorizationService {
    private static final ProviderId INTERNAL_PROVIDER = new ProviderId("maddprestige.internal");
    private static final net.maddkraft.maddprestige.api.id.StageId NUMERIC_COMPATIBILITY_MARKER =
            new net.maddkraft.maddprestige.api.id.StageId("numeric-level");
    private final Supplier<Optional<ActiveLifecycleConfiguration>> activeConfiguration;
    private final PlayerPrestigeStateSource prestigeStates;
    private final PrestigeProgressContextSource progressContexts;
    private final RequirementStateReader requirementStates;
    private final ProviderRegistry providers;
    private final CurrencyBalanceSource balances;
    private final MilestoneStateReader milestoneStates;
    private final ActiveSeasonSource seasons;
    private final Clock clock;
    private final PrestigeActionPlanner actions;

    public PrestigeAuthorizationService(
            Supplier<Optional<ActiveLifecycleConfiguration>> activeConfiguration,
            PlayerPrestigeStateSource prestigeStates,
            PrestigeProgressContextSource progressContexts,
            RequirementStateReader requirementStates,
            ProviderRegistry providers,
            CurrencyBalanceSource balances,
            MilestoneStateReader milestoneStates,
            ActiveSeasonSource seasons,
            Clock clock) {
        this.activeConfiguration = Objects.requireNonNull(activeConfiguration, "active configuration");
        this.prestigeStates = Objects.requireNonNull(prestigeStates, "Prestige states");
        this.progressContexts = Objects.requireNonNull(progressContexts, "progress contexts");
        this.requirementStates = Objects.requireNonNull(requirementStates, "requirement states");
        this.providers = Objects.requireNonNull(providers, "provider registry");
        this.balances = Objects.requireNonNull(balances, "currency balances");
        this.milestoneStates = Objects.requireNonNull(milestoneStates, "milestone states");
        this.seasons = Objects.requireNonNull(seasons, "season source");
        this.clock = Objects.requireNonNull(clock, "clock");
        actions = new PrestigeActionPlanner(providers);
    }

    /** Compatibility constructor; the legacy stage source is deliberately not consulted by numeric Prestige. */
    @Deprecated
    public PrestigeAuthorizationService(
            Supplier<Optional<ActiveLifecycleConfiguration>> activeConfiguration,
            net.maddkraft.maddprestige.core.plan.PlayerStageStateSource ignoredStageStates,
            PlayerPrestigeStateSource prestigeStates,
            PrestigeProgressContextSource progressContexts,
            RequirementStateReader requirementStates,
            ProviderRegistry providers,
            CurrencyBalanceSource balances,
            MilestoneStateReader milestoneStates,
            ActiveSeasonSource seasons,
            Clock clock) {
        this(activeConfiguration, prestigeStates, progressContexts, requirementStates, providers, balances,
                milestoneStates, seasons, clock);
        Objects.requireNonNull(ignoredStageStates, "legacy stage states");
    }

    public CompletionStage<PrestigeAuthorizationResult> authorize(PrestigeIntent intent) {
        Objects.requireNonNull(intent, "Prestige intent");
        Optional<ActiveLifecycleConfiguration> loadedConfig = activeConfiguration.get();
        if (loadedConfig.isEmpty()) {
            return rejected(AuthorizationBlockerKind.NO_ACTIVE_PRESTIGE_CONFIGURATION,
                    "No canonical Prestige configuration is active");
        }
        ActiveLifecycleConfiguration active = loadedConfig.orElseThrow();
        if (!retainsPriorBindings(active.lifecycle().providerGenerations(),
                active.prerequisites().progression().providerGenerations())) {
            String provider = firstBindingMismatch(active.lifecycle().providerGenerations(),
                    active.prerequisites().progression().providerGenerations());
            return rejected(AuthorizationBlockerKind.STALE_PROVIDER_BINDING,
                    "Active provider-generation bindings are stale or inconsistent",
                    "provider", provider);
        }
        PlayerPrestigeState prestigeState;
        try {
            prestigeState = prestigeStates.find(intent.playerId()).orElseThrow();
        } catch (RuntimeException exception) {
            return rejected(AuthorizationBlockerKind.PLAYER_PRESTIGE_STATE_UNAVAILABLE,
                    "Authoritative player Prestige state is unavailable: " + rootMessage(exception),
                    "player", intent.playerId());
        }
        PrestigeConfiguration configuration = active.lifecycle().configuration().prestige();
        if (!configuration.enabled()) {
            return rejected(AuthorizationBlockerKind.PRESTIGE_DISABLED,
                    "Prestige is disabled");
        }
        if (prestigeState.currentPrestige() != prestigeState.lifetimePrestige()) {
            return rejected(AuthorizationBlockerKind.PLAYER_PRESTIGE_STATE_UNAVAILABLE,
                    "Compatibility Prestige counters diverged; numeric Prestige requires one authoritative value",
                    "current_prestige", prestigeState.currentPrestige(),
                    "lifetime_prestige", prestigeState.lifetimePrestige());
        }
        Instant now = clock.instant();
        if (!canIncrement(prestigeState.currentPrestige(), 1)) {
            return rejected(AuthorizationBlockerKind.PRESTIGE_COUNTER_OVERFLOW,
                    "Current Prestige count would overflow the authoritative counter", "counter", "current",
                    "current_prestige", prestigeState.currentPrestige());
        }
        if (!configuration.limit().allows(prestigeState.currentPrestige(), 1)) {
            return rejected(AuthorizationBlockerKind.PRESTIGE_MAXIMUM_REACHED,
                    "Finite Prestige maximum has been reached", "current_prestige",
                    prestigeState.currentPrestige(), "prestige_maximum",
                    configuration.limit().maximum().isPresent()
                            ? configuration.limit().maximum().getAsLong() : "unbounded");
        }
        if (!canIncrement(prestigeState.lifetimePrestige(), 1)) {
            return rejected(AuthorizationBlockerKind.PRESTIGE_COUNTER_OVERFLOW,
                    "Lifetime Prestige count would overflow the authoritative counter", "counter", "lifetime",
                    "current_prestige", prestigeState.lifetimePrestige());
        }
        if (prestigeState.stateRevision() == Long.MAX_VALUE) {
            return rejected(AuthorizationBlockerKind.PRESTIGE_REVISION_OVERFLOW,
                    "Player Prestige state revision cannot be incremented", "revision",
                    prestigeState.stateRevision());
        }
        if (prestigeState.lastPrestigedAt().map(last -> now.isBefore(last.plus(configuration.cooldown())))
                .orElse(false)) {
            Instant eligibleAt = prestigeState.lastPrestigedAt().orElseThrow().plus(configuration.cooldown());
            return rejected(AuthorizationBlockerKind.PRESTIGE_COOLDOWN_ACTIVE,
                    "Prestige cooldown has not elapsed", "cooldown_remaining",
                    Duration.between(now, eligibleAt), "eligible_at", eligibleAt);
        }
        PrestigeProgressContext progress;
        try {
            progress = Objects.requireNonNull(progressContexts.load(intent.playerId(), prestigeState,
                    active), "trusted progress context");
        } catch (RuntimeException exception) {
            return rejected(AuthorizationBlockerKind.TRUSTED_CONTEXT_LOAD_FAILED,
                    "Trusted Prestige context is unavailable: " + rootMessage(exception),
                    "player", intent.playerId());
        }
        if (!progress.playerId().equals(intent.playerId())
                || !progress.activeConfigRevision().equals(active.lifecycle().revisionId())) {
            return rejected(AuthorizationBlockerKind.TRUSTED_CONTEXT_MISMATCH,
                    "Trusted Prestige context does not match active player/revision", "player", intent.playerId(),
                    "revision", active.lifecycle().revisionId().value());
        }
        var progression = active.prerequisites().progression().configuration();
        Optional<RequirementNode> tree = configuration.requirementTreeId().map(progression.trees()::get);
        if (configuration.requirementTreeId().isPresent() && tree.isEmpty()) {
            return rejected(AuthorizationBlockerKind.UNKNOWN_REQUIREMENT_TREE,
                    "Prestige references an unknown requirement tree", "requirement",
                    configuration.requirementTreeId().orElseThrow().value());
        }
        long targetLevel = Math.addExact(prestigeState.currentPrestige(), 1);
        List<CostDefinition> costs = configuration.costIds().stream().map(progression.costs()::get)
                .filter(Objects::nonNull)
                .map(definition -> active.lifecycle().configuration().valueScaling().scale(definition, targetLevel))
                .toList();
        if (costs.size() != configuration.costIds().size()) {
            String missing = configuration.costIds().stream().filter(id -> !progression.costs().containsKey(id))
                    .findFirst().orElseThrow().value();
            return rejected(AuthorizationBlockerKind.UNKNOWN_CONFIGURED_COST,
                    "Prestige references an unknown cost", "configured_cost", missing);
        }
        ActiveSeasonContext season = seasons.active();
        List<MilestoneConsequence> milestoneConsequences = milestones(active, prestigeState, season);
        List<RewardDefinition> rewards = rewardDefinitions(configuration, milestoneConsequences, progression.rewards())
                .stream().map(definition -> active.lifecycle().configuration().valueScaling()
                        .scale(definition, targetLevel)).toList();
        if (rewards.size() != configuration.rewardIds().size()
                + milestoneConsequences.stream().mapToInt(value -> value.rewardIds().size()).sum()) {
            String missing = java.util.stream.Stream.concat(configuration.rewardIds().stream(),
                    milestoneConsequences.stream().flatMap(value -> value.rewardIds().stream()))
                    .filter(id -> !progression.rewards().containsKey(id)).findFirst().orElseThrow().value();
            return rejected(AuthorizationBlockerKind.UNKNOWN_CONFIGURED_REWARD,
                    "Prestige or a triggered milestone references an unknown reward", "configured_reward", missing);
        }
        Set<RequirementDefinition> prestigeDefinitions = LifecycleRequirementReachability
                .prestigeDefinitions(active.lifecycle().configuration(), progression);
        Set<RequirementDefinition> boundaryDefinitions = LifecycleRequirementReachability
                .postPrestigeDefinitions(active.lifecycle().configuration(), progression,
                        active.prerequisites().stages().configuration()).stream()
                .filter(definition -> definition.scope()
                        == net.maddkraft.maddprestige.core.requirement.MeasurementScope.SINCE_PRESTIGE_START)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashSet<ProviderId> requiredProviders = new LinkedHashSet<>();
        prestigeDefinitions.forEach(definition -> requiredProviders.add(definition.providerId()));
        if (resetsScopedRequirementState(configuration)) {
            boundaryDefinitions.forEach(definition -> requiredProviders.add(definition.providerId()));
        }
        costs.forEach(cost -> requiredProviders.add(cost.providerId()));
        rewards.stream().filter(reward -> reward.failurePolicy() == RewardFailurePolicy.REQUIRED)
                .forEach(reward -> requiredProviders.add(reward.providerId()));
        Map<ProviderId, Long> configuredPins = active.lifecycle().providerGenerations();
        if (!bindingsMatch(configuredPins, requiredProviders)) {
            List<AuthorizationBlocker> missing = requiredProviders.stream()
                    .filter(id -> !bindingMatches(configuredPins, id))
                    .map(id -> b(AuthorizationBlockerKind.REQUIRED_PROVIDER_UNAVAILABLE,
                            "A provider required by this Prestige operation is absent, inactive, unhealthy, or stale",
                            "provider", id.value()))
                    .toList();
            return rejected(missing);
        }
        LinkedHashSet<ProviderId> consumedProviders = new LinkedHashSet<>(requiredProviders);
        rewards.stream().filter(reward -> reward.failurePolicy() != RewardFailurePolicy.REQUIRED)
                .map(RewardDefinition::providerId)
                .filter(id -> bindingMatches(configuredPins, id)).forEach(consumedProviders::add);
        Map<ProviderId, Long> pins = pinsFor(configuredPins, consumedProviders);
        Map<MetricBinding, MetricDescriptor> descriptors = descriptors(pins);
        RequirementEvaluationContext context = new RequirementEvaluationContext(intent.playerId(),
                active.lifecycle().revisionId(), pins, progress.scalingIndex(), progress.catchUpPosition(),
                progress.scopes(), Map.of(), requirementStates);
        CompletionStage<Map<net.maddkraft.maddprestige.api.id.RequirementId,
                net.maddkraft.maddprestige.api.metric.MetricSample>> samples = tree.isPresent()
                ? new RequirementMetricCollector(providers, clock).collect(intent.playerId(), tree.orElseThrow(), pins)
                : CompletableFuture.completedFuture(Map.of());
        return samples.thenCompose(collected -> {
            RequirementEvaluationContext populated = new RequirementEvaluationContext(context.playerId(),
                    context.configRevision(), context.providerGenerations(), context.scalingIndex(),
                    context.catchUpPosition(), context.scopes(), collected, context.stateReader());
            RequirementEvaluationAuthorizer authorizer = new RequirementEvaluationAuthorizer(
                    new RequirementEvaluator(descriptors));
            var evaluation = tree.isPresent() ? authorizer.evaluate(tree.orElseThrow(), populated)
                    : authorizer.noRequirements(populated);
            OperationId operationId = distinctOperationId(intent.requestId());
            CompletionStage<PrestigeBoundaryCollection> boundaryStage = resetsScopedRequirementState(configuration)
                    ? collectBoundaryBaselines(intent.playerId(), boundaryDefinitions, pins)
                    : CompletableFuture.completedFuture(new PrestigeBoundaryCollection(List.of(), List.of()));
            return boundaryStage
                    .thenCompose(boundary -> actions.preflight(operationId, intent.playerId(),
                            active.lifecycle().revisionId(), pins, costs, rewards)
                            .thenApply(preflight -> assemble(intent, active, prestigeState,
                                    evaluation, boundary, milestoneConsequences, season, operationId, pins, preflight,
                                    now)));
        });
    }

    private static OperationId distinctOperationId(java.util.UUID requestId) {
        OperationId candidate;
        do {
            candidate = OperationId.random();
        } while (candidate.value().equals(requestId));
        return candidate;
    }

    private PrestigeAuthorizationResult assemble(
            PrestigeIntent intent,
            ActiveLifecycleConfiguration active,
            PlayerPrestigeState prestigeState,
            net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation evaluation,
            PrestigeBoundaryCollection boundary,
            List<MilestoneConsequence> milestones,
            ActiveSeasonContext season,
            OperationId operationId,
            Map<ProviderId, Long> operationPins,
            PrestigeActionPreflight preflight,
            Instant now) {
        PrestigeConfiguration configuration = active.lifecycle().configuration().prestige();
        ArrayList<AuthorizationBlocker> blockers = new ArrayList<>(preflight.blockers());
        blockers.addAll(boundary.blockers());
        if (!evaluation.result().satisfied()) {
            blockers.add(b(AuthorizationBlockerKind.REQUIREMENT_UNSATISFIED,
                    "Prestige requirement tree is " + evaluation.result().status(), "requirement",
                    configuration.requirementTreeId().map(value -> value.value()).orElse("prestige"),
                    "status", evaluation.result().status()));
        }
        Optional<RankProjectionRequest> projection = Optional.empty();
        Optional<ProviderId> rankProviderId = Optional.empty();
        boolean resetScopedState = resetsScopedRequirementState(configuration);
        ScopeId nextScope = resetScopedState ? new ScopeId("prestige-" + operationId)
                : prestigeState.prestigeScope();
        List<CurrencyConsequence> currencies = currencyConsequences(intent.playerId(), active);
        List<ComponentConsequence> components = componentConsequences(configuration);
        List<ProviderActionConsequence> providerActions = providerActions(preflight, projection, rankProviderId);
        List<String> uncertain = providerActions.stream().filter(ProviderActionConsequence::uncertaintyPossible)
                .map(ProviderActionConsequence::description).toList();
        PrestigeSimulation simulation = new PrestigeSimulation(intent.playerId(), NUMERIC_COMPATIBILITY_MARKER,
                NUMERIC_COMPATIBILITY_MARKER, prestigeState.currentPrestige(), Math.addExact(prestigeState.currentPrestige(),
                        1), prestigeState.lifetimePrestige(),
                Math.addExact(prestigeState.lifetimePrestige(), 1), evaluation,
                preflight.costs(), preflight.rewards(), currencies, components, prestigeState.prestigeScope(),
                nextScope, boundary.baselines(),
                new ScopedRequirementStateConsequence(
                        configuration.resetPolicy().disposition(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS),
                        configuration.resetPolicy().disposition(ResetComponent.BASELINES),
                        configuration.resetPolicy().disposition(ResetComponent.LATCHED_COMPLETIONS),
                        resetScopedState, !resetScopedState, boundary.baselines().size()), milestones,
                new SeasonConsequence(season.seasonId(), season.scopeId(),
                        "PRESERVE: player season progress and the external season scope are unchanged"),
                providerActions, uncertain, prestigeState.configRevision(), prestigeState.configRevision(),
                active.lifecycle().revisionId(), 0, prestigeState.stateRevision(),
                operationPins, now);
        List<OperationActionPlan> actionPlans = operationActions(preflight, projection, rankProviderId, currencies,
                milestones, prestigeState.currentPrestige(), simulation.currentPrestigeAfter());
        OperationPlan operationPlan = new OperationPlan(operationId, "prestige", intent.actor(), intent.playerId(),
                prestigeState.stateRevision(), active.lifecycle().revisionId(),
                operationPins, intent.idempotencyKey(), actionPlans,
                "prestige " + prestigeState.currentPrestige() + " -> " + simulation.currentPrestigeAfter());
        List<String> blockerDiagnostics = AuthorizationBlocker.diagnostics(blockers);
        boolean allowed = blockers.isEmpty();
        PrestigeAuthorization authority = allowed ? new PrestigeAuthorization(operationId, intent.playerId(),
                0, prestigeState.stateRevision(), active.lifecycle().revisionId(),
                operationPins, simulation, preflight.costs(), preflight.rewards(),
                rankProviderId, projection, operationPlan) : PrestigeAuthorization.denied();
        PrestigePlan plan = new PrestigePlan(operationId, intent.requestId(), intent.playerId(),
                0,
                prestigeState.stateRevision(), active.lifecycle().revisionId(),
                operationPins, simulation, preflight.costs(), preflight.rewards(),
                rankProviderId, projection, preflight.unavailableProviders(), blockerDiagnostics, allowed,
                operationPlan, authority, blockers);
        return PrestigeAuthorizationResult.planned(plan);
    }

    private List<CurrencyConsequence> currencyConsequences(
            java.util.UUID playerId,
            ActiveLifecycleConfiguration active) {
        if (active.lifecycle().configuration().prestige().resetPolicy()
                .disposition(ResetComponent.PRESTIGE_SCOPED_CURRENCY) != ResetDisposition.RESET) {
            return List.of();
        }
        return active.lifecycle().configuration().currencies().values().stream()
                .filter(net.maddkraft.maddprestige.core.currency.CurrencyDefinition::prestigeScoped)
                .sorted(java.util.Comparator.comparing(value -> value.id().value()))
                .map(definition -> {
                    var before = balances.balance(playerId, definition.id());
                    return new CurrencyConsequence(definition.id(), before, before.negate(),
                            net.maddkraft.maddprestige.api.value.ExactDecimal.ZERO, "Prestige-scope reset");
                }).toList();
    }

    private static List<ComponentConsequence> componentConsequences(PrestigeConfiguration configuration) {
        return java.util.Arrays.stream(ResetComponent.values()).map(component -> new ComponentConsequence(component,
                configuration.resetPolicy().disposition(component), switch (component) {
                    case PROGRESSION_STAGE -> "legacy compatibility component; numeric Prestige changes no stage";
                    case ACTIVE_REQUIREMENT_PROGRESS -> configuration.resetPolicy().disposition(component)
                            == ResetDisposition.RESET
                                    ? "establish a new Prestige scope; prior progress remains historical/inaccessible"
                                    : "retain the existing Prestige scope and all scoped progress";
                    case LATCHED_COMPLETIONS -> configuration.resetPolicy().disposition(component)
                            == ResetDisposition.RESET
                                    ? "retain prior latch evidence but use the new scope for eligibility"
                                    : "retain the existing scope so completed latches remain effective";
                    case BASELINES -> configuration.resetPolicy().disposition(component) == ResetDisposition.RESET
                            ? "sample reachable SINCE_PRESTIGE_START metrics into the new scope"
                            : "retain and continue using existing scope baselines";
                    case PRESTIGE_SCOPED_CURRENCY -> configuration.resetPolicy().disposition(component)
                            == ResetDisposition.RESET ? "atomically set each Prestige-scoped balance to exact zero"
                                    : "leave every Prestige-scoped balance unchanged";
                    case PURCHASED_PERKS -> "PRESERVE only: no purchased-perk store exists";
                    case MILESTONE_HISTORY -> "PRESERVE only: award evidence and repeatability remain unchanged";
                    case SEASON_PROGRESS -> "PRESERVE only: player season state is not owned by Prestige";
                    case HISTORICAL_STATISTICS -> "PRESERVE only: lifetime/history evidence is append-only";
                })).toList();
    }

    private List<MilestoneConsequence> milestones(
            ActiveLifecycleConfiguration active,
            PlayerPrestigeState state,
            ActiveSeasonContext season) {
        long currentAfter = Math.addExact(state.currentPrestige(), 1);
        long lifetimeAfter = Math.addExact(state.lifetimePrestige(), 1);
        ArrayList<MilestoneConsequence> result = new ArrayList<>();
        active.lifecycle().configuration().milestones().values().stream()
                .filter(MilestoneDefinition::enabled)
                .sorted(java.util.Comparator.comparing(value -> value.id().value()))
                .forEach(milestone -> {
                    boolean triggered = switch (milestone.triggerType()) {
                        case CURRENT_PRESTIGE -> reached(currentAfter, milestone.threshold().asNumber());
                        case LIFETIME_PRESTIGE -> reached(lifetimeAfter, milestone.threshold().asNumber());
                        case STAGE_REACHED, SEASON_PROGRESS, PROVIDER_METRIC -> false;
                    };
                    Optional<String> key = repeatabilityKey(milestone.repeatability(), currentAfter, season);
                    if (triggered && key.isPresent() && !milestoneStates.awarded(state.playerId(), milestone.id(),
                            key.orElseThrow())) {
                        result.add(new MilestoneConsequence(milestone.id(), key.orElseThrow(),
                                milestone.rewardIds()));
                    }
                });
        return List.copyOf(result);
    }

    private static Optional<String> repeatabilityKey(
            MilestoneRepeatability repeatability,
            long currentAfter,
            ActiveSeasonContext season) {
        return switch (repeatability) {
            case ONCE -> Optional.of("once");
            case ONCE_PER_PRESTIGE -> Optional.of("prestige-" + currentAfter);
            case ONCE_PER_SEASON -> season.seasonId().map(value -> "season-" + value.value());
        };
    }

    private static boolean reached(long value, BigDecimal threshold) {
        return BigDecimal.valueOf(value).compareTo(threshold) >= 0;
    }

    private static List<RewardDefinition> rewardDefinitions(
            PrestigeConfiguration configuration,
            List<MilestoneConsequence> milestones,
            Map<net.maddkraft.maddprestige.api.id.RewardId, RewardDefinition> definitions) {
        ArrayList<RewardDefinition> result = new ArrayList<>();
        configuration.rewardIds().forEach(id -> Optional.ofNullable(definitions.get(id)).ifPresent(result::add));
        milestones.forEach(milestone -> milestone.rewardIds().forEach(id ->
                Optional.ofNullable(definitions.get(id)).ifPresent(result::add)));
        return List.copyOf(result);
    }

    private static List<ProviderActionConsequence> providerActions(
            PrestigeActionPreflight preflight,
            Optional<RankProjectionRequest> projection,
            Optional<ProviderId> rankProviderId) {
        ArrayList<ProviderActionConsequence> result = new ArrayList<>();
        preflight.costs().forEach(cost -> result.add(new ProviderActionConsequence(cost.actionId(),
                cost.definition().providerId(), cost.redactedPreview(), true, cost.characteristics().idempotent(),
                cost.characteristics().externalUncertaintyPossible())));
        projection.ifPresent(value -> result.add(new ProviderActionConsequence("rank-projection",
                rankProviderId.orElseThrow(), "project managed progression membership", true, true, true)));
        preflight.rewards().forEach(reward -> result.add(new ProviderActionConsequence(reward.actionId(),
                reward.definition().providerId(), reward.redactedPreview(), true,
                reward.characteristics().idempotent(), reward.characteristics().externalUncertaintyPossible())));
        return List.copyOf(result);
    }

    private static List<OperationActionPlan> operationActions(
            PrestigeActionPreflight preflight,
            Optional<RankProjectionRequest> projection,
            Optional<ProviderId> rankProviderId,
            List<CurrencyConsequence> currencies,
            List<MilestoneConsequence> milestones,
            long sourceLevel,
            long targetLevel) {
        ArrayList<OperationActionPlan> result = new ArrayList<>();
        preflight.costs().forEach(cost -> result.add(new OperationActionPlan(cost.actionId(),
                cost.definition().providerId(), "cost", cost.redactedPreview(),
                cost.characteristics().reversible(), cost.characteristics().idempotent())));
        projection.ifPresent(value -> result.add(new OperationActionPlan("rank-projection",
                rankProviderId.orElseThrow(),
                "managed-direct-membership", "project configured reset-stage membership", false, true)));
        result.add(new OperationActionPlan("prestige-state-commit", INTERNAL_PROVIDER, "prestige-state",
                sourceLevel + " -> " + targetLevel, false, true));
        currencies.forEach(currency -> result.add(new OperationActionPlan("currency-reset-"
                + currency.currencyId().value(), INTERNAL_PROVIDER, "currency-reset", currency.reason(), false, true)));
        milestones.forEach(milestone -> result.add(new OperationActionPlan("milestone-"
                + milestone.milestoneId().value(), INTERNAL_PROVIDER, "milestone-record",
                "record milestone " + milestone.milestoneId().value(), false, true)));
        preflight.rewards().forEach(reward -> result.add(new OperationActionPlan(reward.actionId(),
                reward.definition().providerId(), "reward", reward.redactedPreview(),
                reward.characteristics().reversible(), reward.characteristics().idempotent())));
        preflight.costs().stream().filter(cost -> cost.characteristics().reversible()).forEach(cost -> result.add(
                new OperationActionPlan("compensate-" + cost.actionId(), cost.definition().providerId(),
                        "cost-compensation", "compensate " + cost.redactedPreview(), false,
                        cost.characteristics().idempotent())));
        return List.copyOf(result);
    }

    private boolean bindingsMatch(Map<ProviderId, Long> generations, Set<ProviderId> required) {
        return required.stream().allMatch(id -> bindingMatches(generations, id));
    }

    private boolean bindingMatches(Map<ProviderId, Long> generations, ProviderId id) {
        Long generation = generations.get(id);
        var snapshot = providers.find(id);
        return generation != null && snapshot.isPresent() && providers.provider(id).isPresent()
                && snapshot.orElseThrow().generation() == generation
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state());
    }

    private static Map<ProviderId, Long> pinsFor(
            Map<ProviderId, Long> configured,
            Set<ProviderId> consumed) {
        LinkedHashMap<ProviderId, Long> result = new LinkedHashMap<>();
        consumed.forEach(id -> Optional.ofNullable(configured.get(id)).ifPresent(value -> result.put(id, value)));
        return Map.copyOf(result);
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static boolean canIncrement(long value, long increment) {
        return value >= 0 && increment > 0 && value <= Long.MAX_VALUE - increment;
    }

    private static boolean resetsScopedRequirementState(PrestigeConfiguration configuration) {
        return configuration.resetPolicy().disposition(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS)
                == ResetDisposition.RESET;
    }

    private static boolean retainsPriorBindings(
            Map<ProviderId, Long> lifecycle,
            Map<ProviderId, Long> prior) {
        return prior.entrySet().stream().allMatch(entry -> entry.getValue().equals(lifecycle.get(entry.getKey())));
    }

    private static String firstBindingMismatch(
            Map<ProviderId, Long> lifecycle,
            Map<ProviderId, Long> prior) {
        return prior.entrySet().stream().filter(entry -> !entry.getValue().equals(lifecycle.get(entry.getKey())))
                .map(entry -> entry.getKey().value()).sorted().findFirst().orElse("unknown");
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
                            // Missing descriptors make the canonical requirement unavailable/invalid.
                        }
                    });
        }
        return Map.copyOf(result);
    }

    private CompletionStage<PrestigeBoundaryCollection> collectBoundaryBaselines(
            java.util.UUID playerId,
            java.util.Collection<RequirementDefinition> definitions,
            Map<ProviderId, Long> pins) {
        LinkedHashMap<ProviderId, List<RequirementDefinition>> grouped = new LinkedHashMap<>();
        definitions.stream()
                .filter(definition -> definition.scope()
                        == net.maddkraft.maddprestige.core.requirement.MeasurementScope.SINCE_PRESTIGE_START)
                .sorted(java.util.Comparator.comparing(value -> value.id().value()))
                .forEach(definition -> grouped.computeIfAbsent(definition.providerId(),
                        ignored -> new ArrayList<>()).add(definition));
        List<CompletableFuture<PrestigeBoundaryCollection>> futures = grouped.entrySet().stream()
                .map(entry -> collectBoundaryProvider(playerId, entry.getKey(), entry.getValue(), pins))
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            ArrayList<PrestigeBaselineConsequence> baselines = new ArrayList<>();
            ArrayList<AuthorizationBlocker> blockers = new ArrayList<>();
            futures.forEach(future -> {
                PrestigeBoundaryCollection result = future.join();
                baselines.addAll(result.baselines());
                blockers.addAll(result.blockers());
            });
            return new PrestigeBoundaryCollection(baselines, blockers);
        });
    }

    private CompletableFuture<PrestigeBoundaryCollection> collectBoundaryProvider(
            java.util.UUID playerId,
            ProviderId providerId,
            List<RequirementDefinition> definitions,
            Map<ProviderId, Long> pins) {
        Long generation = pins.get(providerId);
        Optional<MetricProvider> provider = providers.provider(providerId)
                .filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast);
        if (generation == null || provider.isEmpty()) {
            return CompletableFuture.completedFuture(new PrestigeBoundaryCollection(List.of(),
                    List.of(b(AuthorizationBlockerKind.BOUNDARY_PROVIDER_UNAVAILABLE,
                            "Prestige boundary metric provider is absent: " + providerId.value(),
                            "provider", providerId.value()))));
        }
        LinkedHashMap<RequirementDefinition, MetricQuery> queries = new LinkedHashMap<>();
        definitions.forEach(definition -> queries.put(definition, new MetricQuery(definition.metricId(),
                definition.scope().readMode(), definition.filters())));
        CompletableFuture<Map<MetricQuery, net.maddkraft.maddprestige.api.metric.MetricSample>> future;
        try {
            var stage = provider.orElseThrow().read(playerId, queries.values().stream().distinct().toList(),
                    generation);
            future = stage == null ? CompletableFuture.failedFuture(
                    new IllegalStateException("Metric provider returned a null boundary stage"))
                    : stage.toCompletableFuture();
        } catch (RuntimeException exception) {
            future = CompletableFuture.failedFuture(exception);
        }
        return future.handle((samples, failure) -> {
            ArrayList<PrestigeBaselineConsequence> baselines = new ArrayList<>();
            ArrayList<AuthorizationBlocker> blockers = new ArrayList<>();
            if (failure != null) {
                blockers.add(b(AuthorizationBlockerKind.BOUNDARY_READ_FAILED,
                        "Prestige boundary read failed for " + providerId.value() + ": " + rootMessage(failure),
                        "provider", providerId.value()));
                return new PrestigeBoundaryCollection(baselines, blockers);
            }
            queries.forEach((definition, query) -> {
                var sample = samples.get(query);
                if (sample == null || sample.status() != MetricSampleStatus.AVAILABLE
                        || sample.providerGeneration() != generation) {
                    blockers.add(b(AuthorizationBlockerKind.BOUNDARY_SAMPLE_UNAVAILABLE,
                            "Prestige boundary sample is unavailable/stale: " + definition.id().value(),
                            "provider", providerId.value(), "requirement", definition.id().value()));
                } else {
                    baselines.add(new PrestigeBaselineConsequence(definition.id(),
                            definition.semanticFingerprint(), sample.value().orElseThrow(), generation));
                }
            });
            return new PrestigeBoundaryCollection(baselines, blockers);
        });
    }

    private static CompletionStage<PrestigeAuthorizationResult> rejected(
            AuthorizationBlockerKind kind,
            String diagnostic,
            Object... facts) {
        return rejected(List.of(b(kind, diagnostic, facts)));
    }

    private static CompletionStage<PrestigeAuthorizationResult> rejected(List<AuthorizationBlocker> blockers) {
        return CompletableFuture.completedFuture(PrestigeAuthorizationResult.rejected(blockers));
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
}
