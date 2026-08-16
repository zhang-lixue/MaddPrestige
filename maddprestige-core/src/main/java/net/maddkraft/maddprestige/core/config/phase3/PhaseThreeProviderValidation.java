package net.maddkraft.maddprestige.core.config.phase3;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record PhaseThreeProviderValidation(
        ValidationReport report,
        Map<ProviderId, Long> providerGenerations) {
    public PhaseThreeProviderValidation {
        report = Objects.requireNonNull(report, "report");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
    }
}
