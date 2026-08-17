package net.maddkraft.maddprestige.core.stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageRemapPlan(String planRevision, Map<StageId, StageId> mappings) {
    public StageRemapPlan {
        planRevision = Objects.requireNonNull(planRevision, "plan revision");
        LinkedHashMap<StageId, StageId> validated = new LinkedHashMap<>();
        Objects.requireNonNull(mappings, "mappings").forEach((source, target) -> {
            Objects.requireNonNull(source, "remap source");
            Objects.requireNonNull(target, "remap target");
            if (source.equals(target)) {
                throw new IllegalArgumentException("A stage remap cannot map a stage to itself");
            }
            validated.put(source, target);
        });
        mappings = Collections.unmodifiableMap(validated);
        if (planRevision.isBlank()) {
            throw new IllegalArgumentException("Remap plan revision cannot be blank");
        }
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("Remap plan must contain at least one source mapping");
        }
    }

    public StageRemapPlan withMapping(String replacementRevision, StageId source, StageId target) {
        LinkedHashMap<StageId, StageId> replacement = new LinkedHashMap<>(mappings);
        replacement.put(source, target);
        return new StageRemapPlan(replacementRevision, replacement);
    }

    public Map<StageId, StageId> withoutMapping(StageId source) {
        LinkedHashMap<StageId, StageId> replacement = new LinkedHashMap<>(mappings);
        replacement.remove(Objects.requireNonNull(source, "remap source"));
        return Collections.unmodifiableMap(replacement);
    }
}
