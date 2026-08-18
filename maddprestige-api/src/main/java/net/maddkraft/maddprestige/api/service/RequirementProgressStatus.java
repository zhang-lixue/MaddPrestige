package net.maddkraft.maddprestige.api.service;

/** Stable coarse requirement state; it does not expose provider implementation exceptions. */
public enum RequirementProgressStatus {
    /** The observed value satisfies the canonical requirement. */
    SATISFIED,

    /** The observed value does not satisfy the canonical requirement. */
    UNSATISFIED,

    /** Required provider or scoped state was not available. */
    UNAVAILABLE,

    /** The configured request or returned value violates its stable semantic contract. */
    INVALID,

    /** A contained operational provider/read failure prevented evaluation. */
    ERROR
}
