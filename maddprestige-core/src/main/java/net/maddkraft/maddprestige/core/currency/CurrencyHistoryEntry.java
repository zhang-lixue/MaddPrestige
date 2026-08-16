package net.maddkraft.maddprestige.core.currency;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record CurrencyHistoryEntry(
        long sequence,
        OperationId operationId,
        String actionId,
        UUID playerId,
        CurrencyId currencyId,
        ExactDecimal delta,
        ExactDecimal balanceAfter,
        CurrencyMutationKind kind,
        String actorType,
        String actorName,
        String source,
        String reason,
        ConfigRevisionId configRevision,
        Instant occurredAt) {
    public CurrencyHistoryEntry {
        if (sequence < 1) {
            throw new IllegalArgumentException("Currency history sequence must be positive");
        }
        operationId = Objects.requireNonNull(operationId, "operation ID");
        actionId = Objects.requireNonNull(actionId, "action ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        currencyId = Objects.requireNonNull(currencyId, "currency ID");
        delta = Objects.requireNonNull(delta, "delta");
        balanceAfter = Objects.requireNonNull(balanceAfter, "balance after");
        kind = Objects.requireNonNull(kind, "kind");
        actorType = Objects.requireNonNull(actorType, "actor type");
        actorName = Objects.requireNonNull(actorName, "actor name");
        source = Objects.requireNonNull(source, "source");
        reason = Objects.requireNonNull(reason, "reason");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        occurredAt = Objects.requireNonNull(occurredAt, "occurred at");
    }
}
