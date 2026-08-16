package net.maddkraft.maddprestige.integrations.vault;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;

/** Current Vault balance metric. Reads are scheduled and fail closed when the exact binding is unavailable. */
public final class VaultBalanceMetricProvider implements MetricProvider {
    public static final MetricId BALANCE = new MetricId("balance");
    private final VaultEconomyBinding binding;
    private final Clock clock;
    private final ProviderDescriptor descriptor;
    private final MetricDescriptor metric;

    public VaultBalanceMetricProvider(VaultEconomyBinding binding, Clock clock, String implementationVersion) {
        this.binding = binding;
        this.clock = clock;
        descriptor = VaultProviderDescriptors.descriptor(VaultProviderDescriptors.BALANCE, "metric",
                implementationVersion);
        metric = new MetricDescriptor(VaultProviderDescriptors.BALANCE, BALANCE, MetricValueType.CURRENCY_AMOUNT,
                MetricOperator.compatibleWith(MetricValueType.CURRENCY_AMOUNT), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), "Vault balance",
                "Current balance from the bound Vault economy provider", "currency",
                "authoritative at observation; external economy may change immediately afterward");
    }

    @Override
    public Collection<MetricDescriptor> metrics() {
        return List.of(metric);
    }

    @Override
    public CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId, List<MetricQuery> queries, long providerGeneration) {
        return binding.scheduler().call(() -> {
            Instant now = clock.instant();
            LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
            for (MetricQuery query : queries) {
                if (!binding.available() || !BALANCE.equals(query.metricId()) || !query.filters().isEmpty()
                        || query.readMode() != MetricReadMode.CURRENT) {
                    result.put(query, MetricSample.unavailable(providerGeneration, now, "vault",
                            "Vault binding is unavailable or query is unsupported"));
                } else {
                    double balance = binding.economy().getBalance(binding.player(playerId));
                    result.put(query, Double.isFinite(balance)
                            ? MetricSample.available(MetricValue.parse(MetricValueType.CURRENCY_AMOUNT,
                                    BigDecimal.valueOf(balance).toPlainString()), providerGeneration, now, "vault")
                            : MetricSample.unavailable(providerGeneration, now, "vault",
                                    "Vault returned a non-finite balance"));
                }
            }
            return Map.copyOf(result);
        });
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return binding.health().get();
    }
}
