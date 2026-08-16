package net.maddkraft.maddprestige.core.prestige;

public enum PrestigeExecutionStatus {
    COMPLETED,
    UNAUTHORIZED,
    DUPLICATE,
    STALE_CONFIGURATION,
    STALE_GENERATION,
    FAILED,
    COMPENSATED,
    NEEDS_RECONCILIATION
}
