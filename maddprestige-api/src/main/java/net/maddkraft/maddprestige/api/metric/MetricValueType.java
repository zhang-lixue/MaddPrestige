package net.maddkraft.maddprestige.api.metric;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Stable canonical representations accepted by {@link MetricValue}. */
@Stable
public enum MetricValueType {
    /** Arbitrary signed integral value. */
    INTEGER,

    /** Arbitrary exact base-10 decimal without floating-point rounding. */
    EXACT_DECIMAL,

    /** Non-negative duration canonicalized as ISO-8601. */
    DURATION,

    /** Canonical lower-case boolean. */
    BOOLEAN,

    /** Bounded opaque string compared lexically. */
    STRING,

    /** Bounded provider/configuration enum token compared lexically. */
    ENUM,

    /** Arbitrary non-negative integral count. */
    COUNT,

    /** Exact base-10 internal currency amount. */
    CURRENCY_AMOUNT;

    /**
     * Returns whether the type has an exact numeric projection.
     *
     * @return true for integers, decimals, durations, counts, and currency amounts
     */
    public boolean isNumeric() {
        return this == INTEGER || this == EXACT_DECIMAL || this == DURATION
                || this == COUNT || this == CURRENCY_AMOUNT;
    }

    /**
     * Returns whether the type advances in integral units.
     *
     * @return true for integers, counts, and millisecond durations
     */
    public boolean isDiscrete() {
        return this == INTEGER || this == COUNT || this == DURATION;
    }
}
