package net.maddkraft.maddprestige.core.prestige;

import java.util.UUID;
import net.maddkraft.maddprestige.core.config.lifecycle.ActiveLifecycleConfiguration;

@FunctionalInterface
public interface PrestigeProgressContextSource {
    PrestigeProgressContext load(
            UUID playerId,
            PlayerPrestigeState prestigeState,
            ActiveLifecycleConfiguration configuration);
}
