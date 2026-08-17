package net.maddkraft.maddprestige.core.admin;

import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;

@FunctionalInterface
public interface PrestigeAdministrationStore {
    PlayerPrestigeState adjust(ManualPrestigeAdjustment adjustment);
}
