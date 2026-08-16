package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageDefinition(
        StageId id,
        boolean enabled,
        String displayName,
        Map<String, String> displayMetadata,
        StageProjection projection,
        Optional<RequirementId> requirementTreeId,
        List<CostId> costIds,
        List<RewardId> rewardIds) {
    public StageDefinition {
        id = Objects.requireNonNull(id, "stage ID");
        displayName = Objects.requireNonNull(displayName, "display name");
        displayMetadata = Map.copyOf(Objects.requireNonNull(displayMetadata, "display metadata"));
        projection = Objects.requireNonNull(projection, "projection");
        requirementTreeId = Objects.requireNonNull(requirementTreeId, "requirement tree ID");
        costIds = List.copyOf(Objects.requireNonNull(costIds, "cost IDs"));
        rewardIds = List.copyOf(Objects.requireNonNull(rewardIds, "reward IDs"));
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Stage display name cannot be blank");
        }
    }

    public StageDefinition(
            StageId id,
            boolean enabled,
            String displayName,
            Map<String, String> displayMetadata,
            StageProjection projection) {
        this(id, enabled, displayName, displayMetadata, projection, Optional.empty(), List.of(), List.of());
    }
}
