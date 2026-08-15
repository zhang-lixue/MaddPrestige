package net.maddkraft.maddprestige.core.operation;

public final class IllegalTransitionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public IllegalTransitionException(String message) {
        super(message);
    }
}
