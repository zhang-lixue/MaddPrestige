package net.maddkraft.maddprestige.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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

    @Test
    @DisplayName("[Phase 9F-A correction] Deferred server work queues even from Paper''s primary thread")
    void deferredServerWorkNeverRunsInline() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(bukkitScheduler);
            BukkitPaperTaskScheduler scheduler = new BukkitPaperTaskScheduler(plugin);

            CompletionStage<String> result = scheduler.submitDeferred(
                    ExecutionThread.PAPER_SERVER_THREAD, () -> "Preview opened");

            assertFalse(result.toCompletableFuture().isDone());
            verify(bukkitScheduler).runTask(eq(plugin), queued.capture());
            queued.getValue().run();
            assertEquals("Preview opened", result.toCompletableFuture().join());
        }
    }
}
