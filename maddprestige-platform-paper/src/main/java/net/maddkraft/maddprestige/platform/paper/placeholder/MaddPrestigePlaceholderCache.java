package net.maddkraft.maddprestige.platform.paper.placeholder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded synchronized cache; render lookups only copy references to immutable snapshots. */
public final class MaddPrestigePlaceholderCache {
    private final int maximumPlayers;
    private final Map<UUID, MaddPrestigePlaceholderSnapshot> snapshots = new LinkedHashMap<>(16, 0.75f, true);

    public MaddPrestigePlaceholderCache(int maximumPlayers) {
        if (maximumPlayers < 1 || maximumPlayers > 100_000) {
            throw new IllegalArgumentException("Placeholder output cache bound is outside the safe domain");
        }
        this.maximumPlayers = maximumPlayers;
    }

    public synchronized void publish(UUID playerId, MaddPrestigePlaceholderSnapshot snapshot) {
        snapshots.put(Objects.requireNonNull(playerId, "player ID"),
                Objects.requireNonNull(snapshot, "placeholder snapshot"));
        while (snapshots.size() > maximumPlayers) {
            snapshots.remove(snapshots.keySet().iterator().next());
        }
    }

    public synchronized Optional<String> resolve(UUID playerId, String identifier) {
        MaddPrestigePlaceholderSnapshot snapshot = snapshots.get(playerId);
        return snapshot == null ? Optional.empty() : Optional.ofNullable(snapshot.resolve(identifier));
    }

    public synchronized void remove(UUID playerId) {
        snapshots.remove(playerId);
    }

    public synchronized int size() {
        return snapshots.size();
    }
}
