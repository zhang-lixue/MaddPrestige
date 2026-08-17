package net.maddkraft.maddprestige.core.admin.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

public record StageReferenceSnapshot(
        Map<StageId, Long> counts,
        Optional<StageRemapSnapshot> remap) {
    public StageReferenceSnapshot {
        counts = Map.copyOf(Objects.requireNonNull(counts, "counts"));
        remap = Objects.requireNonNull(remap, "remap");
        counts.forEach((stage, count) -> {
            Objects.requireNonNull(stage, "stage ID");
            if (count == null || count < 0) {
                throw new IllegalArgumentException("Stage reference counts cannot be negative");
            }
        });
    }
}
