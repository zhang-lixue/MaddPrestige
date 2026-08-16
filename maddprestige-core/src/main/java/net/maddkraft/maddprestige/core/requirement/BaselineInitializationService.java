package net.maddkraft.maddprestige.core.requirement;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricSampleStatus;

public final class BaselineInitializationService {
    private final RequirementStateWriter states;
    private final Clock clock;

    public BaselineInitializationService(RequirementStateWriter states, Clock clock) {
        this.states = Objects.requireNonNull(states, "requirement state writer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<RequirementBaseline> enterScope(
            UUID playerId,
            MeasurementScope scope,
            net.maddkraft.maddprestige.api.id.ScopeId scopeInstance,
            List<RequirementDefinition> requirements,
            Map<RequirementId, MetricSample> samples,
            Map<ProviderId, Long> providerGenerations) {
        if (!scope.requiresBaseline()) {
            throw new IllegalArgumentException("Only snapshot-relative scopes initialize baselines");
        }
        ArrayList<RequirementBaseline> initialized = new ArrayList<>();
        for (RequirementDefinition definition : requirements) {
            if (definition.scope() != scope) {
                continue;
            }
            MetricSample sample = Objects.requireNonNull(samples.get(definition.id()),
                    "missing baseline sample for " + definition.id().value());
            Long generation = Objects.requireNonNull(providerGenerations.get(definition.providerId()),
                    "missing provider generation");
            if (sample.status() != MetricSampleStatus.AVAILABLE || sample.providerGeneration() != generation) {
                throw new IllegalStateException("Cannot initialize a baseline from an unavailable or stale sample");
            }
            BaselineKey key = new BaselineKey(playerId, definition.id(), scope, scopeInstance,
                    definition.semanticFingerprint());
            RequirementBaseline proposed = new RequirementBaseline(key, sample.value().orElseThrow(), generation,
                    clock.instant());
            initialized.add(states.initializeBaseline(proposed));
        }
        return List.copyOf(initialized);
    }
}
