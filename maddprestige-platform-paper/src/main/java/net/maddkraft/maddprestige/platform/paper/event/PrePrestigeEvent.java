package net.maddkraft.maddprestige.platform.paper.event;

import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable Paper-server-thread event fired after Prestige authorization and before post-PRE revalidation,
 * unknown-player materialization, leases, journaling, or any consequential effect. The immutable snapshot remains
 * valid after callback return; this event object is owned by the dispatch and only its cancellation flag is mutable.
 */
@Stable
public final class PrePrestigeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final OperationEventSnapshot snapshot;
    private boolean cancelled;

    /**
     * Creates one synchronous Paper event.
     *
     * @param snapshot non-null immutable PRE snapshot with no durable operation identity
     */
    public PrePrestigeEvent(OperationEventSnapshot snapshot) {
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
