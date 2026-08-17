package net.maddkraft.maddprestige.core.admin.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.config.ContentHash;

/** Durable diagnostic/recovery view of one configuration-owned stage transition. */
public record ConfigurationStageTransitionState(
        ConfigRevisionId configurationRevision,
        Optional<ConfigRevisionId> priorRevision,
        ContentHash candidateHash,
        ConfigurationStageTransitionStatus status,
        boolean scopeComplete,
        Map<StageId, ConfigurationStageReservationKind> reservedStages,
        Optional<ConfigurationApplicationStatus> ownerStatus,
        Instant updatedAt,
        String detail) {
    public ConfigurationStageTransitionState {
        configurationRevision = Objects.requireNonNull(configurationRevision, "configuration revision");
        priorRevision = Objects.requireNonNull(priorRevision, "prior revision");
        candidateHash = Objects.requireNonNull(candidateHash, "candidate hash");
        status = Objects.requireNonNull(status, "status");
        reservedStages = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(
                reservedStages, "reserved stages")));
        ownerStatus = Objects.requireNonNull(ownerStatus, "owner status");
        updatedAt = Objects.requireNonNull(updatedAt, "updated at");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean abnormal() {
        return reservedStages.isEmpty()
                || !scopeComplete
                || ownerStatus.isEmpty()
                || status == ConfigurationStageTransitionStatus.NEEDS_RECONCILIATION
                || (status.active() && ownerStatus.filter(ConfigurationApplicationStatus.ATTEMPTED::equals).isEmpty())
                || (!status.active() && !reservedStages.isEmpty());
    }
}
