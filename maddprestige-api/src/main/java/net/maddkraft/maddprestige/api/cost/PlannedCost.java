package net.maddkraft.maddprestige.api.cost;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.id.OperationId;

public record PlannedCost(
        OperationId operationId,
        String actionId,
        UUID playerId,
        CostDefinition definition,
        long providerGeneration,
        ActionCharacteristics characteristics,
        String redactedPreview) {
    public PlannedCost {
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
