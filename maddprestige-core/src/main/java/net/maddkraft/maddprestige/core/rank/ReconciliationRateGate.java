package net.maddkraft.maddprestige.core.rank;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ReconciliationRateGate {
    private final Duration minimumInterval;
    private final int maximumTrackedPlayers;
    private final LinkedHashMap<UUID, Instant> lastAttempts = new LinkedHashMap<>(16, 0.75f, true);

    public ReconciliationRateGate(Duration minimumInterval, int maximumTrackedPlayers) {
        this.minimumInterval = Objects.requireNonNull(minimumInterval, "minimum interval");
        if (minimumInterval.isNegative() || minimumInterval.isZero()) {
            throw new IllegalArgumentException("Minimum interval must be positive");
        }
        if (maximumTrackedPlayers < 1) {
            throw new IllegalArgumentException("Maximum tracked players must be positive");
        }
        this.maximumTrackedPlayers = maximumTrackedPlayers;
    }

    public synchronized boolean tryAcquire(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "player ID");
        Objects.requireNonNull(now, "now");
        Instant prior = lastAttempts.get(playerId);
        if (prior != null && now.isBefore(prior.plus(minimumInterval))) {
            return false;
        }
        lastAttempts.put(playerId, now);
        while (lastAttempts.size() > maximumTrackedPlayers) {
            UUID eldest = lastAttempts.entrySet().iterator().next().getKey();
            lastAttempts.remove(eldest);
        }
        return true;
    }

    public synchronized int trackedPlayers() {
        return lastAttempts.size();
    }
}
