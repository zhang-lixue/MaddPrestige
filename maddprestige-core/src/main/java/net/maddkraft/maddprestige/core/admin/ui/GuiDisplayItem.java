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
        boolean highlighted,
        Optional<UUID> profilePlayerId) {
    public GuiDisplayItem {
        if (slot < 0 || slot >= 54) {
            throw new IllegalArgumentException("GUI item slot must be between zero and fifty-three");
        }
        icon = Objects.requireNonNull(icon, "icon");
        title = Objects.requireNonNull(title, "title");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        actionId = Objects.requireNonNull(actionId, "action ID");
        profilePlayerId = Objects.requireNonNull(profilePlayerId, "profile player ID");
    }

    public GuiDisplayItem(
            int slot,
            GuiItemIcon icon,
            MessageReference title,
            List<MessageReference> lore,
            Optional<UUID> actionId,
            boolean highlighted) {
        this(slot, icon, title, lore, actionId, highlighted, Optional.empty());
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

    public static GuiDisplayItem profiledDisplay(
            int slot,
            GuiItemIcon icon,
            MessageReference title,
            List<MessageReference> lore,
            UUID profilePlayerId) {
        return new GuiDisplayItem(slot, icon, title, lore, Optional.empty(), false,
                Optional.of(profilePlayerId));
    }

    public static GuiDisplayItem profiledAction(
            int slot,
            GuiItemIcon icon,
            MessageReference title,
            List<MessageReference> lore,
            UUID actionId,
            boolean highlighted,
            UUID profilePlayerId) {
        return new GuiDisplayItem(slot, icon, title, lore, Optional.of(actionId), highlighted,
                Optional.of(profilePlayerId));
    }
}
