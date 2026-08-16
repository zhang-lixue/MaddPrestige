package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricValue;

public record PrestigeBaselineConsequence(
        RequirementId requirementId,
        String semanticFingerprint,
        MetricValue value,
        long providerGeneration) {
    public PrestigeBaselineConsequence {
        requirementId = Objects.requireNonNull(requirementId, "requirement ID");
        semanticFingerprint = Objects.requireNonNull(semanticFingerprint, "semantic fingerprint");
        value = Objects.requireNonNull(value, "value");
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Baseline provider generation must be positive");
        }
    }
}
