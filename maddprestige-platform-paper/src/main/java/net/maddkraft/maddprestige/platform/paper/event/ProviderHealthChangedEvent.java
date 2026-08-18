package net.maddkraft.maddprestige.platform.paper.event;

import java.util.Objects;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable Paper-server-thread notification fired after the authoritative provider registry caches a semantically
 * different health state. It is observational: listener failure cannot change provider registration, activation, or
 * health. The snapshot remains valid after callback return.
 */
@Stable
public final class ProviderHealthChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ProviderHealthChangedSnapshot snapshot;

    /**
     * Creates one synchronous Paper event.
     *
     * @param snapshot non-null immutable before/after provider-health snapshot
     */
    public ProviderHealthChangedEvent(ProviderHealthChangedSnapshot snapshot) {
        super(false);
        this.snapshot = Objects.requireNonNull(snapshot, "provider health snapshot");
    }

    /** @return non-null immutable provider-health transition snapshot */
    public ProviderHealthChangedSnapshot snapshot() {
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
