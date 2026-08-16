package net.maddkraft.maddprestige.integrations.mcmmo;

import java.util.UUID;

/** Narrow read-only seam over mcMMO's official ExperienceAPI. */
public interface McMmoExperienceAccess {
    boolean validSkill(String skill);

    int level(UUID playerId, String skill);

    int powerLevel(UUID playerId);
}
