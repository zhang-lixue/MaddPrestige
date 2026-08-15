package net.maddkraft.maddprestige.platform.paper;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface PaperTaskScheduler {
    <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task);
}
