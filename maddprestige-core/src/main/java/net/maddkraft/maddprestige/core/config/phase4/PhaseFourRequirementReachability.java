package net.maddkraft.maddprestige.core.config.phase4;

import java.util.LinkedHashSet;
import java.util.Set;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

/** Computes only requirement definitions reachable from active Phase 4 execution paths. */
public final class PhaseFourRequirementReachability {
    private PhaseFourRequirementReachability() {
    }

    public static Set<RequirementDefinition> prestigeDefinitions(
            PhaseFourConfiguration phaseFour,
            PhaseThreeConfiguration phaseThree) {
        LinkedHashSet<RequirementDefinition> result = new LinkedHashSet<>();
        if (phaseFour.prestige().enabled()) {
            phaseFour.prestige().requirementTreeId().map(phaseThree.trees()::get)
                    .ifPresent(tree -> collect(tree, result));
        }
        return Set.copyOf(result);
    }

    public static Set<RequirementDefinition> postPrestigeDefinitions(
            PhaseFourConfiguration phaseFour,
            PhaseThreeConfiguration phaseThree,
            StageConfiguration stages) {
        LinkedHashSet<RequirementDefinition> result = new LinkedHashSet<>(
                prestigeDefinitions(phaseFour, phaseThree));
        // Numeric Prestige establishes only its own next interval; stage ladders are a legacy compatibility surface.
        return Set.copyOf(result);
    }

    private static void collect(RequirementNode node, Set<RequirementDefinition> result) {
        if (node instanceof RequirementLeaf leaf) {
            result.add(leaf.definition());
            return;
        }
        ((RequirementGroup) node).children().forEach(child -> collect(child.node(), result));
    }
}
