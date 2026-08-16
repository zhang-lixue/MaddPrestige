package net.maddkraft.maddprestige.core.command;

import java.util.concurrent.CompletionStage;

public interface CommandDispatcher {
    CompletionStage<CommandDispatchResult> dispatch(String command);

    default CompletionStage<CommandDispatchResult> dispatch(
            String command,
            CommandExecutionContext context) {
        return dispatch(command);
    }
}
