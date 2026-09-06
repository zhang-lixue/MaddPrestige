package net.maddkraft.maddprestige.core.admin;

import java.util.OptionalLong;

/** Active numeric bounds for administrative Prestige replacement. */
public record ManualPrestigeAdjustmentPolicy(long resetTarget, OptionalLong maximum) {
    public ManualPrestigeAdjustmentPolicy {
        maximum = maximum == null ? OptionalLong.empty() : maximum;
        if (resetTarget < 0 || maximum.isPresent() && resetTarget > maximum.getAsLong()) {
            throw new IllegalArgumentException("Administrative Prestige reset target is outside active bounds");
        }
    }

    public static ManualPrestigeAdjustmentPolicy unlimited() {
        return new ManualPrestigeAdjustmentPolicy(0, OptionalLong.empty());
    }

    public boolean allows(long target) {
        return target >= resetTarget && (maximum.isEmpty() || target <= maximum.getAsLong());
    }
}
