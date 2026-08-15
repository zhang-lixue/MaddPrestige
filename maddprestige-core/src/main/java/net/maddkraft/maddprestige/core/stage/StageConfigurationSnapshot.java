package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record StageConfigurationSnapshot(ConfigRevisionId revisionId, StageConfiguration configuration) {
    public StageConfigurationSnapshot {
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        configuration = Objects.requireNonNull(configuration, "configuration");
    }
}
