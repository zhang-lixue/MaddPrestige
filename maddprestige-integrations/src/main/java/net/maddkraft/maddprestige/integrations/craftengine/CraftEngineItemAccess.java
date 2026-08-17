package net.maddkraft.maddprestige.integrations.craftengine;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Narrow stable public-API seam for CraftEngine custom-item identity and construction. */
public interface CraftEngineItemAccess {
    boolean exists(String itemId);

    Optional<String> identify(ItemStack stack);

    ItemStack build(String itemId, Player player, int amount);
}
