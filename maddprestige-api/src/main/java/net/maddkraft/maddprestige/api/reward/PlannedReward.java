package net.maddkraft.maddprestige.api.reward;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.id.OperationId;

public record PlannedReward(
        OperationId operationId,
        String actionId,
        UUID playerId,
        RewardDefinition definition,
        long providerGeneration,
        ActionCharacteristics characteristics,
        String redactedPreview) {
    public PlannedReward {
        operationId = Objects.requireNonNull(operationId, "operation ID");
        actionId = Objects.requireNonNull(actionId, "action ID");
        playerId = Objects.requireNonNull(playerId, "player ID");
        definition = Objects.requireNonNull(definition, "definition");
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
        characteristics = Objects.requireNonNull(characteristics, "characteristics");
        redactedPreview = Objects.requireNonNull(redactedPreview, "preview");
    }
}
