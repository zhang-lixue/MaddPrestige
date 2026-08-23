package net.maddkraft.maddprestige.api.service;

/** A supported player progression mutation. */
public enum OperationKind {
    /** Advance from the current stage to its canonical next stage. */
    RANK_UP,

    /** Complete the configured terminal-stage Prestige transition. */
    PRESTIGE
}
