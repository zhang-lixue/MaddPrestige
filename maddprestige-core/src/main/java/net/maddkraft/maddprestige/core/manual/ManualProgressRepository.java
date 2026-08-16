package net.maddkraft.maddprestige.core.manual;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public interface ManualProgressRepository {
    Map<UUID, ManualProgressRecord> load(ProviderId providerId, MetricId metricId);

    void writeBatch(Collection<ManualProgressRecord> records);
}
