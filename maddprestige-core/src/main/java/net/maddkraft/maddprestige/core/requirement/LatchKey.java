package net.maddkraft.maddprestige.core.requirement;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;

public record LatchKey(
        UUID playerId,
        RequirementId requirementId,
        MeasurementScope scope,
        ScopeId scopeInstance,
        String semanticFingerprint) {
    public LatchKey {
        playerId = Objects.requireNonNull(playerId, "player ID");
        requirementId = Objects.requireNonNull(requirementId, "requirement ID");
        scope = Objects.requireNonNull(scope, "scope");
        scopeInstance = Objects.requireNonNull(scopeInstance, "scope instance");
        semanticFingerprint = Objects.requireNonNull(semanticFingerprint, "semantic fingerprint");
    }
}
