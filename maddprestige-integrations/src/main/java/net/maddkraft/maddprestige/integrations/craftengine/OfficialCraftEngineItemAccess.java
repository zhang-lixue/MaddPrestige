package net.maddkraft.maddprestige.integrations.craftengine;

import java.util.Objects;
import java.util.Optional;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Direct adapter over CraftEngine 26.7.4's stable public item API. */
public final class OfficialCraftEngineItemAccess implements CraftEngineItemAccess {
    @Override
    public boolean exists(String itemId) {
        return CraftEngineItems.byId(Key.of(Objects.requireNonNull(itemId, "item ID"))) != null;
    }

    @Override
    public Optional<String> identify(ItemStack stack) {
        if (stack == null || !CraftEngineItems.isCustomItem(stack)) {
            return Optional.empty();
        }
        Key key = CraftEngineItems.getCustomItemId(stack);
        return key == null ? Optional.empty() : Optional.of(key.asString());
    }

    @Override
    public ItemStack build(String itemId, Player player, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("CraftEngine build amount must be positive");
        }
        BukkitItemDefinition definition = CraftEngineItems.byId(Key.of(Objects.requireNonNull(itemId, "item ID")));
        if (definition == null) {
            throw new IllegalArgumentException("CraftEngine item is not loaded: " + itemId);
        }
        ItemBuildContext context = ItemBuildContext.of(BukkitAdaptor.adapt(Objects.requireNonNull(player, "player")));
        return definition.buildBukkitItem(context, amount);
    }
}
