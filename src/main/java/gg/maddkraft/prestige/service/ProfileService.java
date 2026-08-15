package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.storage.Database;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileService implements AutoCloseable {
    private final Database database;
    private final Logger logger;
    private final ExecutorService databaseExecutor;
    private final ConcurrentHashMap<UUID, PlayerState> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerState>> loading = new ConcurrentHashMap<>();

    public ProfileService(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MaddPrestige-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<PlayerState> load(UUID playerId, String seasonId) {
        PlayerState cached = cache.get(playerId);
        if (cached != null && cached.seasonId().equals(seasonId)) return CompletableFuture.completedFuture(cached);
        return loading.computeIfAbsent(playerId, ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                PlayerState state = database.loadPlayer(playerId, seasonId);
                cache.put(playerId, state);
                return state;
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not load player " + playerId, exception);
            }
        }, databaseExecutor).whenComplete((state, error) -> loading.remove(playerId)));
    }

    public Optional<PlayerState> get(UUID playerId) {
        return Optional.ofNullable(cache.get(playerId));
    }

    public boolean isLoading(UUID playerId) {
        return loading.containsKey(playerId);
    }

    public Collection<PlayerState> cachedStates() {
        return ListSnapshot.copy(cache.values());
    }

    public void markSeen(UUID playerId) {
        PlayerState state = cache.get(playerId);
        if (state != null) state.touch(Instant.now());
    }

    public CompletableFuture<Void> flushDirtyAsync() {
        Collection<PlayerState> snapshots = new ArrayList<>();
        for (PlayerState state : cache.values()) {
            if (state.consumeDirty()) snapshots.add(state.snapshot());
        }
        if (snapshots.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            for (PlayerState state : snapshots) {
                try {
                    database.savePlayer(state);
                } catch (SQLException exception) {
                    logger.log(Level.SEVERE, "Could not save profile " + state.playerId(), exception);
                    cache.computeIfPresent(state.playerId(), (uuid, current) -> {
                        current.touch(current.lastSeenAt());
                        return current;
                    });
                }
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> saveAsync(PlayerState state) {
        PlayerState snapshot = state.snapshot();
        return CompletableFuture.runAsync(() -> {
            try {
                database.savePlayer(snapshot);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not save player " + snapshot.playerId(), exception);
            }
        }, databaseExecutor);
    }

    public void saveNow(PlayerState state) throws SQLException {
        database.savePlayer(state);
        state.consumeDirty();
    }

    public void unload(UUID playerId) {
        PlayerState state = cache.remove(playerId);
        if (state == null) return;
        state.touch(Instant.now());
        saveAsync(state).exceptionally(error -> {
            logger.log(Level.SEVERE, "Could not save player during unload " + playerId, error);
            cache.putIfAbsent(playerId, state);
            return null;
        });
    }

    public void beginSeasonForCachedPlayers(String newSeasonId, int legacyStarEvery) {
        for (PlayerState state : cache.values()) {
            int stars = ProgressionMath.legacyStars(state.prestigeLevel(), legacyStarEvery);
            state.addLegacyStars(stars);
            state.beginSeason(newSeasonId);
        }
    }

    public void flushNow() throws SQLException {
        for (PlayerState state : cache.values()) database.savePlayer(state);
    }

    @Override
    public void close() {
        try {
            flushNow();
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Could not flush all MaddPrestige profiles", exception);
        }
        databaseExecutor.shutdown();
    }

    private static final class ListSnapshot {
        private static <T> Collection<T> copy(Collection<T> values) {
            return List.copyOf(values);
        }
    }
}
