package net.maddkraft.maddprestige.api.service;

import java.time.Instant;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.metric.MetricValue;

/**
 * Immutable read-only view of the one active season and the queried player's exact season progress.
 *
 * @param seasonId canonical configured season identity, 1-64 characters
 * @param configRevision exact revision that activated the season
 * @param playerProgress exact non-null player progress value in the season scope
 * @param startedAt non-null persisted season start time
 */
public record SeasonView(
        String seasonId,
        ConfigRevisionId configRevision,
        MetricValue playerProgress,
        Instant startedAt) {
    public SeasonView {
        seasonId = Objects.requireNonNull(seasonId, "season ID");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        playerProgress = Objects.requireNonNull(playerProgress, "player progress");
        startedAt = Objects.requireNonNull(startedAt, "started at");
        if (!seasonId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Season ID is outside stable bounds");
        }
    }
}
