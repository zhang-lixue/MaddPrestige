package net.maddkraft.maddprestige.core.admin.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.config.lifecycle.LifecycleConfiguration;
import net.maddkraft.maddprestige.core.stage.StageChangeImpact;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

public record AdministrationConfigurationCandidate(
        CompiledConfiguration compiled,
        StageConfiguration stages,
        ProgressionConfiguration progression,
        LifecycleConfiguration lifecycle,
        StageChangeImpact stageImpact,
        Optional<StageRemapSnapshot> stageRemap,
        ValidationReport validation,
        Map<ProviderId, Long> providerGenerations) {
    public AdministrationConfigurationCandidate {
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
        stages = Objects.requireNonNull(stages, "stages");
        progression = Objects.requireNonNull(progression, "requirement configuration");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle configuration");
        stageImpact = Objects.requireNonNull(stageImpact, "stage impact");
        stageRemap = Objects.requireNonNull(stageRemap, "stage remap");
        validation = Objects.requireNonNull(validation, "validation");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }
}
