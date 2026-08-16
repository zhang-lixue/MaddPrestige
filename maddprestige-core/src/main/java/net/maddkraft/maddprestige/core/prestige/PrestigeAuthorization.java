package net.maddkraft.maddprestige.core.prestige;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.OperationPlan;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.reward.PlannedReward;

/** Opaque authority binding every execution-consequential Prestige plan component. */
public final class PrestigeAuthorization {
    private final OperationId operationId;
    private final UUID playerId;
    private final long expectedStageRevision;
    private final long expectedPrestigeRevision;
    private final ConfigRevisionId configRevision;
    private final Map<ProviderId, Long> providerGenerations;
    private final PrestigeSimulation simulation;
    private final List<PlannedCost> costs;
    private final List<PlannedReward> rewards;
    private final Optional<ProviderId> rankProviderId;
    private final Optional<RankProjectionRequest> projection;
    private final OperationPlan operationPlan;
    private final boolean issued;

    PrestigeAuthorization(
            OperationId operationId,
            UUID playerId,
            long expectedStageRevision,
            long expectedPrestigeRevision,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> providerGenerations,
            PrestigeSimulation simulation,
            List<PlannedCost> costs,
            List<PlannedReward> rewards,
            Optional<ProviderId> rankProviderId,
            Optional<RankProjectionRequest> projection,
            OperationPlan operationPlan) {
        this.operationId = Objects.requireNonNull(operationId, "operation ID");
        this.playerId = Objects.requireNonNull(playerId, "player ID");
        this.expectedStageRevision = expectedStageRevision;
        this.expectedPrestigeRevision = expectedPrestigeRevision;
        this.configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        this.providerGenerations = Map.copyOf(providerGenerations);
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.costs = List.copyOf(costs);
        this.rewards = List.copyOf(rewards);
        this.rankProviderId = Objects.requireNonNull(rankProviderId, "rank provider ID");
        this.projection = Objects.requireNonNull(projection, "rank projection");
        this.operationPlan = Objects.requireNonNull(operationPlan, "operation plan");
        issued = true;
    }

    private PrestigeAuthorization() {
        operationId = null;
        playerId = null;
        expectedStageRevision = -1;
        expectedPrestigeRevision = -1;
        configRevision = null;
        providerGenerations = Map.of();
        simulation = null;
        costs = List.of();
        rewards = List.of();
        rankProviderId = Optional.empty();
        projection = Optional.empty();
        operationPlan = null;
        issued = false;
    }

    static PrestigeAuthorization denied() {
        return new PrestigeAuthorization();
    }

    public boolean matches(PrestigePlan plan) {
        return issued && operationId.equals(plan.operationId()) && playerId.equals(plan.playerId())
                && expectedStageRevision == plan.expectedStageRevision()
                && expectedPrestigeRevision == plan.expectedPrestigeRevision()
                && configRevision.equals(plan.configRevision())
                && providerGenerations.equals(plan.providerGenerations())
                && simulation.equals(plan.simulation()) && costs.equals(plan.costs())
                && rewards.equals(plan.rewards()) && rankProviderId.equals(plan.rankProviderId())
                && projection.equals(plan.rankProjectionRequest())
                && operationPlan.equals(plan.operationPlan()) && plan.executionAllowed() && plan.blockers().isEmpty();
    }
}
