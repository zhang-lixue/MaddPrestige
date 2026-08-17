package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record StageRemapReconciliation(
        UUID operationId,
        ConfigRevisionId configurationRevision,
        String status,
        int migratedPlayers,
        Instant updatedAt,
        String detail) {
    public StageRemapReconciliation {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        configurationRevision = Objects.requireNonNull(configurationRevision, "configuration revision");
        status = Objects.requireNonNull(status, "status");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
