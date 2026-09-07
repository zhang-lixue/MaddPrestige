package net.maddkraft.maddprestige.core.config.progression;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record ProgressionProviderValidation(
        ValidationReport report,
        Map<ProviderId, Long> providerGenerations) {
    public ProgressionProviderValidation {
        report = Objects.requireNonNull(report, "report");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }
}
