package net.maddkraft.maddprestige.core.config.phase3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

/** Validates only providers transitively reachable from the enabled active ladder. */
public final class PhaseThreeConfigurationValidator {
    public PhaseThreeProviderValidation validate(
            PhaseThreeConfiguration configuration,
            StageConfiguration stages,
            ProviderRegistry providers) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        ActiveReferences references = activeReferences(configuration, stages, findings);
        LinkedHashSet<ProviderId> required = new LinkedHashSet<>();
        references.requirements().forEach(node -> collectMetricProviders(node, required));
        references.costs().forEach(cost -> required.add(cost.providerId()));
        references.requiredRewards().forEach(reward -> required.add(reward.providerId()));

        LinkedHashSet<ProviderId> bound = new LinkedHashSet<>(required);
        references.optionalRewards().stream().map(RewardDefinition::providerId)
                .filter(providerId -> usable(providers, providerId)).forEach(bound::add);

        LinkedHashMap<ProviderId, Long> generations = new LinkedHashMap<>();
        for (ProviderId providerId : bound) {
            boolean isRequired = required.contains(providerId);
            var snapshot = providers.find(providerId);
            var provider = providers.provider(providerId);
            if (snapshot.isEmpty() || provider.isEmpty()
                    || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                    || !healthy(snapshot.orElseThrow().health().state())) {
                if (isRequired) {
                    findings.add(error("phase3.provider.unavailable", "providers." + providerId.value(),
                            "Required active Phase 3 provider is absent, inactive, unhealthy, or unsupported.",
                            "Restore and activate the exact provider before applying configuration."));
                }
                continue;
            }
            if (!contractsMatch(providerId, provider.orElseThrow(), references)) {
                findings.add(error("phase3.provider.contract", "providers." + providerId.value(),
                        "Provider does not implement every active metric/cost/reward contract.",
                        "Use capability-compatible provider definitions."));
                continue;
            }
            generations.put(providerId, snapshot.orElseThrow().generation());
        }

