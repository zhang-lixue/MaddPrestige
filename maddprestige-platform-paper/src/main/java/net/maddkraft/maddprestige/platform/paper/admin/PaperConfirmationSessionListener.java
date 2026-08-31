package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Exact Paper login lifecycle boundary for in-memory player confirmation authority. */
public final class PaperConfirmationSessionListener implements Listener {
    private final Consumer<UUID> begin;
    private final Consumer<UUID> end;

    public PaperConfirmationSessionListener(Consumer<UUID> begin, Consumer<UUID> end) {
        this.begin = Objects.requireNonNull(begin, "session start");
        this.end = Objects.requireNonNull(end, "session end");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        begin.accept(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        end.accept(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        end.accept(event.getPlayer().getUniqueId());
    }
}
