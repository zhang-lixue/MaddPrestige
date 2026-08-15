package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
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

public final class StageConfigurationWorkflow {
    private final ConfigCompiler canonicalCompiler;
    private final ConfigurationService canonicalService;
    private final StageConfigurationCompiler stageCompiler;
    private final StageConfigurationValidator validator;
    private final StageChangeImpactAnalyzer impactAnalyzer;
    private final AtomicReference<StageConfigurationSnapshot> active = new AtomicReference<>();

    public StageConfigurationWorkflow(ConfigurationService canonicalService) {
        this.canonicalCompiler = new ConfigCompiler();
        this.canonicalService = Objects.requireNonNull(canonicalService, "canonical configuration service");
        this.stageCompiler = new StageConfigurationCompiler();
        this.validator = new StageConfigurationValidator();
        this.impactAnalyzer = new StageChangeImpactAnalyzer();
    }

    public Optional<StageConfigurationSnapshot> active() {
        return Optional.ofNullable(active.get());
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
        StageConfiguration configuration = stageCompilation.configuration().orElse(StageConfiguration.inactive());
        StageConfiguration prior = active().map(StageConfigurationSnapshot::configuration)
                .orElse(StageConfiguration.inactive());
        StageChangeImpact impact = impactAnalyzer.analyze(prior, configuration, playerReferences, remapPlan);
        var localValidation = stageCompilation.validation().combine(impact.validation());
        if (stageCompilation.configuration().isEmpty() || localValidation.hasErrors()) {
            return CompletableFuture.completedFuture(new StageConfigurationCandidate(
                    compiled, configuration, impact, localValidation, Map.of()));
        }
        Optional<ProviderId> requiredProvider = configuration.active()
                ? configuration.rankProvider() : Optional.empty();
        Optional<Long> beforeGeneration = requiredProvider.flatMap(providers::find)
                .map(snapshot -> snapshot.generation());
        return validator.validateExternalTargets(configuration, providers)
                .thenApply(external -> {
                    ValidationReport combined = localValidation.combine(external);
                    Map<ProviderId, Long> generations = Map.of();
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
                            generations = Map.of(providerId, beforeGeneration.orElseThrow());
                        }
                    }
                    return new StageConfigurationCandidate(
                            compiled, configuration, impact, combined, generations);
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
        active.set(replacement);
        return replacement;
    }

    private static void validatePinnedProviders(
            StageConfigurationCandidate candidate,
            ProviderRegistry providers) {
        Optional<ProviderId> requiredProvider = candidate.stageConfiguration().active()
                ? candidate.stageConfiguration().rankProvider() : Optional.empty();
        if (requiredProvider.isEmpty()) {
            return;
        }
        ProviderId providerId = requiredProvider.orElseThrow();
        Long generation = candidate.providerGenerations().get(providerId);
        var current = providers.find(providerId);
        if (generation == null || current.isEmpty()
                || current.orElseThrow().generation() != generation
                || current.orElseThrow().activation() != ActivationState.ACTIVE
                || !healthy(current.orElseThrow().health().state())) {
            throw new IllegalStateException(
                    "Pinned rank adapter binding changed after candidate validation; prepare the draft again");
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
