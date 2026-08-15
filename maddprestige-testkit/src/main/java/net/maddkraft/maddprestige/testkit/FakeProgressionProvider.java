package net.maddkraft.maddprestige.testkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public final class FakeProgressionProvider extends FakeProvider {
    private final Map<MetricKey, ExactDecimal> metrics = new ConcurrentHashMap<>();

    public FakeProgressionProvider() {
        super(new ProviderId("fake_progression"), "progression",
                List.of(new CapabilityDescriptor("typed-metrics", "progression", "Fake exact progression metrics", Map.of())));
    }

    public void set(UUID player, MetricId metric, ExactDecimal value) {
        metrics.put(new MetricKey(player, metric), value);
    }

    public Result<ExactDecimal> sample(UUID player, MetricId metric) {
        ProviderHealthState state = health().state();
        if (state != ProviderHealthState.AVAILABLE && state != ProviderHealthState.ACTIVE) {
            return Result.failure(StructuredError.unavailable("provider.metric.unavailable",
                    "Fake progression provider is " + state));
        }
        ExactDecimal value = metrics.get(new MetricKey(player, metric));
        if (value == null) {
            return Result.failure(StructuredError.unavailable("provider.metric.missing", "Metric has no fake sample"));
        }
        return Result.success(value);
    }

    private record MetricKey(UUID player, MetricId metric) {
    }
}
