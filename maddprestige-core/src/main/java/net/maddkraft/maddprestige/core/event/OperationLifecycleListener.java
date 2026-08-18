package net.maddkraft.maddprestige.core.event;

import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

/** Platform-neutral gate positioned between canonical authorization/revalidation and journal insertion. */
public interface OperationLifecycleListener {
    OperationLifecycleListener NONE = new OperationLifecycleListener() {
    };

    default boolean beforeRankUp(RankUpPlan plan) {
        return true;
    }

    default void afterRankUp(RankUpPlan plan, RankUpExecutionResult result) {
    }

    default boolean beforePrestige(PrestigePlan plan) {
        return true;
    }

    default void afterPrestige(PrestigePlan plan, PrestigeExecutionResult result) {
    }
}
