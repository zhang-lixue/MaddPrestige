package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.Season;
import gg.maddkraft.prestige.model.SeasonStatus;
import gg.maddkraft.prestige.storage.Database;
import gg.maddkraft.prestige.integration.IntegrationRelationshipService;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class SeasonService {
    private final Database database;
    private final ProfileService profiles;
    private final IntegrationRelationshipService relationships;
    private volatile PluginSettings settings;
    private volatile Season current;

    public SeasonService(Database database, ProfileService profiles, IntegrationRelationshipService relationships,
                         PluginSettings settings) throws SQLException {
        this.database = database;
        this.profiles = profiles;
        this.relationships = relationships;
        this.settings = settings;
        this.current = database.ensureActiveSeason(
                settings.season().defaultId(),
                settings.season().defaultName(),
                settings.season().targetLengthDays()
        );
    }

    public Season current() {
        return current;
    }

    public void reload(PluginSettings newSettings) {
        this.settings = newSettings;
    }

    public synchronized SeasonStatus setStatus(SeasonStatus status) throws SQLException {
        if (current.status() == SeasonStatus.CLOSED) throw new IllegalStateException("current chapter is already closed");
        database.updateSeasonStatus(current.id(), status);
        current = current.withStatus(status);
        relationships.trigger("season-status", null, null,
                Map.of("season", current.id(), "season_name", current.displayName(), "status", status.name()));
        return status;
    }

    public synchronized Season beginNewChapter(String id, String displayName) throws SQLException {
        if (current.status() != SeasonStatus.FROZEN && current.status() != SeasonStatus.RESETTING) {
            throw new IllegalStateException("Freeze the current chapter before creating a new one");
        }
        profiles.flushNow();
        database.convertLegacyStars(current.id(), settings.season().legacyStarEveryPrestiges());
        Season created = database.createNewSeason(id, displayName, settings.season().targetLengthDays());
        profiles.beginSeasonForCachedPlayers(created.id(), settings.season().legacyStarEveryPrestiges());
        profiles.flushNow();
        current = created;
        relationships.trigger("season-new", null, null,
                Map.of("season", created.id(), "season_name", created.displayName(), "season_number", created.number()));
        return created;
    }

    public long dayNumber(Instant now) {
        return Math.max(1L, Duration.between(current.startsAt(), now).toDays() + 1L);
    }

    public Duration remaining(Instant now) {
        return now.isAfter(current.endsAt()) ? Duration.ZERO : Duration.between(now, current.endsAt());
    }
}
