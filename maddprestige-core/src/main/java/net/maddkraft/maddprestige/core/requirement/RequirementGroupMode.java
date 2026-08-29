package net.maddkraft.maddprestige.core.requirement;

public enum RequirementGroupMode {
    ALL,
    ANY,
    X_OF_N,
    /** @deprecated Use the clearer {@link #X_OF_N} spelling. */
    @Deprecated
    ANY_X_OF_Y,
    WEIGHTED
}
