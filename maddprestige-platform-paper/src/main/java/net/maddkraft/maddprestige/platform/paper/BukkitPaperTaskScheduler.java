package net.maddkraft.maddprestige.platform.paper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Production Paper scheduler boundary with exception-preserving futures. */
public final class BukkitPaperTaskScheduler implements PaperTaskScheduler {
    private final Plugin plugin;

    public BukkitPaperTaskScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public <T> CompletionStage<T> submit(ExecutionThread thread, Supplier<T> task) {
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(task, "task");
        if (thread == ExecutionThread.PAPER_SERVER_THREAD && Bukkit.isPrimaryThread()) {
            return completed(task);
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable invocation = () -> complete(result, task);
        if (thread == ExecutionThread.PAPER_SERVER_THREAD) {
            Bukkit.getScheduler().runTask(plugin, invocation);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, invocation);
        }
        return result;
    }

    private static <T> CompletionStage<T> completed(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        complete(result, task);
        return result;
    }

    private static <T> void complete(CompletableFuture<T> result, Supplier<T> task) {
        try {
            result.complete(task.get());
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
    }
}
