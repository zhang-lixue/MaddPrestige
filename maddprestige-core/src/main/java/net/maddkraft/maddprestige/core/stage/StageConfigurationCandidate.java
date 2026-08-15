package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;

public record StageConfigurationCandidate(
        CompiledConfiguration compiled,
        StageConfiguration stageConfiguration,
        StageChangeImpact impact,
        ValidationReport validation,
        Map<ProviderId, Long> providerGenerations) {
    public StageConfigurationCandidate {
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
        stageConfiguration = Objects.requireNonNull(stageConfiguration, "stage configuration");
        impact = Objects.requireNonNull(impact, "impact");
        validation = Objects.requireNonNull(validation, "validation");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }
}
