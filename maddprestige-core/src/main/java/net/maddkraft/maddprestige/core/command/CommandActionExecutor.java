package net.maddkraft.maddprestige.core.command;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.id.OperationId;

public final class CommandActionExecutor {
    private final CommandDispatcher dispatcher;

    public CommandActionExecutor(CommandDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public CompletionStage<ActionExecutionResult> execute(CommandActionPlan plan) {
        return execute(plan, CommandExecutionContext.root(OperationId.random()));
    }

    public CompletionStage<ActionExecutionResult> execute(
            CommandActionPlan plan,
            CommandExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "execution context");
        return dispatcher.dispatch(plan.command(), context).thenApply(result -> switch (result) {
            case SUCCEEDED -> ActionExecutionResult.applied();
            case FAILED -> ActionExecutionResult.failed("External console command reported failure");
            case UNCERTAIN -> ActionExecutionResult.uncertain(
                    "Console command may have executed and cannot be safely replayed");
        });
    }
}
