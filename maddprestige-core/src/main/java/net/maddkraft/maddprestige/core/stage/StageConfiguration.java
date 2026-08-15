package net.maddkraft.maddprestige.core.stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.rank.ProjectionPolicy;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;

public record StageConfiguration(
        int schemaVersion,
        boolean active,
        Map<StageId, StageDefinition> stages,
        List<StageId> order,
        Optional<StageId> baselineStage,
        ReconciliationPolicy reconciliationPolicy) {
    public StageConfiguration {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Stage schema version must be positive");
        }
        stages = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(stages, "stages")));
        order = List.copyOf(Objects.requireNonNull(order, "order"));
        baselineStage = Objects.requireNonNull(baselineStage, "baseline stage");
        reconciliationPolicy = Objects.requireNonNull(reconciliationPolicy, "reconciliation policy");
    }

    public static StageConfiguration inactive() {
        return new StageConfiguration(2, false, Map.of(), List.of(), Optional.empty(),
                ReconciliationPolicy.WARN_ONLY);
    }

    public Optional<ProviderId> rankProvider() {
        return stages.values().stream()
                .map(StageDefinition::projection)
                .filter(projection -> projection.policy() == ProjectionPolicy.GROUP)
                .map(StageProjection::providerId)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public Set<String> managedGroups(ProviderId providerId) {
        Objects.requireNonNull(providerId, "provider ID");
        return stages.values().stream()
                .map(StageDefinition::projection)
                .filter(projection -> projection.providerId().filter(providerId::equals).isPresent())
                .map(StageProjection::groupName)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<StageId> stageForGroup(ProviderId providerId, String groupName) {
        return stages.values().stream()
                .filter(StageDefinition::enabled)
                .filter(stage -> stage.projection().providerId().filter(providerId::equals).isPresent())
                .filter(stage -> stage.projection().groupName().filter(groupName::equals).isPresent())
                .map(StageDefinition::id)
                .findFirst();
    }
}
