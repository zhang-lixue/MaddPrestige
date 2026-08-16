package net.maddkraft.maddprestige.core.config.phase4;

import java.util.Objects;
import net.maddkraft.maddprestige.core.stage.ActiveStageConfiguration;

public record ActivePhaseFourConfiguration(
        ActiveStageConfiguration priorPhases,
        PhaseFourConfigurationSnapshot phaseFour) {
    public ActivePhaseFourConfiguration {
        priorPhases = Objects.requireNonNull(priorPhases, "prior-phase configuration");
        phaseFour = Objects.requireNonNull(phaseFour, "Phase 4 configuration");
        if (!priorPhases.stages().revisionId().equals(phaseFour.revisionId())) {
            throw new IllegalArgumentException("All active configuration snapshots must share one revision");
        }
        var retainedPins = phaseFour.providerGenerations();
        priorPhases.phaseThree().providerGenerations().forEach((provider, generation) -> {
            if (!generation.equals(retainedPins.get(provider))) {
                throw new IllegalArgumentException("Phase 4 snapshot must retain every prior provider pin exactly");
            }
        });
    }
}
