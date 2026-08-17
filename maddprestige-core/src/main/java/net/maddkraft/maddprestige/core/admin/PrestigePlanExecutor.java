package net.maddkraft.maddprestige.core.admin;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigePlan;

@FunctionalInterface
public interface PrestigePlanExecutor {
    CompletionStage<PrestigeExecutionResult> execute(PrestigePlan plan);
}
