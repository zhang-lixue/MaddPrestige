package net.maddkraft.maddprestige.core.admin.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigCompiler;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationCompiler;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationValidator;
import net.maddkraft.maddprestige.core.config.lifecycle.ActiveLifecycleConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfigurationCompiler;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfigurationSnapshot;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfigurationValidator;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageChangeImpactAnalyzer;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageConfigurationCompiler;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageConfigurationValidator;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;

public final class AdministrationConfigurationWorkflow {
    private final ConfigurationService canonical;
    private final ProviderRegistry providers;
    private final StageReferenceMigrationStore stageReferences;
    private final List<ConfigurationValidationExtension> extensions;
    private final AtomicReference<ActiveLifecycleConfiguration> active = new AtomicReference<>();

    public AdministrationConfigurationWorkflow(
            ConfigurationService canonical,
            ProviderRegistry providers,
            Supplier<Map<StageId, Long>> playerReferences,
            List<ConfigurationValidationExtension> extensions) {
        this(canonical, providers, StageReferenceMigrationStore.readOnly(playerReferences), extensions);
    }

    public AdministrationConfigurationWorkflow(
            ConfigurationService canonical,
            ProviderRegistry providers,
            StageReferenceMigrationStore stageReferences,
            List<ConfigurationValidationExtension> extensions) {
        this.canonical = Objects.requireNonNull(canonical, "canonical service");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.stageReferences = Objects.requireNonNull(stageReferences, "stage references");
        this.extensions = List.copyOf(Objects.requireNonNull(extensions, "validation extensions"));
    }

    public Optional<ActiveLifecycleConfiguration> active() {
        return Optional.ofNullable(active.get());
    }

