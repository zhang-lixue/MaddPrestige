package net.maddkraft.maddprestige.api.metric;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Stable provider read scope requested for one metric. */
@Stable
public enum MetricReadMode {
    /** Read the value in its currently active/resettable scope. */
    CURRENT,

    /** Read the durable lifetime value across reset scopes. */
    LIFETIME
}
