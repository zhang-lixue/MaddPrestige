package net.maddkraft.maddprestige.core.stage;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageRemapPlan(String planRevision, Map<StageId, StageId> mappings) {
    public StageRemapPlan {
        planRevision = Objects.requireNonNull(planRevision, "plan revision");
        mappings = Map.copyOf(Objects.requireNonNull(mappings, "mappings"));
        if (planRevision.isBlank()) {
            throw new IllegalArgumentException("Remap plan revision cannot be blank");
        }
    }
}
