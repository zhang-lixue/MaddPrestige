package net.maddkraft.maddprestige.core.admin;

import java.util.List;
import java.util.Objects;

/** Bounded server-owned numeric choices for one Set Prestige selector page. */
public record ManualPrestigeTargetPage(
        long currentPrestige,
        int page,
        List<ManualPrestigeAdjustmentReview> targets,
        boolean hasPrevious,
        boolean hasNext) {
    public ManualPrestigeTargetPage {
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (currentPrestige < 0 || page < 0 || targets.size() > 7) {
            throw new IllegalArgumentException("Administrative Prestige target page is outside bounds");
        }
    }
}
