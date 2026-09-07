package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfigurationSnapshot;

/** One atomically published, revision-consistent Stage + provider-backed progression configuration view. */
public record ActiveStageConfiguration(
        StageConfigurationSnapshot stages,
        ProgressionConfigurationSnapshot progression) {
    public ActiveStageConfiguration {
        stages = Objects.requireNonNull(stages, "stage snapshot");
        progression = Objects.requireNonNull(progression, "requirement snapshot");
        if (!stages.revisionId().equals(progression.revisionId())) {
            throw new IllegalArgumentException("Stage and requirement snapshots must share one revision");
        }
    }
}
