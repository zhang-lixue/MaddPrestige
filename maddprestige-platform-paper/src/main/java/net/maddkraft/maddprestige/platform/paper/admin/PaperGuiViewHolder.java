package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.ui.GuiAudience;
import org.bukkit.inventory.InventoryHolder;

/** Common authority-bearing holder for ordinary and typed-input MaddPrestige views. */
interface PaperGuiViewHolder extends InventoryHolder {
    UUID sessionId();

    GuiAudience audience();

    Optional<UUID> actionAt(int rawSlot);
}
