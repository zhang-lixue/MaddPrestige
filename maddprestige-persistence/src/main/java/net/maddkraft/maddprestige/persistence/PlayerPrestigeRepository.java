package net.maddkraft.maddprestige.persistence;

import java.util.List;
import java.util.UUID;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeStateSource;

public interface PlayerPrestigeRepository extends PlayerPrestigeStateSource {
    List<UUID> knownPlayerIds();

    void insert(PlayerPrestigeState state);

    void update(PlayerPrestigeState replacement, long expectedRevision);
}
