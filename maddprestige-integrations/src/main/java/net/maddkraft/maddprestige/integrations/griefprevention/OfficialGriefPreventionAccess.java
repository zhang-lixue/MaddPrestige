package net.maddkraft.maddprestige.integrations.griefprevention;

import java.util.Objects;
import java.util.UUID;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;

/** Direct adapter over GriefPrevention 16.18.7's public data-store API. */
public final class OfficialGriefPreventionAccess implements GriefPreventionAccess {
    private final DataStore dataStore;

    public OfficialGriefPreventionAccess(GriefPrevention plugin) {
        this.dataStore = Objects.requireNonNull(Objects.requireNonNull(plugin, "plugin").dataStore, "data store");
    }

    @Override
    public ClaimBlockSnapshot read(UUID playerId) {
        PlayerData data = dataStore.getPlayerData(Objects.requireNonNull(playerId, "player ID"));
        return new ClaimBlockSnapshot(data.getRemainingClaimBlocks(), data.getAccruedClaimBlocks(),
                data.getBonusClaimBlocks(), data.getClaims().size());
    }

    @Override
    public int addBonus(UUID playerId, int amount) {
        Objects.requireNonNull(playerId, "player ID");
        if (amount <= 0) {
            throw new IllegalArgumentException("Bonus claim-block reward must be positive");
        }
        PlayerData data = dataStore.getPlayerData(playerId);
        int expected = Math.addExact(data.getBonusClaimBlocks(), amount);
        data.setBonusClaimBlocks(expected);
        dataStore.savePlayerDataSync(playerId, data);
        int observed = dataStore.getPlayerData(playerId).getBonusClaimBlocks();
        if (observed != expected) {
            throw new GriefPreventionVerificationException(expected, observed);
        }
        return observed;
    }

    public static final class GriefPreventionVerificationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private GriefPreventionVerificationException(int expected, int observed) {
            super("GriefPrevention bonus verification mismatch: expected " + expected + " but observed " + observed);
        }
    }
}
