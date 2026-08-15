package net.maddkraft.maddprestige.core.economy;

import net.maddkraft.maddprestige.api.value.ExactDecimal;

public record PeerTransferProgressionPolicy(boolean enabled, ExactDecimal creditWeight) {
    public PeerTransferProgressionPolicy {
        if (creditWeight.compareTo(ExactDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit weight cannot be negative");
        }
    }

    public static PeerTransferProgressionPolicy safeDefault() {
        return new PeerTransferProgressionPolicy(false, ExactDecimal.ZERO);
    }
}
