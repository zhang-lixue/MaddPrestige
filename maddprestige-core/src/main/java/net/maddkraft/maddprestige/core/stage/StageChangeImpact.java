package net.maddkraft.maddprestige.core.stage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.SemanticDiff;

public record StageChangeImpact(
        List<StageId> oldOrder,
        List<StageId> newOrder,
        Set<StageId> addedStages,
        Set<StageId> removedStages,
        Set<StageId> enabledStages,
        Set<StageId> disabledStages,
        List<StageProjectionChange> projectionChanges,
        Map<StageId, Long> affectedPlayerReferences,
        boolean explicitRemapRequired,
        SemanticDiff semanticDiff,
        ValidationReport validation) {
    public StageChangeImpact {
        oldOrder = List.copyOf(Objects.requireNonNull(oldOrder, "old order"));
        newOrder = List.copyOf(Objects.requireNonNull(newOrder, "new order"));
        addedStages = Set.copyOf(Objects.requireNonNull(addedStages, "added stages"));
        removedStages = Set.copyOf(Objects.requireNonNull(removedStages, "removed stages"));
        enabledStages = Set.copyOf(Objects.requireNonNull(enabledStages, "enabled stages"));
        disabledStages = Set.copyOf(Objects.requireNonNull(disabledStages, "disabled stages"));
        projectionChanges = List.copyOf(Objects.requireNonNull(projectionChanges, "projection changes"));
        affectedPlayerReferences = Map.copyOf(Objects.requireNonNull(
                affectedPlayerReferences, "affected player references"));
        semanticDiff = Objects.requireNonNull(semanticDiff, "semantic diff");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
