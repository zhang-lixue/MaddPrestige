package net.maddkraft.maddprestige.api.rank;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ManagedRankState(
        UUID playerId,
        Set<String> permanentContextFreeGroups,
        List<AmbiguousRankMembership> ambiguousMemberships) {
    public ManagedRankState {
        playerId = Objects.requireNonNull(playerId, "player ID");
        permanentContextFreeGroups = Set.copyOf(Objects.requireNonNull(
                permanentContextFreeGroups, "permanent context-free groups"));
        ambiguousMemberships = List.copyOf(Objects.requireNonNull(ambiguousMemberships, "ambiguous memberships"));
    }

    public boolean hasAmbiguity() {
        return !ambiguousMemberships.isEmpty();
    }
}
