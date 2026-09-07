package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.milestone.MilestoneTriggerType;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.RequirementTreeValidator;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

public final class LifecycleConfigurationValidator {
    public ValidationReport validate(
            LifecycleConfiguration configuration,
            ProgressionConfiguration progression,
            StageConfiguration stages) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        PrestigeConfiguration prestige = configuration.prestige();
        if (prestige.enabled()) {
            prestige.requirementTreeId().filter(id -> !progression.trees().containsKey(id)).ifPresent(id ->
                    findings.add(error("prestige.configuration.requirements", "prestige.requirement-tree",
                            "Prestige references unknown requirement tree " + id.value(),
                            "Define the requirement tree before applying.")));
            prestige.costIds().stream().filter(id -> !progression.costs().containsKey(id)).forEach(id ->
                    findings.add(error("prestige.configuration.cost", "prestige.costs",
                            "Prestige references unknown cost " + id.value(),
                            "Define the cost before applying.")));
            prestige.rewardIds().stream().filter(id -> !progression.rewards().containsKey(id)).forEach(id ->
                    findings.add(error("prestige.configuration.reward", "prestige.rewards",
                            "Prestige references unknown reward " + id.value(),
                            "Define the reward before applying.")));
        }
        configuration.valueScaling().costs().forEach((id, profile) -> {
            var definition = progression.costs().get(id);
            if (definition == null) {
                findings.add(error("scaling.cost.unknown", "prestige.cost-scaling." + id.value(),
                        "Cost scaling references an unknown cost.", "Define the cost before applying."));
            } else if (!definition.amount().type().isNumeric()) {
                findings.add(error("scaling.cost.non_numeric", "prestige.cost-scaling." + id.value(),
                        "Only numeric provider costs may be scaled.", "Remove scaling or use a numeric amount."));
            }
            validateCoverage(profile, prestige.limit(), "prestige.cost-scaling." + id.value(), findings);
        });
        configuration.valueScaling().rewards().forEach((id, profile) -> {
            var definition = progression.rewards().get(id);
            if (definition == null) {
                findings.add(error("scaling.reward.unknown", "prestige.reward-scaling." + id.value(),
                        "Reward scaling references an unknown reward.", "Define the reward before applying."));
            } else if (!definition.value().type().isNumeric()) {
                findings.add(error("scaling.reward.non_numeric", "prestige.reward-scaling." + id.value(),
                        "Only numeric provider rewards may be scaled.", "Remove scaling or use a numeric value."));
            }
            validateCoverage(profile, prestige.limit(), "prestige.reward-scaling." + id.value(), findings);
        });
        progression.requirements().values().stream().filter(value -> !value.scaling().segments().isEmpty())
                .forEach(requirement -> validateCoverage(
                        new net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile(
                                requirement.scaling().segments()), prestige.limit(),
                        "requirements.requirements." + requirement.id().value() + ".scaling", findings));
        if (prestige.externalResetsEnabled()) {
            findings.add(error("prestige.configuration.external_reset", "prestige.external-resets",
                    "No safe external-reset capability is configured.",
                    "Keep external resets disabled until a qualified opt-in provider exists."));
        }
        if (prestige.scalingProfileId().isPresent() || prestige.catchUpProfileId().isPresent()) {
            findings.add(error("prestige.configuration.profile.unsupported", "prestige",
                    "Prestige scaling-profile and catch-up-profile have no runtime semantics.",
                    "Use per-requirement scaling and prestige.cost-scaling/prestige.reward-scaling instead."));
        }
        validatePrestigeResetPolicy(prestige, findings);
        for (var milestone : configuration.milestones().values()) {
            HashSet<RewardId> missing = new HashSet<>(milestone.rewardIds());
            missing.removeAll(progression.rewards().keySet());
            if (!missing.isEmpty()) {
                findings.add(error("milestone.configuration.reward", "milestones." + milestone.id().value(),
                        "Milestone references unknown rewards " + missing,
                        "Define every reward before applying."));
            }
            if ((milestone.triggerType() == MilestoneTriggerType.CURRENT_PRESTIGE
                    || milestone.triggerType() == MilestoneTriggerType.LIFETIME_PRESTIGE)
                    && (!milestone.threshold().type().isNumeric() || milestone.threshold().asNumber().signum() <= 0)) {
                findings.add(error("milestone.configuration.threshold", "milestones." + milestone.id().value(),
                        "Prestige-count milestones require a positive numeric threshold.",
                        "Use a positive integer/count/exact-decimal threshold."));
            }
            if (milestone.enabled() && milestone.triggerType() != MilestoneTriggerType.CURRENT_PRESTIGE
                    && milestone.triggerType() != MilestoneTriggerType.LIFETIME_PRESTIGE) {
                findings.add(error("milestone.configuration.trigger.unsupported",
                        "milestones." + milestone.id().value() + ".trigger",
                        "This milestone trigger is not consumed by the Prestige runtime.",
                        "Use CURRENT_PRESTIGE/LIFETIME_PRESTIGE or disable the milestone until its runtime exists."));
            }
        }
        configuration.seasons().values().forEach(season -> {
            if (!season.requirementOverrides().isEmpty()) {
                findings.add(error("season.configuration.requirement_override.unsupported",
                        "seasons." + season.id().value() + ".requirement-overrides",
                        "Season requirement overrides are not applied by the requirement runtime.",
                        "Remove requirement-overrides until resolution is supported."));
            }
            if (!season.catchUpProfileReferences().isEmpty()) {
                findings.add(error("season.configuration.catch_up.unsupported",
                        "seasons." + season.id().value() + ".catch-up-profiles",
                        "Season catch-up profile references are not applied by the runtime.",
                        "Remove catch-up-profiles until they are supported."));
            }
        });
        return ValidationReport.of(findings);
    }

    public LifecycleProviderValidation validateProviders(
            LifecycleConfiguration configuration,
            ProgressionConfiguration progression,
            StageConfiguration stages,
            ProviderRegistry providers,
            Map<ProviderId, Long> priorPins) {
        ArrayList<ValidationFinding> findings = new ArrayList<>(
                validate(configuration, progression, stages).findings());
        LinkedHashMap<ProviderId, Long> generations = new LinkedHashMap<>(priorPins);
        PrestigeConfiguration prestige = configuration.prestige();
        if (prestige.enabled()) {
            prestige.costIds().stream().map(progression.costs()::get).filter(java.util.Objects::nonNull)
                    .forEach(cost -> pin(providers, cost.providerId(), CostProvider.class, true,
                            "prestige.costs." + cost.id().value(), findings, generations));
            prestige.rewardIds().stream().map(progression.rewards()::get).filter(java.util.Objects::nonNull)
                    .forEach(reward -> pin(providers, reward.providerId(), RewardProvider.class,
                            reward.failurePolicy() == RewardFailurePolicy.REQUIRED,
                            "prestige.rewards." + reward.id().value(), findings, generations));
            prestige.requirementTreeId().map(progression.trees()::get).ifPresent(tree -> {
                LinkedHashSet<ProviderId> metricProviders = new LinkedHashSet<>();
                LifecycleRequirementReachability.prestigeDefinitions(configuration, progression)
                        .forEach(definition -> metricProviders.add(definition.providerId()));
                LinkedHashMap<MetricBinding, MetricDescriptor> descriptors = new LinkedHashMap<>();
                metricProviders.forEach(providerId -> {
                    Provider provider = pin(providers, providerId, MetricProvider.class, true,
                            "prestige.requirement-tree.providers." + providerId.value(), findings, generations);
                    if (provider instanceof MetricProvider metrics) {
                        try {
                            metrics.metrics().forEach(metric -> descriptors.put(
                                    new MetricBinding(metric.providerId(), metric.metricId()), metric));
                        } catch (RuntimeException exception) {
                            findings.add(error("requirement.configuration.metric.discovery",
                                    "prestige.requirement-tree.providers." + providerId.value(),
                                    "Metric discovery failed: " + exception.getMessage(),
                                    "Restore the required provider and prepare the configuration again."));
                        }
                    }
                });
                findings.addAll(new RequirementTreeValidator().validate(tree, descriptors,
                        progression.maximumTreeDepth()).findings());
            });
        }
        configuration.milestones().values().stream().filter(value -> value.enabled()).forEach(milestone -> {
            milestone.rewardIds().stream().map(progression.rewards()::get).filter(java.util.Objects::nonNull)
                    .forEach(reward -> pin(providers, reward.providerId(), RewardProvider.class,
                            reward.failurePolicy() == RewardFailurePolicy.REQUIRED,
                            "milestones." + milestone.id().value() + ".rewards", findings, generations));
            if (milestone.triggerType() == MilestoneTriggerType.PROVIDER_METRIC) {
                ProviderId providerId = milestone.providerId().orElseThrow();
                Provider provider = pin(providers, providerId, MetricProvider.class, true,
                        "milestones." + milestone.id().value() + ".provider", findings, generations);
                if (provider instanceof MetricProvider metrics) {
                    MetricId metricId;
                    try {
                        metricId = new MetricId(milestone.providerMetricId().orElseThrow());
                    } catch (IllegalArgumentException exception) {
                        findings.add(error("milestone.configuration.metric", "milestones." + milestone.id().value(),
                                exception.getMessage(), "Use a valid immutable metric ID."));
                        return;
                    }
                    try {
                        if (metrics.metrics().stream().noneMatch(metric -> metric.metricId().equals(metricId))) {
                            findings.add(error("milestone.configuration.metric", "milestones." + milestone.id().value(),
                                    "Provider does not advertise the configured milestone metric.",
                                    "Reference a metric advertised by the pinned provider generation."));
                        }
                    } catch (RuntimeException exception) {
                        findings.add(error("milestone.configuration.metric", "milestones." + milestone.id().value(),
                                "Provider metric discovery failed: " + exception.getMessage(),
                                "Restore provider health and prepare the configuration again."));
                    }
                }
            }
        });
        return new LifecycleProviderValidation(ValidationReport.of(findings), generations);
    }

    private static Provider pin(
            ProviderRegistry providers,
            ProviderId providerId,
            Class<? extends Provider> contract,
            boolean required,
            String path,
            List<ValidationFinding> findings,
            Map<ProviderId, Long> generations) {
        var snapshot = providers.find(providerId);
        var provider = providers.provider(providerId);
        boolean usable = snapshot.isPresent() && provider.isPresent()
                && snapshot.orElseThrow().activation() == ActivationState.ACTIVE
                && healthy(snapshot.orElseThrow().health().state())
                && contract.isInstance(provider.orElseThrow());
        if (!usable) {
            if (required) {
                findings.add(error("lifecycle.configuration.provider.unavailable", path,
                        "Required provider is absent, unhealthy, inactive, or lacks " + contract.getSimpleName() + ".",
                        "Restore and activate the required provider before apply."));
            }
            return null;
        }
        Long prior = generations.putIfAbsent(providerId, snapshot.orElseThrow().generation());
        if (prior != null && prior.longValue() != snapshot.orElseThrow().generation()) {
            findings.add(error("lifecycle.configuration.provider.generation", path,
                    "Provider generation differs from the prior canonical pin.",
                    "Prepare all canonical configuration documents against one provider generation."));
            return null;
        }
        return provider.orElseThrow();
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static void validatePrestigeResetPolicy(
            PrestigeConfiguration prestige,
            List<ValidationFinding> findings) {
        ResetPreservePolicy policy = prestige.resetPolicy();
        ResetDisposition scoped = policy.disposition(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS);
        if (policy.disposition(ResetComponent.BASELINES) != scoped
                || policy.disposition(ResetComponent.LATCHED_COMPLETIONS) != scoped) {
            findings.add(error("reset_policy.scoped_state_conflict", "prestige.reset-policy",
                    "ACTIVE_REQUIREMENT_PROGRESS, BASELINES, and LATCHED_COMPLETIONS must share one disposition.",
                    "Set all three to RESET for a new scope or all three to PRESERVE for the existing scope."));
        }
        requirePreserve(policy, ResetComponent.PURCHASED_PERKS,
                "No purchased-perk store exists", findings);
        requirePreserve(policy, ResetComponent.MILESTONE_HISTORY,
                "The runtime preserves auditable milestone eligibility/history", findings);
        requirePreserve(policy, ResetComponent.SEASON_PROGRESS,
                "Prestige does not own or mutate player season progress", findings);
        requirePreserve(policy, ResetComponent.HISTORICAL_STATISTICS,
                "Historical/lifetime evidence is append-only", findings);
    }

    private static void requirePreserve(
            ResetPreservePolicy policy,
            ResetComponent component,
            String reason,
            List<ValidationFinding> findings) {
        if (policy.disposition(component) != ResetDisposition.PRESERVE) {
            findings.add(error("reset_policy.unsupported",
                    "prestige.reset-policy." + component.name().toLowerCase(java.util.Locale.ROOT)
                            .replace('_', '-'),
                    reason + "; RESET is unsupported.",
                    "Use PRESERVE for this component."));
        }
    }

    private static void validateCoverage(
            net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile profile,
            PrestigeLimit limit,
            String path,
            List<ValidationFinding> findings) {
        if (!profile.covers(limit)) {
            findings.add(error(limit.maximum().isPresent()
                            ? "scaling.coverage.incomplete_finite"
                            : "scaling.coverage.incomplete_unlimited", path,
                    limit.maximum().isPresent()
                            ? "Scaling must cover finite maximum P" + limit.maximum().getAsLong() + "."
                            : "Scaling P" + profile.segments().getLast().startLevel()
                                    + "+ must be open-ended for unlimited Prestige.",
                    "Extend the final segment through the maximum or use end-prestige: unlimited."));
        }
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "The active lifecycle configuration fails closed.", remediation);
    }
}
