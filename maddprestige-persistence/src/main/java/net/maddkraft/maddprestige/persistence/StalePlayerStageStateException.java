package net.maddkraft.maddprestige.persistence;

public final class StalePlayerStageStateException extends PersistenceException {
    private static final long serialVersionUID = 1L;

    public StalePlayerStageStateException(String message) {
        super(message);
    }
}
