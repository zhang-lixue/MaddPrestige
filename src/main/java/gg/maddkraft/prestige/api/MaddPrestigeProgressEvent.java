package gg.maddkraft.prestige.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MaddPrestigeProgressEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ProgressType type;
    private final double amount;
    private final String source;

    public MaddPrestigeProgressEvent(Player player, ProgressType type, double amount, String source) {
        this.player = player;
        this.type = type;
        this.amount = amount;
        this.source = source == null ? "external" : source;
    }

    public Player getPlayer() { return player; }
    public ProgressType getType() { return type; }
    public double getAmount() { return amount; }
    public String getSource() { return source; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
