package net.maddkraft.maddprestige.core.season;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.core.config.phase4.ResetDisposition;

public record SeasonRecord(
        SeasonId id,
        String displayNameSnapshot,
        SeasonLifecycleState state,
        ScopeId scopeId,
        ResetDisposition progressPolicy,
        ConfigRevisionId configRevision,
        Instant startedAt,
        Optional<Instant> endedAt,
        Optional<Instant> archivedAt) {
    public SeasonRecord {
        id = Objects.requireNonNull(id, "season ID");
        displayNameSnapshot = Objects.requireNonNull(displayNameSnapshot, "display name snapshot");
        state = Objects.requireNonNull(state, "state");
        scopeId = Objects.requireNonNull(scopeId, "scope ID");
        progressPolicy = Objects.requireNonNull(progressPolicy, "progress policy");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        startedAt = Objects.requireNonNull(startedAt, "started at");
        endedAt = Objects.requireNonNull(endedAt, "ended at");
        archivedAt = Objects.requireNonNull(archivedAt, "archived at");
        if ((state == SeasonLifecycleState.ACTIVE) != (endedAt.isEmpty() && archivedAt.isEmpty())
                || state == SeasonLifecycleState.ENDED && (endedAt.isEmpty() || archivedAt.isPresent())
                || state == SeasonLifecycleState.ARCHIVED && (endedAt.isEmpty() || archivedAt.isEmpty())) {
            throw new IllegalArgumentException("Season lifecycle timestamps do not match state");
        }
    }
}
