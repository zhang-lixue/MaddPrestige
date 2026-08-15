package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.HatterHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class HatterItemService {
    private static final String TYPE_VALUE = "maddhatter_hat";

    private final NamespacedKey typeKey;
    private final NamespacedKey uniqueIdKey;
    private final NamespacedKey ownerKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile PluginSettings settings;

    public HatterItemService(JavaPlugin plugin, PluginSettings settings) {
        this.typeKey = new NamespacedKey(plugin, "item_type");
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");
        this.ownerKey = new NamespacedKey(plugin, "holder_uuid");
        this.settings = settings;
    }

    public void reload(PluginSettings settings) {
        this.settings = settings;
    }

    public ItemStack create(HatterHolder holder) {
        ItemStack item = new ItemStack(settings.hatter().hatMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize("<gradient:#e6b94e:#8060f2><bold>The MaddHatter's Top Hat</bold></gradient>"));
        meta.lore(List.of(
                miniMessage.deserialize("<gray>There is only one authentic hat.</gray>"),
                miniMessage.deserialize("<dark_gray>Held since " + holder.since().toString().substring(0, 10) + "</dark_gray>"),
                miniMessage.deserialize("<gold>Cosmetic - Soulbound - Unique</gold>")
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, TYPE_VALUE);
        meta.getPersistentDataContainer().set(uniqueIdKey, PersistentDataType.STRING, holder.hatItemId());
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, holder.playerId().toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHatterItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return TYPE_VALUE.equals(item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING));
    }

    public boolean isAuthentic(ItemStack item, HatterHolder holder) {
        if (!isHatterItem(item) || holder == null) return false;
        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(uniqueIdKey, PersistentDataType.STRING);
        String ownerId = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return holder.hatItemId().equals(itemId) && holder.playerId().toString().equals(ownerId);
    }

    public ItemStack makeReplica(ItemStack source) {
        ItemStack replica = source.clone();
        ItemMeta meta = replica.getItemMeta();
        meta.getPersistentDataContainer().remove(uniqueIdKey);
        meta.getPersistentDataContainer().remove(ownerKey);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, "former_hatter_replica");
        meta.displayName(miniMessage.deserialize("<gray><bold>Former MaddHatter's Replica</bold></gray>"));
        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize("<dark_gray>A commemorative replica of a past reign.</dark_gray>"));
        lore.add(miniMessage.deserialize("<gray>Cosmetic - Non-unique</gray>"));
        meta.lore(lore);
        replica.setItemMeta(meta);
        return replica;
    }

    public void reconcile(Player player, HatterHolder holder) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        boolean authenticFound = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isHatterItem(item)) continue;
            boolean authentic = holder != null && isAuthentic(item, holder)
                    && player.getUniqueId().equals(holder.playerId()) && !authenticFound;
            if (authentic) authenticFound = true;
            else {
                contents[slot] = makeReplica(item);
                changed = true;
            }
        }
        if (changed) player.getInventory().setContents(contents);
    }

    public ActionResult claim(Player player, HatterHolder holder) {
        if (holder == null || !holder.playerId().equals(player.getUniqueId())) {
            return ActionResult.fail("Only the reigning MaddHatter can claim the authentic Top Hat.");
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isAuthentic(item, holder)) return ActionResult.fail("You already possess the authentic Top Hat.");
        }
        if (player.getInventory().firstEmpty() < 0) return ActionResult.fail("Make one empty inventory slot before claiming the hat.");
        player.getInventory().addItem(create(holder));
        return ActionResult.ok("The authentic MaddHatter's Top Hat has been returned to you.");
    }
}
