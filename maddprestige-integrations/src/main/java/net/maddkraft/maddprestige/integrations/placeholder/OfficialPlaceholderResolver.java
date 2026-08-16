package net.maddkraft.maddprestige.integrations.placeholder;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

/** Direct compile-time binding to PlaceholderAPI's supported static resolution API. */
public final class OfficialPlaceholderResolver implements PlaceholderResolver {
    private final Function<UUID, OfflinePlayer> players;

    public OfficialPlaceholderResolver(Function<UUID, OfflinePlayer> players) {
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public String resolve(UUID playerId, String placeholder) {
        return PlaceholderAPI.setPlaceholders(players.apply(playerId), placeholder);
    }
}
