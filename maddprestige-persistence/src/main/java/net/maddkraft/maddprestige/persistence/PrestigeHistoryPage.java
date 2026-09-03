package net.maddkraft.maddprestige.persistence;

import java.util.List;
import java.util.Objects;

/** Deterministic page from the canonical Prestige history journal. */
public record PrestigeHistoryPage(List<PrestigeHistoryRecord> entries, long totalEntries) {
    public PrestigeHistoryPage {
        entries = List.copyOf(Objects.requireNonNull(entries, "history entries"));
        if (totalEntries < entries.size()) {
            throw new IllegalArgumentException("History total cannot be smaller than the returned page");
        }
    }
}
