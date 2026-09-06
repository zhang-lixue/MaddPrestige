package net.maddkraft.maddprestige.platform.paper.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.persistence.sqlite.SqlitePlayerPrestigeRepository;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceholderSnapshotPublisherTest {
    @Test
    @DisplayName("[OR8F-A76-02] Dormant online ticks are no-ops and live activation initializes normally")
    void dormantTicksDeferUntilLiveActivationWithoutWarningOrSnapshot() {
        Fixture fixture = new Fixture();
        try {
            fixture.start();
            for (int tick = 1; tick <= 6; tick++) {
                fixture.tick();
                int expected = tick;
                await(() -> fixture.initializations.get() == expected);
            }

            assertEquals(0, fixture.cache.size());
            assertEquals(0, fixture.warnings().size());
            verify(fixture.prestiges, never()).find(fixture.playerId);

            fixture.initialization.set(PlaceholderSnapshotPublisher.InitializationResult.ready());
            fixture.publishableState();
            fixture.tick();
            await(() -> fixture.cache.size() == 1);

            assertTrue(fixture.cache.resolve(fixture.playerId, "stage").isEmpty());
            assertEquals("0", fixture.cache.resolve(fixture.playerId, "current_prestige").orElseThrow());
            assertEquals(0, fixture.warnings().size());
        } finally {
            fixture.close();
        }
    }

    @Test
    @DisplayName("[OR8F-A76-02] Active initialization failures remain actionable warnings")
    void activeInitializationFailureStillWarnsAndRemovesStaleSnapshot() {
        Fixture fixture = new Fixture();
        try {
            fixture.cache.publish(fixture.playerId,
                    new MaddPrestigePlaceholderSnapshot("7", "9", "STALE", java.util.Map.of()));
            fixture.initialization.set(PlaceholderSnapshotPublisher.InitializationResult.failed(
                    "Player initialization rank provider is unavailable: luckperms"));
            fixture.publisher.refresh(fixture.playerId);
            await(() -> !fixture.warnings().isEmpty());

            assertEquals(0, fixture.cache.size());
            assertTrue(fixture.warnings().getFirst().contains("rank provider is unavailable: luckperms"));
        } finally {
            fixture.close();
        }
    }

    private static void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        assertTrue(condition.getAsBoolean(), "asynchronous placeholder refresh did not finish");
    }

    private static final class Fixture implements AutoCloseable {
        private final UUID playerId = UUID.randomUUID();
        private final Plugin plugin = mock(Plugin.class);
        private final Server server = mock(Server.class);
        private final PluginManager pluginManager = mock(PluginManager.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final BukkitTask task = mock(BukkitTask.class);
        private final Player player = mock(Player.class);
        private final SqlitePlayerPrestigeRepository prestiges = mock(SqlitePlayerPrestigeRepository.class);
        private final MaddPrestigePlaceholderCache cache = new MaddPrestigePlaceholderCache(10);
        private final AtomicReference<PlaceholderSnapshotPublisher.InitializationResult> initialization =
                new AtomicReference<>(PlaceholderSnapshotPublisher.InitializationResult.dormantResult());
        private final AtomicInteger initializations = new AtomicInteger();
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();
        private final Logger logger = Logger.getLogger("placeholder-publisher-" + playerId);
        private final PlaceholderSnapshotPublisher publisher;
        private Runnable scheduledTick;

        private Fixture() {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(logger);
            when(server.getPluginManager()).thenReturn(pluginManager);
            when(server.getScheduler()).thenReturn(scheduler);
            doReturn(List.of(player)).when(server).getOnlinePlayers();
            when(player.getUniqueId()).thenReturn(playerId);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(20L)))
                    .thenAnswer(invocation -> {
                        scheduledTick = invocation.getArgument(1);
                        return task;
                    });
            publisher = new PlaceholderSnapshotPublisher(plugin, cache, prestiges, ignored -> {
                initializations.incrementAndGet();
                return initialization.get();
            });
        }

        private void start() {
            publisher.start();
            verify(pluginManager).registerEvents(publisher, plugin);
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(20L));
        }

        private void tick() {
            scheduledTick.run();
        }

        private void publishableState() {
            Instant now = Instant.parse("2026-08-28T00:00:00Z");
            ConfigRevisionId revision = new ConfigRevisionId("r_placeholder_active");
            when(prestiges.find(playerId)).thenReturn(Optional.of(new PlayerPrestigeState(playerId,
                    0, 0, 0, revision, new ScopeId("p0"), Optional.empty(), now, now)));
        }

        private List<String> warnings() {
            return records.stream().filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                    .map(LogRecord::getMessage).toList();
        }

        @Override
        public void close() {
            publisher.close();
        }
    }
}
