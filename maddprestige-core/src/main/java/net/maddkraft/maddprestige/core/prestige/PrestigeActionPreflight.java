package net.maddkraft.maddprestige.core.prestige;

import java.util.List;
import java.util.Set;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.reward.PlannedReward;

record PrestigeActionPreflight(
        List<PlannedCost> costs,
        List<PlannedReward> rewards,
        List<String> blockers,
        Set<ProviderId> unavailableProviders) {
    PrestigeActionPreflight {
        costs = List.copyOf(costs);
        rewards = List.copyOf(rewards);
        blockers = List.copyOf(blockers);
        unavailableProviders = Set.copyOf(unavailableProviders);
    }
}
