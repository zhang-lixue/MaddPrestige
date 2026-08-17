package net.maddkraft.maddprestige.core.admin.config;

import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;

@FunctionalInterface
public interface ConfigurationSnapshotStore {
    PreparedConfigurationSnapshot prepare(ConfigRevisionId revisionId, CompiledConfiguration configuration);

    /** Server-owned pointer authority used only for durable transition recovery. */
    default Optional<ConfigRevisionId> currentRevision() {
        return Optional.empty();
    }
}
