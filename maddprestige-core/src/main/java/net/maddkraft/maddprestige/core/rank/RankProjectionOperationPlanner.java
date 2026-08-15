package net.maddkraft.maddprestige.core.rank;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.operation.OperationActionPlan;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfigurationSnapshot;
import net.maddkraft.maddprestige.core.stage.StageDefinition;

public final class RankProjectionOperationPlanner {
    public RankProjectionOperation plan(
            String operationType,
            Actor actor,
            PlayerStageState playerState,
            StageId targetStage,
            StageConfigurationSnapshot configuration,
            ProviderId providerId,
            long providerGeneration,
            String idempotencyKey,
            boolean updateInternalStage) {
        Objects.requireNonNull(configuration, "configuration");
        StageDefinition target = configuration.configuration().stages().get(targetStage);
        if (target == null || !target.enabled() || !configuration.configuration().order().contains(targetStage)) {
            throw new IllegalArgumentException("Target stage is not enabled and ordered: " + targetStage.value());
        }
        if (!configuration.configuration().rankProvider().filter(providerId::equals).isPresent()) {
            throw new IllegalArgumentException("Provider does not own the pinned ladder projection");
        }
        OperationId operationId = OperationId.random();
        var request = new RankProjectionRequest(playerState.playerId(), operationId, configuration.revisionId(),
                providerGeneration, configuration.configuration().managedGroups(providerId),
                target.projection().groupName());
        OperationActionPlan action = new OperationActionPlan("rank-projection", providerId,
                "managed-direct-membership", "Project pinned managed progression membership", false, true);
        OperationPlan plan = new OperationPlan(operationId, Objects.requireNonNull(operationType, "operation type"),
                Objects.requireNonNull(actor, "actor"), playerState.playerId(), playerState.stateRevision(),
                configuration.revisionId(), Map.of(providerId, providerGeneration),
                Objects.requireNonNull(idempotencyKey, "idempotency key"), List.of(action),
                "Project immutable stage '" + targetStage.value() + "' using configuration "
                        + configuration.revisionId().value());
        return new RankProjectionOperation(plan, request, playerState, targetStage, updateInternalStage);
    }
}
