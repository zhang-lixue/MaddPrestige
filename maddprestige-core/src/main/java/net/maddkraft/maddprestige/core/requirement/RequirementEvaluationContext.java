package net.maddkraft.maddprestige.core.requirement;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record RequirementEvaluationContext(
        UUID playerId,
        ConfigRevisionId configRevision,
        Map<ProviderId, Long> providerGenerations,
        long scalingIndex,
        ExactDecimal catchUpPosition,
        ScopeContext scopes,
        Map<RequirementId, MetricSample> samples,
        RequirementStateReader stateReader) {
    public RequirementEvaluationContext {
        playerId = Objects.requireNonNull(playerId, "player ID");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        if (scalingIndex < 0) {
            throw new IllegalArgumentException("Scaling index cannot be negative");
        }
        catchUpPosition = Objects.requireNonNull(catchUpPosition, "catch-up position");
        if (catchUpPosition.asBigDecimal().signum() < 0) {
            throw new IllegalArgumentException("Catch-up position cannot be negative");
        }
        scopes = Objects.requireNonNull(scopes, "scopes");
        samples = Map.copyOf(Objects.requireNonNull(samples, "samples"));
        stateReader = Objects.requireNonNull(stateReader, "state reader");
    }
}
