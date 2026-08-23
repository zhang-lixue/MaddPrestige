package net.maddkraft.maddprestige.api.provider;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Read-only, non-blocking cancellation capability whose lifetime is limited to one provider callback. */
@FunctionalInterface
@Stable
public interface ProviderCancellationSignal {
    /**
     * Reads the current cancellation state without blocking or throwing operational failures.
     *
     * @return true when the provider should stop optional work and complete promptly
     */
    boolean cancellationRequested();
}
