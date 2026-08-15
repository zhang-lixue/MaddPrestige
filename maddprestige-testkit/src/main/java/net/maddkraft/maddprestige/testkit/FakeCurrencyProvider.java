package net.maddkraft.maddprestige.testkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public final class FakeCurrencyProvider extends FakeProvider {
    private final Map<AccountKey, ExactDecimal> balances = new ConcurrentHashMap<>();
    private final FailureInjector failures;

    public FakeCurrencyProvider(FailureInjector failures) {
        super(new ProviderId("fake_currency"), "currency",
                List.of(new CapabilityDescriptor("exact-balance", "currency", "Exact fake balance", Map.of())));
        this.failures = failures;
    }

    public Result<ExactDecimal> balance(UUID player, CurrencyId currency) {
        failures.check("currency.balance");
        return Result.success(balances.getOrDefault(new AccountKey(player, currency), ExactDecimal.ZERO));
    }

    public void set(UUID player, CurrencyId currency, ExactDecimal value) {
        failures.check("currency.set");
        balances.put(new AccountKey(player, currency), value);
    }

    private record AccountKey(UUID player, CurrencyId currency) {
    }
}
