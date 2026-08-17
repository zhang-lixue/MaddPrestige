package net.maddkraft.maddprestige.core.admin.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfiguration;
import net.maddkraft.maddprestige.core.stage.StageChangeImpact;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

public record PhaseSixConfigurationCandidate(
        CompiledConfiguration compiled,
        StageConfiguration stages,
        PhaseThreeConfiguration phaseThree,
        PhaseFourConfiguration phaseFour,
        StageChangeImpact stageImpact,
        Optional<StageRemapSnapshot> stageRemap,
        ValidationReport validation,
        Map<ProviderId, Long> providerGenerations) {
    public PhaseSixConfigurationCandidate {
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
        stages = Objects.requireNonNull(stages, "stages");
        phaseThree = Objects.requireNonNull(phaseThree, "Phase 3 configuration");
        phaseFour = Objects.requireNonNull(phaseFour, "Phase 4 configuration");
        stageImpact = Objects.requireNonNull(stageImpact, "stage impact");
        stageRemap = Objects.requireNonNull(stageRemap, "stage remap");
        validation = Objects.requireNonNull(validation, "validation");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }
}
