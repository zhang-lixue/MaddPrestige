package net.maddkraft.maddprestige.core.config;

import java.util.List;
import java.util.Objects;

public record SemanticDiff(List<SemanticDiffEntry> entries) {
    public SemanticDiff {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
