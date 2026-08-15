package gg.maddkraft.prestige.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Outbound lifecycle event for plugins that prefer an API integration over
 * config-driven command actions.
 */
public final class MaddPrestigeActionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String action;
    private final OfflinePlayer player;
    private final Map<String, String> values;

    public MaddPrestigeActionEvent(String action, @Nullable OfflinePlayer player, Map<String, String> values) {
        this.action = action;
        this.player = player;
        this.values = Map.copyOf(values);
    }

    public @NotNull String getAction() { return action; }
    public @Nullable OfflinePlayer getPlayer() { return player; }
    public @NotNull Map<String, String> getValues() { return values; }
    public @Nullable String getValue(String key) { return values.get(key); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
