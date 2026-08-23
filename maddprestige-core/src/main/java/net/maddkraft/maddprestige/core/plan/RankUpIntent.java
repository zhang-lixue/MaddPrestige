package net.maddkraft.maddprestige.core.plan;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;

/** Non-authoritative caller intent. Progression state is resolved only by trusted engine dependencies. */
public record RankUpIntent(
        Actor actor,
        UUID playerId,
        UUID requestId,
        Optional<StageId> intendedTarget,
        String idempotencyKey) {
    public RankUpIntent {
        actor = Objects.requireNonNull(actor, "actor");
        playerId = Objects.requireNonNull(playerId, "player ID");
        requestId = Objects.requireNonNull(requestId, "request ID");
        intendedTarget = Objects.requireNonNull(intendedTarget, "intended target");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key");
    }

    public RankUpIntent(
            Actor actor,
            UUID playerId,
            Optional<StageId> intendedTarget,
            String idempotencyKey) {
        this(actor, playerId, UUID.randomUUID(), intendedTarget, idempotencyKey);
    }
}
