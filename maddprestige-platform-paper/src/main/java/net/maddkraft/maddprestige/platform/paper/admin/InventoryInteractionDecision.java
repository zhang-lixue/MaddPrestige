package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Objects;

public record InventoryInteractionDecision(boolean cancelEvent, boolean dispatchServerAction, String reason) {
    public InventoryInteractionDecision {
        reason = Objects.requireNonNull(reason, "reason");
    }
}
