package net.maddkraft.maddprestige.platform.paper.event;

import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable Paper-server-thread notification fired after one canonical configuration revision is durable and the
 * production runtime has attempted to publish that exact revision. Listener failure is isolated from the completed
 * apply. The snapshot remains valid after callback return.
 */
@Stable
public final class ConfigAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ConfigAppliedSnapshot snapshot;

    /**
     * Creates one synchronous Paper event.
     *
     * @param snapshot non-null immutable applied-configuration snapshot
     */
    public ConfigAppliedEvent(ConfigAppliedSnapshot snapshot) {
        super(false);
        this.snapshot = Objects.requireNonNull(snapshot, "configuration snapshot");
    }

    /** @return non-null immutable applied-configuration snapshot */
    public ConfigAppliedSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** @return this event type's shared synchronous handler list */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
