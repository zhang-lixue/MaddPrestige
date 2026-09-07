package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;

public record StageConfigurationCandidate(
        CompiledConfiguration compiled,
        StageConfiguration stageConfiguration,
        ProgressionConfiguration progressionConfiguration,
        StageChangeImpact impact,
        ValidationReport validation,
        Map<ProviderId, Long> providerGenerations) {
    public StageConfigurationCandidate {
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
        stageConfiguration = Objects.requireNonNull(stageConfiguration, "stage configuration");
        progressionConfiguration = Objects.requireNonNull(progressionConfiguration, "requirement configuration");
        impact = Objects.requireNonNull(impact, "impact");
        validation = Objects.requireNonNull(validation, "validation");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }

    public StageConfigurationCandidate(
            CompiledConfiguration compiled,
            StageConfiguration stageConfiguration,
            StageChangeImpact impact,
            ValidationReport validation,
            Map<ProviderId, Long> providerGenerations) {
        this(compiled, stageConfiguration, ProgressionConfiguration.empty(), impact, validation, providerGenerations);
    }
}
