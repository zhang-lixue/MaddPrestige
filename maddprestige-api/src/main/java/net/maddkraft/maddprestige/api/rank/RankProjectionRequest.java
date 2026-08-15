package net.maddkraft.maddprestige.api.rank;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;

public record RankProjectionRequest(
        UUID playerId,
        OperationId operationId,
        ConfigRevisionId configRevision,
        long providerGeneration,
        Set<String> managedGroups,
        Optional<String> desiredGroup) {
    public RankProjectionRequest {
        playerId = Objects.requireNonNull(playerId, "player ID");
        operationId = Objects.requireNonNull(operationId, "operation ID");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
        managedGroups = Set.copyOf(Objects.requireNonNull(managedGroups, "managed groups"));
        desiredGroup = Objects.requireNonNull(desiredGroup, "desired group");
        if (desiredGroup.isPresent() && !managedGroups.contains(desiredGroup.orElseThrow())) {
            throw new IllegalArgumentException(
                    "Desired group is not in the pinned managed set: " + desiredGroup.orElseThrow());
        }
    }
}
