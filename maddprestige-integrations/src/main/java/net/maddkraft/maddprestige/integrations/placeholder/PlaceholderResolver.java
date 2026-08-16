package net.maddkraft.maddprestige.integrations.placeholder;

import java.util.UUID;

/** Narrow seam over PlaceholderAPI's official resolution function. */
@FunctionalInterface
public interface PlaceholderResolver {
    String resolve(UUID playerId, String placeholder);
}
