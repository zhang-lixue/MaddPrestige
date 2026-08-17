package net.maddkraft.maddprestige.core.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record OperationPreview(
        OperationKind kind,
        UUID playerId,
        boolean executable,
        String stateChange,
        Optional<ExplanationNode> requirements,
        List<String> costs,
        List<String> rewards,
        List<String> consequences,
        List<String> blockers,
        ConfigRevisionId configRevision,
        Map<ProviderId, Long> providerGenerations,
        List<String> externalUncertainty) {
    public OperationPreview {
        kind = Objects.requireNonNull(kind, "kind");
        playerId = Objects.requireNonNull(playerId, "player ID");
        stateChange = Objects.requireNonNull(stateChange, "state change");
        requirements = Objects.requireNonNull(requirements, "requirements");
        costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
        rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        consequences = List.copyOf(Objects.requireNonNull(consequences, "consequences"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        externalUncertainty = List.copyOf(Objects.requireNonNull(externalUncertainty, "external uncertainty"));
        if (executable && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Executable preview cannot contain blockers");
        }
    }
}
