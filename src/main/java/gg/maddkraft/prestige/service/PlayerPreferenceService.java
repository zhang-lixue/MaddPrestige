package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.storage.Database;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Persistent, player-owned presentation preferences for the inventory GUI. */
public final class PlayerPreferenceService {
    private static final String[] PAGES = {"RANK", "PRESTIGE", "REWARDS", "SEASON", "HATTER"};

    private final Database database;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Database.PlayerPreferences> cache = new ConcurrentHashMap<>();

    public PlayerPreferenceService(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public Database.PlayerPreferences get(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadSafely);
    }

    public Database.PlayerPreferences toggleSounds(UUID playerId) {
        Database.PlayerPreferences current = get(playerId);
        return save(playerId, new Database.PlayerPreferences(!current.menuSounds(), current.compactNumbers(),
                current.showIntegrationStatus(), current.defaultPage()));
    }

    public Database.PlayerPreferences toggleCompactNumbers(UUID playerId) {
        Database.PlayerPreferences current = get(playerId);
        return save(playerId, new Database.PlayerPreferences(current.menuSounds(), !current.compactNumbers(),
                current.showIntegrationStatus(), current.defaultPage()));
    }

    public Database.PlayerPreferences toggleIntegrationStatus(UUID playerId) {
        Database.PlayerPreferences current = get(playerId);
        return save(playerId, new Database.PlayerPreferences(current.menuSounds(), current.compactNumbers(),
                !current.showIntegrationStatus(), current.defaultPage()));
    }

    public Database.PlayerPreferences cycleDefaultPage(UUID playerId) {
        Database.PlayerPreferences current = get(playerId);
        String normalized = current.defaultPage().toUpperCase(Locale.ROOT);
        int next = 0;
        for (int index = 0; index < PAGES.length; index++) {
            if (PAGES[index].equals(normalized)) {
                next = (index + 1) % PAGES.length;
                break;
            }
        }
        return save(playerId, new Database.PlayerPreferences(current.menuSounds(), current.compactNumbers(),
                current.showIntegrationStatus(), PAGES[next]));
    }

    public void unload(UUID playerId) {
        cache.remove(playerId);
    }

    private Database.PlayerPreferences save(UUID playerId, Database.PlayerPreferences preferences) {
        try {
            database.savePlayerPreferences(playerId, preferences);
            cache.put(playerId, preferences);
            return preferences;
        } catch (SQLException exception) {
            logger.warning("Could not save GUI preferences for " + playerId + ": " + exception.getMessage());
            return get(playerId);
        }
    }

    private Database.PlayerPreferences loadSafely(UUID playerId) {
        try {
            return database.loadPlayerPreferences(playerId);
        } catch (SQLException exception) {
            logger.warning("Could not load GUI preferences for " + playerId + ": " + exception.getMessage());
            return Database.PlayerPreferences.defaults();
        }
    }
}
