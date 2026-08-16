package net.maddkraft.maddprestige.persistence;

import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeStateSource;

public interface PlayerPrestigeRepository extends PlayerPrestigeStateSource {
    void insert(PlayerPrestigeState state);

    void update(PlayerPrestigeState replacement, long expectedRevision);
}
