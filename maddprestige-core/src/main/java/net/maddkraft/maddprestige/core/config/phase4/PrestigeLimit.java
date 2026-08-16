package net.maddkraft.maddprestige.core.config.phase4;

import java.util.OptionalLong;

public record PrestigeLimit(OptionalLong maximum) {
    public PrestigeLimit {
        maximum = maximum == null ? OptionalLong.empty() : maximum;
        if (maximum.isPresent() && maximum.getAsLong() < 1) {
            throw new IllegalArgumentException("Finite Prestige maximum must be positive");
        }
    }

    public static PrestigeLimit unlimited() {
        return new PrestigeLimit(OptionalLong.empty());
    }

    public static PrestigeLimit finite(long maximum) {
        return new PrestigeLimit(OptionalLong.of(maximum));
    }

    public boolean allows(long current, long increment) {
        if (current < 0 || increment < 1 || current > Long.MAX_VALUE - increment) {
            return false;
        }
        long result = current + increment;
        return maximum.isEmpty() || result <= maximum.getAsLong();
    }
}
