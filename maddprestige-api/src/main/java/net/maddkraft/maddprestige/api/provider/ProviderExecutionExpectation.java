package net.maddkraft.maddprestige.api.provider;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Stable execution expectations for external provider callbacks. */
@Stable
public enum ProviderExecutionExpectation {
    /** Callback runs on a bounded MaddPrestige worker and must not assume Paper server-thread affinity. */
    BOUNDED_WORKER
}
