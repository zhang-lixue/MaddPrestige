package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record LifecycleProviderValidation(
        ValidationReport report,
        Map<ProviderId, Long> providerGenerations) {
    public LifecycleProviderValidation {
        report = Objects.requireNonNull(report, "report");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }
}
