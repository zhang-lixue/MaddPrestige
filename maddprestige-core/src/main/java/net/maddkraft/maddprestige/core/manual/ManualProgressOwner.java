package net.maddkraft.maddprestige.core.manual;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Non-serializable owner capability issued only with its exact provider at bootstrap. */
public final class ManualProgressOwner {
    private final ManualProgressProvider provider;
    private final UUID capability;

    ManualProgressOwner(ManualProgressProvider provider, UUID capability) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    public CompletionStage<ManualMetricHandle> registerMetric(ManualCounterDefinition definition) {
        return provider.registerMetric(capability, definition)
                .thenApply(registration -> new ManualMetricHandle(provider, registration, capability));
    }

    public ProgressProvenance provenance(String source, Instant observedAt) {
        return provider.attest(capability, source, observedAt);
    }
}
