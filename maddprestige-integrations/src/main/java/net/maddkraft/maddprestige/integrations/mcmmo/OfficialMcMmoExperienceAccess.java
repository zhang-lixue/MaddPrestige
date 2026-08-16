package net.maddkraft.maddprestige.integrations.mcmmo;

import com.gmail.nossr50.api.ExperienceAPI;
import java.util.UUID;

/** Direct compile-time binding to mcMMO 2.2's supported public API. */
public final class OfficialMcMmoExperienceAccess implements McMmoExperienceAccess {
    @Override
    public boolean validSkill(String skill) {
        return ExperienceAPI.isValidSkillType(skill);
    }

    @Override
    public int level(UUID playerId, String skill) {
        return ExperienceAPI.getLevelOffline(playerId, skill);
    }

    @Override
    public int powerLevel(UUID playerId) {
        return ExperienceAPI.getPowerLevelOffline(playerId);
    }
}
