package net.maddkraft.maddprestige.core.milestone;

import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MilestoneId;

@FunctionalInterface
public interface MilestoneStateReader {
    boolean awarded(UUID playerId, MilestoneId milestoneId, String repeatabilityKey);
}
