package net.maddkraft.maddprestige.api.service;

/** Machine-readable outcome of a side-effect-free operation evaluation. */
public enum OperationEvaluationStatus {
    /** Canonical authorization succeeded and includes a side-effect-free simulation. */
    ELIGIBLE,

    /** Canonical runtime is available but one or more machine-readable blockers apply. */
    BLOCKED,

    /** Evaluation cannot be authoritative because a required runtime capability is unavailable. */
    UNAVAILABLE
}
