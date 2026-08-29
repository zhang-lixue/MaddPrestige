package net.maddkraft.maddprestige.platform.paper.event;

import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Stage-era event retained for stable source and binary compatibility. Active numeric Prestige never fires this event
 * because no durable rank-up operation can be created. Direct compatibility instances are immutable notifications only.
 */
@Stable
public final class PostRankUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final OperationEventSnapshot snapshot;

    /**
     * Creates one synchronous Paper event.
     *
     * @param snapshot non-null immutable POST snapshot with a durable operation identity and terminal status
     */
    public PostRankUpEvent(OperationEventSnapshot snapshot) {
        super(false);
        this.snapshot = Objects.requireNonNull(snapshot, "operation snapshot");
    }

    /** @return non-null immutable terminal snapshot */
    public OperationEventSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
