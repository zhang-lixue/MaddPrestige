package net.maddkraft.maddprestige.core.config.progression;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;

public record ProgressionConfiguration(
        int schemaVersion,
        int maximumTreeDepth,
        Map<RequirementId, RequirementDefinition> requirements,
        Map<RequirementId, RequirementNode> trees,
        Map<CostId, CostDefinition> costs,
        Map<RewardId, RewardDefinition> rewards,
        CommandActionPolicy commandPolicy) {
    public ProgressionConfiguration {
        if (schemaVersion < 1 || maximumTreeDepth < 1) {
            throw new IllegalArgumentException("Schema version and maximum requirement depth must be positive");
        }
        requirements = Map.copyOf(Objects.requireNonNull(requirements, "requirements"));
        trees = Map.copyOf(Objects.requireNonNull(trees, "trees"));
        costs = Map.copyOf(Objects.requireNonNull(costs, "costs"));
        rewards = Map.copyOf(Objects.requireNonNull(rewards, "rewards"));
        commandPolicy = Objects.requireNonNull(commandPolicy, "command policy");
    }

    public static ProgressionConfiguration empty() {
        return new ProgressionConfiguration(3, 16, Map.of(), Map.of(), Map.of(), Map.of(),
                CommandActionPolicy.safeDefaults());
    }
}
