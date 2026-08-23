package net.maddkraft.maddprestige.api.metric;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Provider declaration of whether an observed metric can decrease within its scope. */
@Stable
public enum MetricMonotonicity {
    /** Value cannot decrease within its declared measurement scope. */
    MONOTONIC,

    /** Value may increase or decrease within its declared measurement scope. */
    NON_MONOTONIC,

    /** Provider makes no monotonicity guarantee. */
    UNKNOWN
}
