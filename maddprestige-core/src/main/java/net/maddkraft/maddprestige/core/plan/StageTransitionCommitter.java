package net.maddkraft.maddprestige.core.plan;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;

public interface StageTransitionCommitter {
    CompletionStage<ActionExecutionResult> commit(RankUpPlan plan);
}
