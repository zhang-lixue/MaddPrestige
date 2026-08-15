package net.maddkraft.maddprestige.persistence;

import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public interface CurrencyAccountRepository {
    void set(UUID playerId, CurrencyId currencyId, ExactDecimal value);

    Optional<ExactDecimal> find(UUID playerId, CurrencyId currencyId);
}
