package net.maddkraft.maddprestige.core.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.core.requirement.BoundRequirementEvaluation;
import net.maddkraft.maddprestige.core.requirement.RequirementEvaluationResult;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;

/** Low-level proposal. Public caller-composed instances are deliberately never authoritative. */
public final class RankUpPlanningRequest {
    private final Actor actor;
    private final UUID playerId;
    private final PlayerStageState playerState;
    private final StageDefinition targetStage;
    private final ConfigRevisionId configRevision;
    private final Map<ProviderId, Long> pinnedProviderGenerations;
    private final RequirementEvaluationResult requirements;
    private final Optional<BoundRequirementEvaluation> boundRequirements;
    private final List<CostDefinition> costs;
    private final List<RewardDefinition> rewards;
    private final String idempotencyKey;
    private final Optional<StageConfiguration> canonicalStages;
    private final boolean canonical;

    public RankUpPlanningRequest(
            Actor actor,
            UUID playerId,
            PlayerStageState playerState,
            StageDefinition targetStage,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> pinnedProviderGenerations,
            RequirementEvaluationResult requirements,
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            String idempotencyKey) {
        this(actor, playerId, playerState, targetStage, configRevision, pinnedProviderGenerations, requirements,
                Optional.empty(), costs, rewards, idempotencyKey, Optional.empty(), false);
    }

    static RankUpPlanningRequest canonical(
            Actor actor,
            UUID playerId,
            PlayerStageState playerState,
            StageDefinition targetStage,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> pinnedProviderGenerations,
            BoundRequirementEvaluation requirements,
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            String idempotencyKey,
            StageConfiguration stages) {
        return new RankUpPlanningRequest(actor, playerId, playerState, targetStage, configRevision,
                pinnedProviderGenerations, requirements.result(), Optional.of(requirements), costs, rewards,
                idempotencyKey, Optional.of(stages), true);
    }

    private RankUpPlanningRequest(
            Actor actor,
            UUID playerId,
            PlayerStageState playerState,
            StageDefinition targetStage,
            ConfigRevisionId configRevision,
            Map<ProviderId, Long> pinnedProviderGenerations,
            RequirementEvaluationResult requirements,
            Optional<BoundRequirementEvaluation> boundRequirements,
            List<CostDefinition> costs,
            List<RewardDefinition> rewards,
            String idempotencyKey,
            Optional<StageConfiguration> canonicalStages,
            boolean canonical) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.playerId = Objects.requireNonNull(playerId, "player ID");
        this.playerState = Objects.requireNonNull(playerState, "player state");
        this.targetStage = Objects.requireNonNull(targetStage, "target stage");
        this.configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        this.pinnedProviderGenerations = Map.copyOf(Objects.requireNonNull(pinnedProviderGenerations,
                "pinned provider generations"));
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.boundRequirements = Objects.requireNonNull(boundRequirements, "bound requirements");
        this.costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
        this.rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key");
        this.canonicalStages = Objects.requireNonNull(canonicalStages, "canonical stages");
        this.canonical = canonical;
        if (!playerId.equals(playerState.playerId())) {
            throw new IllegalArgumentException("Planning player and player state UUID differ");
        }
    }

    public Actor actor() { return actor; }
    public UUID playerId() { return playerId; }
    public PlayerStageState playerState() { return playerState; }
    public StageDefinition targetStage() { return targetStage; }
    public ConfigRevisionId configRevision() { return configRevision; }
    public Map<ProviderId, Long> pinnedProviderGenerations() { return pinnedProviderGenerations; }
    public RequirementEvaluationResult requirements() { return requirements; }
    public Optional<BoundRequirementEvaluation> boundRequirements() { return boundRequirements; }
    public List<CostDefinition> costs() { return costs; }
    public List<RewardDefinition> rewards() { return rewards; }
    public String idempotencyKey() { return idempotencyKey; }
    public Optional<StageConfiguration> canonicalStages() { return canonicalStages; }
    boolean canonical() { return canonical; }
}
