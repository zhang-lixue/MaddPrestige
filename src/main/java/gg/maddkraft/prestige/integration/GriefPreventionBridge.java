package gg.maddkraft.prestige.integration;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class GriefPreventionBridge {
    private final JavaPlugin owner;
    private final boolean enabled;

    public GriefPreventionBridge(JavaPlugin owner, boolean enabled) {
        this.owner = owner;
        this.enabled = enabled;
    }

    public boolean available() {
        return enabled && owner.getServer().getPluginManager().isPluginEnabled("GriefPrevention");
    }

    public boolean addBonusClaimBlocks(UUID playerId, int amount) {
        if (amount == 0 || !available()) return amount == 0;
        try {
            Plugin plugin = owner.getServer().getPluginManager().getPlugin("GriefPrevention");
            Field dataStoreField = plugin.getClass().getField("dataStore");
            Object dataStore = dataStoreField.get(plugin);
            Method getPlayerData = dataStore.getClass().getMethod("getPlayerData", UUID.class);
            Object playerData = getPlayerData.invoke(dataStore, playerId);
            Method getBonus = playerData.getClass().getMethod("getBonusClaimBlocks");
            Method setBonus = playerData.getClass().getMethod("setBonusClaimBlocks", Integer.class);
            int current = ((Number) getBonus.invoke(playerData)).intValue();
            setBonus.invoke(playerData, Math.max(0, current + amount));
            Method save = dataStore.getClass().getMethod("savePlayerData", UUID.class, playerData.getClass());
            save.invoke(dataStore, playerId, playerData);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            owner.getLogger().warning("GriefPrevention claim-block integration failed: " + exception.getMessage());
            return false;
        }
    }
}
