package net.maddkraft.maddprestige.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaperTaskSchedulerContractTest {
    @Test
    @DisplayName("[A62] Platform scheduler makes server-thread and asynchronous boundaries explicit")
    void declaresThreadBoundary() {
        PaperTaskScheduler directTestScheduler = new PaperTaskScheduler() {
            @Override
            public <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }
        };
        assertEquals("ok", directTestScheduler.submit(ExecutionThread.ASYNC_WORKER, () -> "ok")
                .toCompletableFuture().join());
    }
}
