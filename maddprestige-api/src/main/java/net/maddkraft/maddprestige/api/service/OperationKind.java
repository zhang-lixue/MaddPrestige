package net.maddkraft.maddprestige.api.service;

/** Stable operation identity for active and retained compatibility operations. */
public enum OperationKind {
    /** Retired stage-era operation; production keeps it blocked for stable API compatibility. */
    RANK_UP,

    /** Active numeric progression from Prestige {@code N} to {@code N + 1}. */
    PRESTIGE
}
