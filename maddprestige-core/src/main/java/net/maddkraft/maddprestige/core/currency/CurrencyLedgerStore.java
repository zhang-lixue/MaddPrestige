package net.maddkraft.maddprestige.core.currency;

import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.CurrencyId;

public interface CurrencyLedgerStore extends CurrencyBalanceSource {
    CurrencyMutationResult apply(CurrencyDefinition definition, CurrencyMutation mutation);

    List<CurrencyHistoryEntry> history(UUID playerId, CurrencyId currencyId, int limit);
}
