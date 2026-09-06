package net.maddkraft.maddprestige.platform.paper;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface PaperTaskScheduler {
    <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task);

    /**
     * Enqueues work even when the caller is already on the requested thread.
     * Event handlers use this boundary for Paper operations that must not run inside the current event callback.
     */
    default <T> CompletionStage<T> submitDeferred(ExecutionThread thread, Supplier<T> task) {
        return submit(thread, task);
    }
}
