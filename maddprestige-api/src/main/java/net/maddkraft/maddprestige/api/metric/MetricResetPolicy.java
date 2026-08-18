package net.maddkraft.maddprestige.api.metric;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Provider declaration of how a metric participates in progression resets. */
@Stable
public enum MetricResetPolicy {
    /** A reset conflict makes reconciliation fail closed. */
    FAIL_RECONCILIATION,

    /** The provider owns and defines the reset operation. */
    PROVIDER_DEFINED_RESET,

    /** The metric does not participate in resets. */
    NOT_APPLICABLE
}
