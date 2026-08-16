package net.maddkraft.maddprestige.core.currency;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record CurrencyMutation(
        OperationId operationId,
        String actionId,
        UUID playerId,
        CurrencyId currencyId,
        ExactDecimal delta,
        CurrencyMutationKind kind,
        Actor actor,
        String source,
        String reason,
        ConfigRevisionId configRevision,
        Instant occurredAt) {
    public CurrencyMutation {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        actionId = Objects.requireNonNull(actionId, "action ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        currencyId = Objects.requireNonNull(currencyId, "currency ID");
        delta = Objects.requireNonNull(delta, "delta");
        kind = Objects.requireNonNull(kind, "kind");
        actor = Objects.requireNonNull(actor, "actor");
        source = Objects.requireNonNull(source, "source");
        reason = Objects.requireNonNull(reason, "reason");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        occurredAt = Objects.requireNonNull(occurredAt, "occurred at");
        if (actionId.isBlank() || source.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException("Currency action/source/reason cannot be blank");
        }
        if (kind == CurrencyMutationKind.EARN && delta.asBigDecimal().signum() <= 0
                || kind == CurrencyMutationKind.SPEND && delta.asBigDecimal().signum() >= 0) {
            throw new IllegalArgumentException("Currency earn/spend delta has the wrong sign");
        }
    }
}
