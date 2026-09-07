package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;
import net.maddkraft.maddprestige.core.admin.ui.GuiAudience;
import net.maddkraft.maddprestige.platform.paper.PaperThreadGuard;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

/** Server-owned slot/action map. Item names and metadata never carry authority. */
public final class PaperGuiInventory implements PaperGuiViewHolder {
    private final UUID sessionId;
    private final GuiAudience audience;
    private final Map<Integer, UUID> actionIds;
    private final Inventory inventory;

    public PaperGuiInventory(GuiSessionView view, PaperMessageService messages) {
        PaperThreadGuard.requireServerThread("Create MaddPrestige GUI");
        java.util.Objects.requireNonNull(messages, "messages");
        sessionId = view.sessionId();
        audience = view.audience();
        int size = view.inventorySize();
        inventory = Bukkit.createInventory(this, size, renderTitle(view, messages));
        LinkedHashMap<Integer, UUID> slots = new LinkedHashMap<>();
        if (view.items().isEmpty()) {
            renderLegacy(view, messages, size, slots);
        } else {
            for (var display : view.items()) {
                inventory.setItem(display.slot(), renderDisplayItem(display, messages));
                display.actionId().ifPresent(action -> slots.put(display.slot(), action));
            }
        }
        actionIds = Map.copyOf(slots);
    }

    static ItemStack renderDisplayItem(
            net.maddkraft.maddprestige.core.admin.ui.GuiDisplayItem display,
            PaperMessageService messages) {
        ItemStack item = new ItemStack(material(display.icon()));
        item.editMeta(meta -> {
            applyPlayerProfile(meta, display.profilePlayerId());
            meta.displayName(nonItalic(messages.render(display.title())));
            if (!display.lore().isEmpty()) {
                meta.lore(display.lore().stream().map(messages::render)
                        .map(PaperGuiInventory::nonItalic).toList());
            }
            if (display.highlighted()) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return item;
    }

    private static void applyPlayerProfile(ItemMeta meta, java.util.Optional<UUID> playerId) {
        if (!(meta instanceof SkullMeta skull) || playerId.isEmpty()) {
            return;
        }
        try {
            var player = Bukkit.getPlayer(playerId.orElseThrow());
            if (player != null && player.isOnline()) {
                skull.setPlayerProfile(player.getPlayerProfile());
            }
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // A normal player head remains usable when a platform cannot resolve the live profile.
        }
    }

    private void renderLegacy(
            GuiSessionView view,
            PaperMessageService messages,
            int size,
            Map<Integer, UUID> slots) {
        for (int slot = 0; slot < view.actions().size() && slot < size; slot++) {
            var action = view.actions().get(slot);
            ItemStack item = new ItemStack(action.mutating() ? Material.REDSTONE_TORCH : Material.PAPER);
            item.editMeta(meta -> meta.displayName(nonItalic(renderActionLabel(action, messages))));
            inventory.setItem(slot, item);
            slots.put(slot, action.actionId());
        }
    }

    static Material material(net.maddkraft.maddprestige.core.admin.ui.GuiItemIcon icon) {
        return switch (icon) {
            case PROGRESS -> Material.CLOCK;
            case REFRESH -> Material.COMPASS;
            case PRESTIGE -> Material.NETHER_STAR;
            case READY -> Material.LIME_DYE;
            case BLOCKED -> Material.RED_CONCRETE;
            case REQUIREMENTS -> Material.WRITABLE_BOOK;
            case BALANCE -> Material.GOLD_INGOT;
            case COST -> Material.GOLD_INGOT;
            case REWARD -> Material.CHEST;
            case MILESTONE -> Material.BEACON;
            case CONFIRM -> Material.LIME_CONCRETE;
            case STAFF -> Material.COMPASS;
            case OVERVIEW -> Material.SPYGLASS;
            case PLAYERS -> Material.PLAYER_HEAD;
            case PLAYER_INFORMATION -> Material.NAME_TAG;
            case CONFIGURATION -> Material.KNOWLEDGE_BOOK;
            case HISTORY -> Material.BOOK;
            case SYSTEM_STATUS -> Material.COMPARATOR;
            case ADMINISTRATION -> Material.ANVIL;
            case INCREASE -> Material.EMERALD;
            case DECREASE -> Material.REDSTONE;
            case BACK -> Material.ARROW;
            case CLOSE -> Material.BARRIER;
            case BORDER_PURPLE -> Material.PURPLE_STAINED_GLASS_PANE;
            case BORDER_AQUA -> Material.CYAN_STAINED_GLASS_PANE;
        };
    }

    private static Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public GuiAudience audience() {
        return audience;
    }

    public static net.kyori.adventure.text.Component renderTitle(
            GuiSessionView view,
            PaperMessageService messages) {
        return messages.render(java.util.Objects.requireNonNull(view, "GUI view").title());
    }

    public static net.kyori.adventure.text.Component renderActionLabel(
            net.maddkraft.maddprestige.core.admin.ui.GuiAction action,
            PaperMessageService messages) {
        return messages.render(java.util.Objects.requireNonNull(action, "GUI action").label());
    }

    public java.util.Optional<UUID> actionAt(int rawSlot) {
        return java.util.Optional.ofNullable(actionIds.get(rawSlot));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
