package net.maddkraft.maddprestige.api.service;

/** Stable, intentionally coarse operation outcome. */
public enum OperationStatus {
    /** Every durable effect completed and the journal reached its clean terminal state. */
    COMPLETED,

    /** Authorization, PRE cancellation, or another expected gate rejected the request. */
    BLOCKED,

    /** A revision, stage, provider, duplicate, or reentrancy fence rejected stale/conflicting work. */
    CONFLICT,

    /** The operation failed with a known clean or compensated terminal outcome. */
    FAILED,

    /** Durability is terminal but one or more effects require operator reconciliation. */
    NEEDS_RECONCILIATION
}