        references.costs().forEach(cost -> validateCost(cost, providers, findings));
        references.requirements().forEach(tree -> validateMetrics(tree, providers, findings));
        references.requiredRewards().forEach(reward -> validateReward(reward, providers, findings));
        references.optionalRewards().stream().filter(reward -> usable(providers, reward.providerId()))
                .forEach(reward -> validateReward(reward, providers, findings));
        return new PhaseThreeProviderValidation(ValidationReport.of(findings), generations);
    }

    private static ActiveReferences activeReferences(
            PhaseThreeConfiguration configuration,
            StageConfiguration stages,
            List<ValidationFinding> findings) {
        LinkedHashSet<RequirementNode> requirements = new LinkedHashSet<>();
        LinkedHashSet<CostDefinition> costs = new LinkedHashSet<>();
        LinkedHashSet<RewardDefinition> requiredRewards = new LinkedHashSet<>();
        LinkedHashSet<RewardDefinition> optionalRewards = new LinkedHashSet<>();
        if (!stages.active()) {
            return new ActiveReferences(requirements, costs, requiredRewards, optionalRewards);
        }
        stages.order().stream().map(stages.stages()::get).filter(java.util.Objects::nonNull)
                .filter(stage -> stage.enabled()).forEach(stage -> {
                    stage.requirementTreeId().ifPresent(id -> {
                        RequirementNode tree = configuration.trees().get(id);
                        if (tree == null) {
                            findings.add(unknown("stage.requirement_tree.unknown", stage.id().value(),
                                    "requirement tree", id.value()));
                        } else {
                            requirements.add(tree);
                        }
                    });
                    for (CostId id : stage.costIds()) {
                        CostDefinition cost = configuration.costs().get(id);
                        if (cost == null) {
                            findings.add(unknown("stage.cost.unknown", stage.id().value(), "cost", id.value()));
                        } else {
                            costs.add(cost);
                        }
                    }
                    for (RewardId id : stage.rewardIds()) {
                        RewardDefinition reward = configuration.rewards().get(id);
                        if (reward == null) {
                            findings.add(unknown("stage.reward.unknown", stage.id().value(), "reward", id.value()));
                        } else if (reward.failurePolicy() == RewardFailurePolicy.REQUIRED) {
                            requiredRewards.add(reward);
                        } else {
                            optionalRewards.add(reward);
                        }
                    }
                });
        return new ActiveReferences(requirements, costs, requiredRewards, optionalRewards);
    }

    private static void collectMetricProviders(RequirementNode node, Set<ProviderId> providers) {
        if (node instanceof RequirementLeaf leaf) {
            providers.add(leaf.definition().providerId());
            return;
        }
        ((RequirementGroup) node).children().forEach(child -> collectMetricProviders(child.node(), providers));
    }

    private static boolean contractsMatch(ProviderId id, Provider provider, ActiveReferences references) {
        boolean metric = references.requirements().stream().noneMatch(tree -> containsMetricProvider(tree, id))
                || provider instanceof MetricProvider;
        boolean cost = references.costs().stream().noneMatch(value -> value.providerId().equals(id))
                || provider instanceof CostProvider;
        boolean reward = java.util.stream.Stream.concat(references.requiredRewards().stream(),
                        references.optionalRewards().stream()).noneMatch(value -> value.providerId().equals(id))
                || provider instanceof RewardProvider;
        return metric && cost && reward;
    }

    private static boolean containsMetricProvider(RequirementNode node, ProviderId id) {
        if (node instanceof RequirementLeaf leaf) {
            return leaf.definition().providerId().equals(id);
        }
        return ((RequirementGroup) node).children().stream()
                .anyMatch(child -> containsMetricProvider(child.node(), id));
    }

    private static void validateMetrics(
            RequirementNode node,
            ProviderRegistry providers,
            List<ValidationFinding> findings) {
        if (node instanceof RequirementLeaf leaf) {
            ProviderId providerId = leaf.definition().providerId();
            MetricId metricId = leaf.definition().metricId();
            providers.provider(providerId).filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast)
                    .ifPresent(provider -> {
                        try {
                            boolean advertised = provider.metrics().stream()
                                    .anyMatch(metric -> metric.providerId().equals(providerId)
                                            && metric.metricId().equals(metricId));
                            if (!advertised) {
                                findings.add(error("phase3.metric.unavailable",
                                        "providers." + providerId.value() + ".metrics." + metricId.value(),
                                        "Active requirement references a metric the provider does not advertise.",
                                        "Advertise the exact metric or disable every stage that reaches it."));
                            }
                        } catch (RuntimeException exception) {
                            findings.add(providerFailure(providerId, "metric capability", exception));
                        }
                    });
            return;
        }
        ((RequirementGroup) node).children()
                .forEach(child -> validateMetrics(child.node(), providers, findings));
    }

    private static void validateCost(
            CostDefinition definition,
            ProviderRegistry providers,
            List<ValidationFinding> findings) {
        providers.provider(definition.providerId()).filter(CostProvider.class::isInstance).map(CostProvider.class::cast)
                .ifPresent(provider -> {
                    try {
                        findings.addAll(provider.validate(definition).findings());
                    } catch (RuntimeException exception) {
                        findings.add(providerFailure(definition.providerId(), "cost", exception));
                    }
                });
    }

    private static void validateReward(
            RewardDefinition definition,
            ProviderRegistry providers,
            List<ValidationFinding> findings) {
        providers.provider(definition.providerId()).filter(RewardProvider.class::isInstance)
                .map(RewardProvider.class::cast).ifPresent(provider -> {
                    try {
                        findings.addAll(provider.validate(definition).findings());
                    } catch (RuntimeException exception) {
                        findings.add(providerFailure(definition.providerId(), "reward", exception));
                    }
                });
    }

    private static boolean usable(ProviderRegistry providers, ProviderId id) {
        var snapshot = providers.find(id);
        return snapshot.isPresent() && providers.provider(id).isPresent()
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state());
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static ValidationFinding unknown(String code, String stage, String kind, String id) {
        return error(code, "progression.stages." + stage,
                "Enabled stage references unknown " + kind + " " + id + ".",
                "Define the stable " + kind + " before activation.");
    }

    private static ValidationFinding providerFailure(ProviderId provider, String contract, Throwable failure) {
        return error("phase3.provider.validation_exception", "providers." + provider.value(),
                "Provider threw while validating an active " + contract + ": " + rootMessage(failure),
                "Repair or replace the provider; validation failures never escape or activate silently.");
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "Active references and required providers fail closed.", remediation);
    }

    private record ActiveReferences(
            Set<RequirementNode> requirements,
            Set<CostDefinition> costs,
            Set<RewardDefinition> requiredRewards,
            Set<RewardDefinition> optionalRewards) {
        private ActiveReferences {
            requirements = Set.copyOf(requirements);
            costs = Set.copyOf(costs);
            requiredRewards = Set.copyOf(requiredRewards);
            optionalRewards = Set.copyOf(optionalRewards);
        }
    }
}
