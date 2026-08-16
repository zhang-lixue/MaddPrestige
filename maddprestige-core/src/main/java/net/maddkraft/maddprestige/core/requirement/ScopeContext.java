package net.maddkraft.maddprestige.core.requirement;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ScopeId;

public record ScopeContext(Map<MeasurementScope, ScopeId> instances) {
    public ScopeContext {
        instances = Map.copyOf(Objects.requireNonNull(instances, "scope instances"));
    }

    public Optional<ScopeId> instance(MeasurementScope scope) {
        return Optional.ofNullable(instances.get(scope));
    }
}
