package net.maddkraft.maddprestige.api.rank;

import java.util.Objects;

public record RankProjectionResult(
        ManagedRankState before,
        ManagedRankState after,
        RankProjectionOutcome outcome) {
    public RankProjectionResult {
        before = Objects.requireNonNull(before, "before state");
        after = Objects.requireNonNull(after, "after state");
        outcome = Objects.requireNonNull(outcome, "outcome");
        if (!before.playerId().equals(after.playerId())) {
            throw new IllegalArgumentException("Projection result player IDs must match");
        }
    }
}
