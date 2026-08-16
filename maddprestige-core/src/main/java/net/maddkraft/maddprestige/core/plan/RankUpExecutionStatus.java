package net.maddkraft.maddprestige.core.plan;

public enum RankUpExecutionStatus {
    COMPLETED,
    BLOCKED,
    DUPLICATE,
    FAILED,
    STALE_GENERATION,
    NEEDS_RECONCILIATION,
    COMPENSATED
}
