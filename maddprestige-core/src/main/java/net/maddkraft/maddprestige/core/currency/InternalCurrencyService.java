package net.maddkraft.maddprestige.core.currency;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public final class InternalCurrencyService {
    private final Supplier<Map<CurrencyId, CurrencyDefinition>> definitions;
    private final CurrencyLedgerStore store;
    private final Clock clock;

    public InternalCurrencyService(
            Supplier<Map<CurrencyId, CurrencyDefinition>> definitions,
            CurrencyLedgerStore store,
            Clock clock) {
        this.definitions = Objects.requireNonNull(definitions, "currency definitions");
        this.store = Objects.requireNonNull(store, "currency store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CurrencyMutationResult earn(
            OperationId operationId,
            String actionId,
            UUID playerId,
            CurrencyId currencyId,
            ExactDecimal amount,
            Actor actor,
            String source,
            String reason,
            ConfigRevisionId revision) {
        requirePositive(amount);
        return apply(operationId, actionId, playerId, currencyId, amount, CurrencyMutationKind.EARN, actor,
                source, reason, revision);
    }

    public CurrencyMutationResult spend(
            OperationId operationId,
            String actionId,
            UUID playerId,
            CurrencyId currencyId,
            ExactDecimal amount,
            Actor actor,
            String source,
            String reason,
            ConfigRevisionId revision) {
        requirePositive(amount);
        return apply(operationId, actionId, playerId, currencyId, amount.negate(), CurrencyMutationKind.SPEND, actor,
                source, reason, revision);
    }

    /** Audited administrative boundary. Adjustment is still idempotent by operation/action ID. */
    public CurrencyMutationResult adjust(
            OperationId operationId,
            String actionId,
            UUID playerId,
            CurrencyId currencyId,
            ExactDecimal delta,
            Actor actor,
            String reason,
            ConfigRevisionId revision) {
        if ("player".equalsIgnoreCase(actor.type()) || reason.isBlank()) {
            throw new SecurityException("Currency adjustment requires a non-player actor and explicit reason");
        }
        return apply(operationId, actionId, playerId, currencyId, delta, CurrencyMutationKind.ADJUSTMENT, actor,
                "administration", reason, revision);
    }

    private CurrencyMutationResult apply(
            OperationId operationId,
            String actionId,
            UUID playerId,
            CurrencyId currencyId,
            ExactDecimal delta,
            CurrencyMutationKind kind,
            Actor actor,
            String source,
            String reason,
            ConfigRevisionId revision) {
        CurrencyDefinition definition = definitions.get().get(currencyId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown internal currency ID: " + currencyId.value());
        }
        CurrencyMutation mutation = new CurrencyMutation(operationId, actionId, playerId, currencyId, delta, kind,
                actor, source, reason, revision, clock.instant());
        return store.apply(definition, mutation);
    }

    private static void requirePositive(ExactDecimal amount) {
        if (amount.asBigDecimal().signum() <= 0) {
            throw new IllegalArgumentException("Currency amount must be positive");
        }
    }
}
