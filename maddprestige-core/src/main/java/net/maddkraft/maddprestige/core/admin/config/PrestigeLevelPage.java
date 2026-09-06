package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record PrestigeLevelPage(
        ConfigRevisionId revision,
        List<Long> levels,
        int pageIndex,
        boolean hasPrevious,
        boolean hasNext) {
    public PrestigeLevelPage {
        revision = Objects.requireNonNull(revision, "revision");
        levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
        if (levels.isEmpty() || pageIndex < 0) {
            throw new IllegalArgumentException("Prestige level page requires levels and a non-negative page");
        }
    }
}
