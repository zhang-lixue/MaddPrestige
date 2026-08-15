package gg.maddkraft.prestige.model;

public enum SeasonStatus {
    ACTIVE,
    CLOSING,
    FROZEN,
    RESETTING,
    CLOSED;

    public boolean permitsProgress() {
        return this == ACTIVE || this == CLOSING;
    }

    public boolean permitsPrestige() {
        return this == ACTIVE || this == CLOSING;
    }
}
