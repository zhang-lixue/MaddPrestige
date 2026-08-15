package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record StageRuntimeStatus(
        StageRuntimeState state,
        Optional<ConfigRevisionId> revisionId,
        String reason) {
    public StageRuntimeStatus {
        state = Objects.requireNonNull(state, "state");
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
