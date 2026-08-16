package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;

public record StageConfigurationCandidate(
        CompiledConfiguration compiled,
        StageConfiguration stageConfiguration,
        PhaseThreeConfiguration phaseThreeConfiguration,
        StageChangeImpact impact,
        ValidationReport validation,
        Map<ProviderId, Long> providerGenerations) {
    public StageConfigurationCandidate {
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
        stageConfiguration = Objects.requireNonNull(stageConfiguration, "stage configuration");
        phaseThreeConfiguration = Objects.requireNonNull(phaseThreeConfiguration, "Phase 3 configuration");
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
        this(compiled, stageConfiguration, PhaseThreeConfiguration.empty(), impact, validation, providerGenerations);
    }
}
