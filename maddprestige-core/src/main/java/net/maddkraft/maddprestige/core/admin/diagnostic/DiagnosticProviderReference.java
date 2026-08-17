package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record DiagnosticProviderReference(
        ProviderId providerId,
        Optional<MetricId> metricId,
        String path) {
    public DiagnosticProviderReference {
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
        path = Objects.requireNonNull(path, "path");
    }
}
