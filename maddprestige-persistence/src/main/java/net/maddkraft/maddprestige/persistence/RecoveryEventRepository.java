package net.maddkraft.maddprestige.persistence;

import java.util.List;
import net.maddkraft.maddprestige.api.id.OperationId;

public interface RecoveryEventRepository {
    void append(RecoveryEvent event);

    List<RecoveryEvent> find(OperationId operationId, int limit);
}
