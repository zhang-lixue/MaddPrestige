package net.maddkraft.maddprestige.api.operation;

public enum OperationState {
    PLANNED,
    PREPARED,
    EXECUTING,
    STATE_COMMITTED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED,
    NEEDS_RECONCILIATION
}
