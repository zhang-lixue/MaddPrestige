package net.maddkraft.maddprestige.core.command;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.OperationId;

/** Trusted correlation state; nested work can only increase the current trigger depth. */
public final class CommandExecutionContext {
    private final OperationId correlationId;
    private final int triggerDepth;

    private CommandExecutionContext(OperationId correlationId, int triggerDepth) {
        this.correlationId = Objects.requireNonNull(correlationId, "correlation ID");
        this.triggerDepth = triggerDepth;
    }

    public static CommandExecutionContext root(OperationId correlationId) {
        return new CommandExecutionContext(correlationId, 0);
    }

    public CommandExecutionContext nested() {
        return new CommandExecutionContext(correlationId, Math.addExact(triggerDepth, 1));
    }

    public OperationId correlationId() {
        return correlationId;
    }

    public int triggerDepth() {
        return triggerDepth;
    }
}
