package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.operation.Actor;

/** Consequence-free caller intent. All consequential inputs are loaded by the authorization service. */
public record PrestigeIntent(Actor actor, UUID playerId, UUID requestId, String idempotencyKey) {
    public PrestigeIntent {
        actor = Objects.requireNonNull(actor, "actor");
        playerId = Objects.requireNonNull(playerId, "player ID");
        requestId = Objects.requireNonNull(requestId, "request ID");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key");
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency key must contain 1-128 characters");
        }
    }

    public PrestigeIntent(Actor actor, UUID playerId, String idempotencyKey) {
        this(actor, playerId, UUID.randomUUID(), idempotencyKey);
    }
}
