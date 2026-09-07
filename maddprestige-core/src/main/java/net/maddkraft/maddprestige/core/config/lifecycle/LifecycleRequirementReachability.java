package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.LinkedHashSet;
import java.util.Set;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;

/** Computes only requirement definitions reachable from active Prestige lifecycle execution paths. */
public final class LifecycleRequirementReachability {
    private LifecycleRequirementReachability() {
    }

    public static Set<RequirementDefinition> prestigeDefinitions(
            LifecycleConfiguration lifecycle,
            ProgressionConfiguration progression) {
        LinkedHashSet<RequirementDefinition> result = new LinkedHashSet<>();
        if (lifecycle.prestige().enabled()) {
            lifecycle.prestige().requirementTreeId().map(progression.trees()::get)
                    .ifPresent(tree -> collect(tree, result));
        }
        return Set.copyOf(result);
    }

    public static Set<RequirementDefinition> postPrestigeDefinitions(
            LifecycleConfiguration lifecycle,
            ProgressionConfiguration progression,
            StageConfiguration stages) {
        LinkedHashSet<RequirementDefinition> result = new LinkedHashSet<>(
                prestigeDefinitions(lifecycle, progression));
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
