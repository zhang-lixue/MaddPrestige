package net.maddkraft.maddprestige.core.admin.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

/** Immutable display projection. Action authority remains in {@link GuiSessionService}. */
public record GuiDisplayItem(
        int slot,
        GuiItemIcon icon,
        MessageReference title,
        List<MessageReference> lore,
        Optional<UUID> actionId,
        boolean highlighted) {
    public GuiDisplayItem {
        if (slot < 0 || slot >= 54) {
            throw new IllegalArgumentException("GUI item slot must be between zero and fifty-three");
        }
        icon = Objects.requireNonNull(icon, "icon");
        title = Objects.requireNonNull(title, "title");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        actionId = Objects.requireNonNull(actionId, "action ID");
    }

    public static GuiDisplayItem display(
            int slot,
            GuiItemIcon icon,
            MessageReference title,
            List<MessageReference> lore) {
        return new GuiDisplayItem(slot, icon, title, lore, Optional.empty(), false);
    }

    public static GuiDisplayItem action(
            int slot,
            GuiItemIcon icon,
            MessageReference title,
            List<MessageReference> lore,
            UUID actionId,
            boolean highlighted) {
        return new GuiDisplayItem(slot, icon, title, lore, Optional.of(actionId), highlighted);
    }
}
