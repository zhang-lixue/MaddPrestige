package net.maddkraft.maddprestige.platform.paper.event;

import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Stage-era event retained for stable source and binary compatibility. Active numeric Prestige never fires this event:
 * rank-up is blocked before confirmation, dispatch, journaling, projection, or mutation. The immutable snapshot remains
 * valid if a compatibility caller constructs an event directly.
 */
@Stable
public final class PreRankUpEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final OperationEventSnapshot snapshot;
    private boolean cancelled;

    /**
     * Creates one synchronous Paper event.
     *
     * @param snapshot non-null immutable PRE snapshot with no durable operation identity
     */
    public PreRankUpEvent(OperationEventSnapshot snapshot) {
        super(false);
        this.snapshot = Objects.requireNonNull(snapshot, "operation snapshot");
    }

    /** @return non-null immutable request snapshot */
    public OperationEventSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean value) {
        cancelled = value;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
