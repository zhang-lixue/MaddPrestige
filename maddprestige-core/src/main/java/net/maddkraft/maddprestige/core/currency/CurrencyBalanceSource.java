package net.maddkraft.maddprestige.core.currency;

import java.util.UUID;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

@FunctionalInterface
public interface CurrencyBalanceSource {
    ExactDecimal balance(UUID playerId, CurrencyId currencyId);
}
