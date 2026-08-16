package net.maddkraft.maddprestige.core.requirement;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;

public record RequirementEvaluationBinding(
        UUID playerId,
        Optional<RequirementId> treeId,
        String treeSemanticIdentity,
        ConfigRevisionId configRevision,
        Map<ProviderId, Long> providerGenerations,
        Map<MeasurementScope, ScopeId> scopeInstances) {
    public RequirementEvaluationBinding {
        playerId = Objects.requireNonNull(playerId, "player ID");
        treeId = Objects.requireNonNull(treeId, "tree ID");
        treeSemanticIdentity = Objects.requireNonNull(treeSemanticIdentity, "tree semantic identity");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        scopeInstances = Map.copyOf(Objects.requireNonNull(scopeInstances, "scope instances"));
        if (!treeSemanticIdentity.matches("rtf1:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Requirement tree identity must use the rtf1 format");
        }
    }
}
