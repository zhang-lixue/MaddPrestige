package net.maddkraft.maddprestige.core.prestige;

import java.util.List;

record PrestigeBoundaryCollection(List<PrestigeBaselineConsequence> baselines, List<String> blockers) {
    PrestigeBoundaryCollection {
        baselines = List.copyOf(baselines);
        blockers = List.copyOf(blockers);
    }
}
