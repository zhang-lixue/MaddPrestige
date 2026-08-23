package net.maddkraft.maddprestige.platform.paper.placeholder;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.stage.PlayerStageState;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerStageRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Periodically materializes canonical player state into the bounded PlaceholderAPI output cache. */
public final class PlaceholderSnapshotPublisher implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final MaddPrestigePlaceholderCache cache;
    private final SqlitePlayerStageRepository stages;
    private final SqlitePlayerPrestigeRepository prestiges;
    private final Function<UUID, java.util.Optional<String>> playerInitializer;
    private final ExecutorService worker;
    private final Set<UUID> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BukkitTask refreshTask;

    public PlaceholderSnapshotPublisher(
            Plugin plugin,
            MaddPrestigePlaceholderCache cache,
            SqlitePlayerStageRepository stages,
            SqlitePlayerPrestigeRepository prestiges,
            Function<UUID, java.util.Optional<String>> playerInitializer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.cache = Objects.requireNonNull(cache, "placeholder cache");
        this.stages = Objects.requireNonNull(stages, "stage repository");
        this.prestiges = Objects.requireNonNull(prestiges, "Prestige repository");
        this.playerInitializer = Objects.requireNonNull(playerInitializer, "player initializer");
        worker = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("maddprestige-placeholder-publisher-", 0).factory());
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> plugin.getServer()
                .getOnlinePlayers().forEach(player -> refresh(player.getUniqueId())), 1L, 20L);
    }

    /** Requests a coalesced refresh without blocking the caller. */
    public void refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "player ID");
        if (!inFlight.add(playerId)) {
            return;
        }
        worker.execute(() -> {
            try {
                java.util.Optional<String> initializationFailure = playerInitializer.apply(playerId);
                if (initializationFailure.isPresent()) {
                    cache.remove(playerId);
                    plugin.getLogger().warning("Placeholder snapshot initialization failed safely: "
                            + initializationFailure.orElseThrow());
                    return;
                }
                PlayerStageState stage = stages.find(playerId).orElse(null);
                PlayerPrestigeState prestige = prestiges.find(playerId).orElse(null);
                if (stage == null && prestige == null) {
                    cache.remove(playerId);
                    return;
                }
                cache.publish(playerId, new MaddPrestigePlaceholderSnapshot(
                        stage == null ? "" : stage.stageId().value(),
                        prestige == null ? "0" : Long.toString(prestige.currentPrestige()),
                        prestige == null ? "0" : Long.toString(prestige.lifetimePrestige()),
                        "MATERIALIZED", Map.of()));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Placeholder snapshot refresh failed safely", failure);
            } finally {
                inFlight.remove(playerId);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(3, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }
}