    public CompletionStage<AdministrationConfigurationCandidate> prepare(
            ConfigDraft draft,
            Optional<StageRemapPlan> remapPlan) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(remapPlan, "remap plan");
        var compiled = new ConfigCompiler().compile(draft);
        var stageCompilation = new StageConfigurationCompiler().compile(compiled);
        StageConfiguration stages = stageCompilation.configuration().orElse(StageConfiguration.inactive());
        var descriptors = metricDescriptors();
        var progressionCompilation = new ProgressionConfigurationCompiler().compile(compiled, descriptors);
        var lifecycleCompilation = new LifecycleConfigurationCompiler().compile(compiled);
        StageConfiguration prior = active().map(value -> value.prerequisites().stages().configuration())
                .orElse(StageConfiguration.inactive());
        StageReferenceSnapshot references = stageReferences.capture(remapPlan);
        var impact = new StageChangeImpactAnalyzer().analyze(prior, stages, references.counts(), remapPlan);
        var progressionProviders = new ProgressionConfigurationValidator()
                .validate(progressionCompilation.configuration(), stages, providers);
        var lifecycleProviders = new LifecycleConfigurationValidator().validateProviders(
                lifecycleCompilation.configuration(), progressionCompilation.configuration(), stages, providers,
                progressionProviders.providerGenerations());
        ValidationReport local = stageCompilation.validation().combine(impact.validation())
                .combine(progressionCompilation.validation()).combine(progressionProviders.report())
                .combine(lifecycleCompilation.validation()).combine(lifecycleProviders.report());
        for (ConfigurationValidationExtension extension : extensions) {
            try {
                local = local.combine(extension.validate(compiled));
            } catch (RuntimeException exception) {
                local = local.combine(ValidationReport.of(List.of(finding("config.extension.failed",
                        "configuration", "Configuration extension validation failed.",
                        "Restore the extension or remove its active configuration before apply."))));
            }
        }
        ValidationReport preparedLocal = local;
        Map<ProviderId, Long> initialPins = lifecycleProviders.providerGenerations();
        Optional<ProviderId> rankProvider = stages.active() ? stages.rankProvider() : Optional.empty();
        Optional<Long> rankGeneration = rankProvider.flatMap(providers::find).map(value -> value.generation());
        return new StageConfigurationValidator().validateExternalTargets(stages, providers).thenApply(external -> {
            ValidationReport combined = preparedLocal.combine(external);
            LinkedHashMap<ProviderId, Long> pins = new LinkedHashMap<>(initialPins);
            if (rankProvider.isPresent()) {
                var snapshot = providers.find(rankProvider.orElseThrow());
                if (rankGeneration.isEmpty() || snapshot.isEmpty()
                        || snapshot.orElseThrow().generation() != rankGeneration.orElseThrow()
                        || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                        || !healthy(snapshot.orElseThrow().health().state())) {
                    combined = combined.combine(ValidationReport.of(List.of(finding(
                            "stage.rank_provider.changed_during_validation", "progression.stages",
                            "The rank-provider binding changed while targets were checked.",
                            "Restore the provider and validate the draft again."))));
                } else {
                    Long priorPin = pins.putIfAbsent(rankProvider.orElseThrow(), rankGeneration.orElseThrow());
                    if (priorPin != null && priorPin.longValue() != rankGeneration.orElseThrow()) {
                        combined = combined.combine(ValidationReport.of(List.of(finding(
                                "stage.rank_provider.generation_conflict", "progression.stages",
                                "The rank-provider generation conflicts with another canonical pin.",
                                "Prepare every document against one current provider generation."))));
                    }
                }
            }
            if (impact.explicitRemapRequired() && references.remap().isEmpty()) {
                combined = combined.combine(ValidationReport.of(List.of(finding(
                        "stage.change.remap_snapshot_missing", "progression.stages",
                        "Referenced stage deletion has no executable persisted-reference snapshot.",
                        "Preview through a migration-capable stage repository and select an explicit replacement."))));
            }
            if (references.remap().isPresent()
                    && !references.remap().orElseThrow().countsBySource().equals(impact.affectedPlayerReferences())) {
                combined = combined.combine(ValidationReport.of(List.of(finding(
                        "stage.change.remap_snapshot_inconsistent", "progression.stages",
                        "Persisted stage reference counts changed while the exact remap snapshot was captured.",
                        "Preview again against one current persisted player-state snapshot."))));
            }
            Optional<StageRemapSnapshot> executableRemap = impact.explicitRemapRequired()
                    ? references.remap() : Optional.empty();
            return new AdministrationConfigurationCandidate(compiled, stages, progressionCompilation.configuration(),
                    lifecycleCompilation.configuration(), impact, executableRemap, combined, pins);
        });
    }

    public Optional<ConfigurationStageTransitionExecution> beginStageTransition(
            ConfigRevisionId revisionId,
            Optional<ConfigRevisionId> priorRevision,
            AdministrationConfigurationCandidate candidate,
            net.maddkraft.maddprestige.api.operation.Actor actor,
            String reason,
            java.time.Instant occurredAt) {
        LinkedHashMap<StageId, ConfigurationStageReservationKind> reserved = new LinkedHashMap<>();
        candidate.stageImpact().removedStages().forEach(stage ->
                reserved.put(stage, ConfigurationStageReservationKind.REMOVED));
        candidate.stageImpact().disabledStages().forEach(stage ->
                reserved.put(stage, ConfigurationStageReservationKind.DISABLED));
        if (reserved.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(stageReferences.beginTransition(revisionId, priorRevision,
                candidate.compiled().contentHash(), reserved, candidate.stageRemap(), actor, reason, occurredAt));
    }

    public void markStageTransitionApplied(ConfigRevisionId revisionId, java.time.Instant occurredAt) {
        stageReferences.markTransitionApplied(revisionId, occurredAt);
    }

    public void markStageTransitionFailedSafe(
            ConfigRevisionId revisionId,
            String detail,
            java.time.Instant occurredAt) {
        stageReferences.markTransitionFailedSafe(revisionId, detail, occurredAt);
    }

    public void markStageTransitionNeedsReconciliation(
            ConfigRevisionId revisionId,
            String detail,
            java.time.Instant occurredAt) {
        stageReferences.markTransitionNeedsReconciliation(revisionId, detail, occurredAt);
    }

    public List<ConfigurationStageTransitionState> stageTransitions(int limit) {
        return stageReferences.transitions(limit);
    }

    public List<StageRemapReconciliation> unresolvedStageRemaps(int limit) {
        return stageReferences.unresolved(limit);
    }

    public synchronized ActiveLifecycleConfiguration apply(
            ConfigRevisionId revisionId,
            AdministrationConfigurationCandidate candidate,
            Set<String> acknowledgements,
            BackupMetadata backup) {
        validatePins(candidate.providerGenerations());
        canonical.apply(revisionId, candidate.compiled(), candidate.validation(), acknowledgements, backup);
        StageConfigurationSnapshot stageSnapshot = new StageConfigurationSnapshot(revisionId, candidate.stages());
        ProgressionConfigurationSnapshot progressionSnapshot = new ProgressionConfigurationSnapshot(
                revisionId, candidate.progression(), candidate.providerGenerations());
        ActiveStageConfiguration prerequisites = new ActiveStageConfiguration(stageSnapshot, progressionSnapshot);
        LifecycleConfigurationSnapshot lifecycleSnapshot = new LifecycleConfigurationSnapshot(
                revisionId, candidate.lifecycle(), candidate.providerGenerations());
        ActiveLifecycleConfiguration replacement = new ActiveLifecycleConfiguration(prerequisites, lifecycleSnapshot);
        active.set(replacement);
        return replacement;
    }

    private Map<MetricBinding, net.maddkraft.maddprestige.api.metric.MetricDescriptor> metricDescriptors() {
        LinkedHashMap<MetricBinding, net.maddkraft.maddprestige.api.metric.MetricDescriptor> descriptors =
                new LinkedHashMap<>();
        providers.snapshots().forEach(snapshot -> providers.provider(snapshot.descriptor().id())
                .filter(MetricProvider.class::isInstance).map(MetricProvider.class::cast).ifPresent(provider -> {
                    try {
                        provider.metrics().forEach(metric -> descriptors.put(
                                new MetricBinding(metric.providerId(), metric.metricId()), metric));
                    } catch (RuntimeException ignored) {
                        // The canonical validators emit an actionable unavailable-provider finding.
                    }
                }));
        return Map.copyOf(descriptors);
    }

    private void validatePins(Map<ProviderId, Long> pins) {
        pins.forEach((providerId, generation) -> {
            var snapshot = providers.find(providerId);
            if (snapshot.isEmpty() || snapshot.orElseThrow().generation() != generation
                    || snapshot.orElseThrow().activation() != ActivationState.ACTIVE
                    || !healthy(snapshot.orElseThrow().health().state())) {
                throw new IllegalStateException("Pinned provider binding changed; validate the draft again");
            }
        });
    }

    private static boolean healthy(ProviderHealthState state) {
        return state == ProviderHealthState.AVAILABLE || state == ProviderHealthState.ACTIVE;
    }

    private static ValidationFinding finding(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "The last known-good configuration remains active.", remediation);
    }
}
