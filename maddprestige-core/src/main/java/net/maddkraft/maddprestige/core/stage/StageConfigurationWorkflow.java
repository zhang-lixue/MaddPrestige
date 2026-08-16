package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigCompiler;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationCompiler;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationValidator;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;

public final class StageConfigurationWorkflow {
    private final ConfigCompiler canonicalCompiler;
    private final ConfigurationService canonicalService;
    private final StageConfigurationCompiler stageCompiler;
    private final StageConfigurationValidator validator;
    private final StageChangeImpactAnalyzer impactAnalyzer;
    private final PhaseThreeConfigurationCompiler phaseThreeCompiler;
    private final PhaseThreeConfigurationValidator phaseThreeValidator;
    private final AtomicReference<ActiveStageConfiguration> activeSnapshots = new AtomicReference<>();

    public StageConfigurationWorkflow(ConfigurationService canonicalService) {
        this.canonicalCompiler = new ConfigCompiler();
        this.canonicalService = Objects.requireNonNull(canonicalService, "canonical configuration service");
        this.stageCompiler = new StageConfigurationCompiler();
        this.validator = new StageConfigurationValidator();
        this.impactAnalyzer = new StageChangeImpactAnalyzer();
        this.phaseThreeCompiler = new PhaseThreeConfigurationCompiler();
        this.phaseThreeValidator = new PhaseThreeConfigurationValidator();
    }

    public Optional<PhaseThreeConfigurationSnapshot> activePhaseThree() {
        return Optional.ofNullable(activeSnapshots.get()).map(ActiveStageConfiguration::phaseThree);
    }

    public Optional<StageConfigurationSnapshot> active() {
        return Optional.ofNullable(activeSnapshots.get()).map(ActiveStageConfiguration::stages);
    }

    public Optional<ActiveStageConfiguration> activeCanonical() {
        return Optional.ofNullable(activeSnapshots.get());
    }

    public CompletionStage<StageConfigurationCandidate> prepare(
            ConfigDraft draft,
            Map<StageId, Long> playerReferences,
            Optional<StageRemapPlan> remapPlan,
            ProviderRegistry providers) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(playerReferences, "player references");
        Objects.requireNonNull(remapPlan, "remap plan");
        Objects.requireNonNull(providers, "provider registry");
        var compiled = canonicalCompiler.compile(draft);
        var stageCompilation = stageCompiler.compile(compiled);
        LinkedHashMap<MetricBinding, net.maddkraft.maddprestige.api.metric.MetricDescriptor> descriptors =
                new LinkedHashMap<>();
        providers.snapshots().forEach(snapshot -> providers.provider(snapshot.descriptor().id())
                .filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast)
                .ifPresent(provider -> {
                    try {
                        provider.metrics().forEach(metric -> descriptors.put(
                                new MetricBinding(metric.providerId(), metric.metricId()), metric));
                    } catch (RuntimeException ignored) {
                        // A broken capability boundary becomes an unknown/unavailable metric finding below.
                    }
                }));
        var phaseThreeCompilation = phaseThreeCompiler.compile(compiled, descriptors);
        StageConfiguration configuration = stageCompilation.configuration().orElse(StageConfiguration.inactive());
        StageConfiguration prior = active().map(StageConfigurationSnapshot::configuration)
                .orElse(StageConfiguration.inactive());
        StageChangeImpact impact = impactAnalyzer.analyze(prior, configuration, playerReferences, remapPlan);
        var phaseThreeProviderValidation = phaseThreeValidator.validate(
                phaseThreeCompilation.configuration(), configuration, providers);
        var localValidation = stageCompilation.validation().combine(impact.validation())
                .combine(phaseThreeCompilation.validation()).combine(phaseThreeProviderValidation.report());
        if (stageCompilation.configuration().isEmpty() || localValidation.hasErrors()) {
            return CompletableFuture.completedFuture(new StageConfigurationCandidate(
                    compiled, configuration, phaseThreeCompilation.configuration(), impact, localValidation,
                    phaseThreeProviderValidation.providerGenerations()));
        }
        Optional<ProviderId> requiredProvider = configuration.active()
                ? configuration.rankProvider() : Optional.empty();
        Optional<Long> beforeGeneration = requiredProvider.flatMap(providers::find)
                .map(snapshot -> snapshot.generation());
        return validator.validateExternalTargets(configuration, providers)
                .thenApply(external -> {
                    ValidationReport combined = localValidation.combine(external);
                    Map<ProviderId, Long> generations = phaseThreeProviderValidation.providerGenerations();
                    if (requiredProvider.isPresent() && beforeGeneration.isPresent()) {
                        ProviderId providerId = requiredProvider.orElseThrow();
                        var after = providers.find(providerId);
                        if (after.isEmpty()
                                || after.orElseThrow().generation() != beforeGeneration.orElseThrow()
                                || after.orElseThrow().activation() != ActivationState.ACTIVE
                                || !healthy(after.orElseThrow().health().state())) {
                            combined = combined.combine(ValidationReport.of(java.util.List.of(bindingFinding(
                                    "stage.rank_provider.changed_during_validation",
                                    "Rank adapter binding changed while external targets were validated."))));
                        } else {
                            LinkedHashMap<ProviderId, Long> combinedGenerations = new LinkedHashMap<>(generations);
                            combinedGenerations.put(providerId, beforeGeneration.orElseThrow());
                            generations = Map.copyOf(combinedGenerations);
                        }
                    }
                    return new StageConfigurationCandidate(
                            compiled, configuration, phaseThreeCompilation.configuration(), impact, combined,
                            generations);
                });
    }

    public StageConfigurationSnapshot apply(
            ConfigRevisionId revisionId,
            StageConfigurationCandidate candidate,
            Set<String> acknowledgements,
            BackupMetadata backup,
            ProviderRegistry providers) {
        validatePinnedProviders(candidate, providers);
        canonicalService.apply(revisionId, candidate.compiled(), candidate.validation(), acknowledgements, backup);
        StageConfigurationSnapshot replacement = new StageConfigurationSnapshot(
                revisionId, candidate.stageConfiguration());
        activeSnapshots.set(new ActiveStageConfiguration(replacement, new PhaseThreeConfigurationSnapshot(revisionId,
                candidate.phaseThreeConfiguration(), candidate.providerGenerations())));
        return replacement;
    }

    private static void validatePinnedProviders(
            StageConfigurationCandidate candidate,
            ProviderRegistry providers) {
        for (var pinned : candidate.providerGenerations().entrySet()) {
            var current = providers.find(pinned.getKey());
            if (current.isEmpty() || current.orElseThrow().generation() != pinned.getValue()
                    || current.orElseThrow().activation() != ActivationState.ACTIVE
                    || !healthy(current.orElseThrow().health().state())) {
                throw new IllegalStateException(
                        "Pinned provider binding changed after candidate validation; prepare the draft again");
            }
        }
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static ValidationFinding bindingFinding(String code, String explanation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, "progression.stages", explanation,
                "External validation is pinned to one provider generation.",
                "Restore the provider and prepare the draft again before apply.");
    }
}
