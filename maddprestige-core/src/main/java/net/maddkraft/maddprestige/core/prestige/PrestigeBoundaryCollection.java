package net.maddkraft.maddprestige.core.prestige;

import java.util.List;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

record PrestigeBoundaryCollection(
        List<PrestigeBaselineConsequence> baselines,
        List<AuthorizationBlocker> blockers) {
    PrestigeBoundaryCollection {
        baselines = List.copyOf(baselines);
        blockers = List.copyOf(blockers);
    }
}
