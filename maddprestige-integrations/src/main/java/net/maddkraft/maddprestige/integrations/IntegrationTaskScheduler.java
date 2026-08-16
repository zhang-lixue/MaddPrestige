package net.maddkraft.maddprestige.integrations;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Runs third-party API calls in the server-thread context required by the plugin API. */
@FunctionalInterface
public interface IntegrationTaskScheduler {
    <T> CompletionStage<T> call(Supplier<T> action);
}
