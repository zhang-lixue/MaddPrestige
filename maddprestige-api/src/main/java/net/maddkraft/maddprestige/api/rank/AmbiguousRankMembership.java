package net.maddkraft.maddprestige.api.rank;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record AmbiguousRankMembership(
        String groupName,
        Map<String, Set<String>> contexts,
        Optional<Instant> expiresAt) {
    public AmbiguousRankMembership {
        groupName = Objects.requireNonNull(groupName, "group name");
        Objects.requireNonNull(contexts, "contexts");
        contexts = contexts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        if (contexts.isEmpty() && expiresAt.isEmpty()) {
            throw new IllegalArgumentException("Ambiguous membership must be contextual or temporary");
        }
    }
}
