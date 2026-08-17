package net.maddkraft.maddprestige.core.admin;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpPlan;

@FunctionalInterface
public interface RankUpPlanExecutor {
    CompletionStage<RankUpExecutionResult> execute(RankUpPlan plan);
}
