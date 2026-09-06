package net.maddkraft.maddprestige.core.admin.ui;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Read-only, paged projection of canonical Prestige history for the Staff GUI. */
@FunctionalInterface
public interface StaffHistorySource {
    CompletionStage<Page> recent(int offset, int limit);

    /** Returns a deterministic page for one server-owned player identity. */
    default CompletionStage<Page> forPlayer(UUID playerId, int offset, int limit) {
        Objects.requireNonNull(playerId, "player ID");
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Selected-player history is not available from this source"));
    }

    /** Resolves one stable canonical operation entry for one player. */
    default CompletionStage<Optional<Entry>> entry(UUID playerId, UUID entryId) {
        Objects.requireNonNull(playerId, "player ID");
        Objects.requireNonNull(entryId, "entry ID");
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Selected history detail is not available from this source"));
    }

    record Page(List<Entry> entries, boolean hasPrevious, boolean hasNext, long totalEntries) {
        public Page {
            entries = List.copyOf(Objects.requireNonNull(entries, "history entries"));
            if (totalEntries < entries.size()) {
                throw new IllegalArgumentException("History total cannot be smaller than the returned page");
            }
        }

        public Page(List<Entry> entries, boolean hasPrevious, boolean hasNext) {
            this(entries, hasPrevious, hasNext,
                    entries.size() + (hasPrevious ? 1L : 0L) + (hasNext ? 1L : 0L));
        }
    }

    record Entry(
            UUID entryId,
            String player,
            Kind kind,
            Optional<UUID> actorId,
            Optional<String> actorName,
            long before,
            long after,
            Outcome outcome,
            boolean costRecorded,
            boolean rewardRecorded,
            Optional<String> balanceBefore,
            Optional<String> balanceAfter,
            Optional<String> costSummary,
            Optional<String> rewardSummary,
            Instant occurredAt) {
        public Entry {
            entryId = Objects.requireNonNull(entryId, "entry ID");
            player = Objects.requireNonNull(player, "player");
            kind = Objects.requireNonNull(kind, "history kind");
            actorId = Objects.requireNonNull(actorId, "actor ID");
            actorName = normalized(actorName, "actor name");
            outcome = Objects.requireNonNull(outcome, "outcome");
            balanceBefore = normalized(balanceBefore, "balance before");
            balanceAfter = normalized(balanceAfter, "balance after");
            costSummary = normalized(costSummary, "cost summary");
            rewardSummary = normalized(rewardSummary, "reward summary");
            occurredAt = Objects.requireNonNull(occurredAt, "occurred at");
        }

        public Entry(
                UUID entryId,
                String player,
                long before,
                long after,
                Outcome outcome,
                boolean costRecorded,
                boolean rewardRecorded,
                Optional<String> balanceBefore,
                Optional<String> balanceAfter,
                Optional<String> costSummary,
                Optional<String> rewardSummary,
                Instant occurredAt) {
            this(entryId, player, Kind.NORMAL_PRESTIGE, Optional.empty(), Optional.empty(), before, after, outcome,
                    costRecorded, rewardRecorded, balanceBefore, balanceAfter, costSummary, rewardSummary, occurredAt);
        }

        private static Optional<String> normalized(Optional<String> value, String label) {
            Optional<String> checked = Objects.requireNonNull(value, label);
            return checked.map(String::trim).filter(text -> !text.isEmpty());
        }
    }

    enum Kind {
        NORMAL_PRESTIGE,
        ADMIN_SET,
        ADMIN_RESET
    }

    enum Outcome {
        COMPLETED,
        RECOVERED,
        REJECTED,
        ATTENTION,
        FAILED,
        IN_PROGRESS
    }
}
