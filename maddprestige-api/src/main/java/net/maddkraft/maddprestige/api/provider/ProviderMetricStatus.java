package net.maddkraft.maddprestige.api.provider;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Provider-owned availability outcome for one metric request. */
@Stable
public enum ProviderMetricStatus {
    /** A valid exact metric value is present. */
    AVAILABLE,

    /** No value is authoritative; machine failure metadata explains the outcome. */
    UNAVAILABLE
}
