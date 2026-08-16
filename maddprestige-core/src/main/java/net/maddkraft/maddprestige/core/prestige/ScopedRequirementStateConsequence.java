package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import net.maddkraft.maddprestige.core.config.phase4.ResetDisposition;

/** Structured scope-level consequence shared by simulation and persistence. */
public record ScopedRequirementStateConsequence(
        ResetDisposition activeProgress,
        ResetDisposition baselines,
        ResetDisposition latchedCompletions,
        boolean newScopeEstablished,
        boolean priorScopedStateRemainsEffective,
        int initializedBaselineCount) {
    public ScopedRequirementStateConsequence {
        activeProgress = Objects.requireNonNull(activeProgress, "active progress disposition");
        baselines = Objects.requireNonNull(baselines, "baseline disposition");
        latchedCompletions = Objects.requireNonNull(latchedCompletions, "latch disposition");
        if (activeProgress != baselines || activeProgress != latchedCompletions) {
            throw new IllegalArgumentException("Scoped requirement dispositions must agree");
        }
        if (initializedBaselineCount < 0
                || activeProgress == ResetDisposition.RESET
                        && (!newScopeEstablished || priorScopedStateRemainsEffective)
                || activeProgress == ResetDisposition.PRESERVE
                        && (newScopeEstablished || !priorScopedStateRemainsEffective || initializedBaselineCount != 0)) {
            throw new IllegalArgumentException("Scoped requirement consequence contradicts its disposition");
        }
    }
}
