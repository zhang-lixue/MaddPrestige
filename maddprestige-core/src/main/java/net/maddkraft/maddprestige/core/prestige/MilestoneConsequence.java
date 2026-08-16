package net.maddkraft.maddprestige.core.prestige;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.RewardId;

public record MilestoneConsequence(
        MilestoneId milestoneId,
        String repeatabilityKey,
        List<RewardId> rewardIds) {
    public MilestoneConsequence {
        milestoneId = Objects.requireNonNull(milestoneId, "milestone ID");
        repeatabilityKey = Objects.requireNonNull(repeatabilityKey, "repeatability key");
        rewardIds = List.copyOf(Objects.requireNonNull(rewardIds, "reward IDs"));
    }
}
