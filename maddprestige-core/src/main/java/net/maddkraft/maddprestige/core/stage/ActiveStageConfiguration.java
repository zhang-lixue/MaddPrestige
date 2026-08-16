package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationSnapshot;

/** One atomically published, revision-consistent Stage + Phase 3 configuration view. */
public record ActiveStageConfiguration(
        StageConfigurationSnapshot stages,
        PhaseThreeConfigurationSnapshot phaseThree) {
    public ActiveStageConfiguration {
        stages = Objects.requireNonNull(stages, "stage snapshot");
        phaseThree = Objects.requireNonNull(phaseThree, "Phase 3 snapshot");
        if (!stages.revisionId().equals(phaseThree.revisionId())) {
            throw new IllegalArgumentException("Stage and Phase 3 snapshots must share one revision");
        }
    }
}
