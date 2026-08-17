package net.maddkraft.maddprestige.integrations.griefprevention;

import java.util.UUID;

/** Narrow public-API seam around the deployed GriefPrevention data store. */
public interface GriefPreventionAccess {
    ClaimBlockSnapshot read(UUID playerId);

    /** Adds bonus claim blocks, saves synchronously, and returns the verified stored value. */
    int addBonus(UUID playerId, int amount);

    record ClaimBlockSnapshot(int remaining, int accrued, int bonus, int ownedClaims) {
        public ClaimBlockSnapshot {
            if (ownedClaims < 0) {
                throw new IllegalArgumentException("Owned claim count cannot be negative");
            }
        }
    }
}
