package net.maddkraft.maddprestige.core.stage;

/** Raised before effects when a configuration transition has fenced a source or target stage. */
public final class StageTransitionBlockedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StageTransitionBlockedException(String message) {
        super(message);
    }
}
