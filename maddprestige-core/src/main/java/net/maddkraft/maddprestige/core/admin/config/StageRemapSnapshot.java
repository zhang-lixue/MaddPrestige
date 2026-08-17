package net.maddkraft.maddprestige.core.admin.config;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;

public record StageRemapSnapshot(
        StageRemapPlan plan,
        List<StageRemapEntry> entries,
        ContentHash seal) {
    public StageRemapSnapshot {
        plan = Objects.requireNonNull(plan, "plan");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        seal = Objects.requireNonNull(seal, "seal");
        if (!seal.equals(seal(plan, entries))) {
            throw new IllegalArgumentException("Stage remap snapshot seal does not match its exact entries");
        }
    }

    public static StageRemapSnapshot create(StageRemapPlan plan, List<StageRemapEntry> entries) {
        return new StageRemapSnapshot(plan, entries, seal(plan, entries));
    }

    public Map<StageId, Long> countsBySource() {
        return entries.stream().collect(java.util.stream.Collectors.groupingBy(StageRemapEntry::sourceStage,
                java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
    }

    private static ContentHash seal(StageRemapPlan plan, List<StageRemapEntry> entries) {
        StringBuilder canonical = new StringBuilder("plan=").append(plan.planRevision()).append('\n');
        plan.mappings().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(StageId::value)))
                .forEach(entry -> canonical.append("map=").append(entry.getKey().value()).append("->")
                        .append(entry.getValue().value()).append('\n'));
        entries.stream().sorted(Comparator.comparing((StageRemapEntry entry) -> entry.sourceStage().value())
                .thenComparing(entry -> entry.playerId().toString())).forEach(entry -> canonical.append("player=")
                        .append(entry.playerId()).append('|').append(entry.sourceStage().value()).append("->")
                        .append(entry.targetStage().value()).append('|').append(entry.expectedStateRevision())
                        .append('|').append(entry.sourceConfigRevision().value()).append('\n'));
        return RevisionHasher.hashText(canonical.toString());
    }
}
