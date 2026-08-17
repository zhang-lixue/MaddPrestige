package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public interface ConfigurationHistoryStore {
    void append(StoredConfigurationRevision revision);

    void replaceOutcome(StoredConfigurationRevision revision);

    Optional<StoredConfigurationRevision> find(ConfigRevisionId revisionId);

    List<StoredConfigurationRevision> recent(int limit);
}
