package net.maddkraft.maddprestige.core.requirement;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricOperator;

public record RequirementDefinition(
        RequirementId id,
        ProviderId providerId,
        MetricId metricId,
        MetricOperator operator,
        RequirementTarget target,
        MeasurementScope scope,
        CompletionMode completionMode,
        ScalingProfile scaling,
        CatchUpProfile catchUp,
        Map<String, String> filters,
        Map<String, String> displayMetadata,
        boolean hidden,
        String semanticFingerprint) {
    public RequirementDefinition {
        id = Objects.requireNonNull(id, "requirement ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        metricId = Objects.requireNonNull(metricId, "metric ID");
        operator = Objects.requireNonNull(operator, "operator");
        target = Objects.requireNonNull(target, "target");
        scope = Objects.requireNonNull(scope, "scope");
        completionMode = Objects.requireNonNull(completionMode, "completion mode");
        scaling = Objects.requireNonNull(scaling, "scaling");
        catchUp = Objects.requireNonNull(catchUp, "catch-up");
        filters = Map.copyOf(Objects.requireNonNull(filters, "filters"));
        displayMetadata = Map.copyOf(Objects.requireNonNull(displayMetadata, "display metadata"));
        semanticFingerprint = Objects.requireNonNull(semanticFingerprint, "semantic fingerprint");
        if (!semanticFingerprint.matches("rsf[0-9]+:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Semantic fingerprint must carry a versioned SHA-256 format");
        }
        filters.forEach((key, value) -> validateFilterPart("key", key, 128));
        filters.forEach((key, value) -> validateFilterPart("value", value, 512));
    }

    public static RequirementDefinition create(
            RequirementId id,
            ProviderId providerId,
            MetricId metricId,
            MetricOperator operator,
            RequirementTarget target,
            MeasurementScope scope,
            CompletionMode completionMode,
            ScalingProfile scaling,
            CatchUpProfile catchUp,
            Map<String, String> filters,
            Map<String, String> displayMetadata,
            boolean hidden) {
        String fingerprint = RequirementSemantics.fingerprint(providerId, metricId, operator, target, scope,
                completionMode, scaling, catchUp, filters);
        return new RequirementDefinition(id, providerId, metricId, operator, target, scope, completionMode,
                scaling, catchUp, filters, displayMetadata, hidden, fingerprint);
    }

    private static void validateFilterPart(String kind, String value, int maximumLength) {
        if (value.isEmpty() || value.length() > maximumLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Requirement filter " + kind
                    + " is empty, too long, or contains a control character");
        }
    }
}
