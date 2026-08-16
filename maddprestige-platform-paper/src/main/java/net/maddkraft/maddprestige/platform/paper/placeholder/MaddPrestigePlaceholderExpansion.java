package net.maddkraft.maddprestige.platform.paper.placeholder;

import java.util.Objects;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PlaceholderAPI expansion whose hot path performs no database, provider, or mutation calls. */
public final class MaddPrestigePlaceholderExpansion extends PlaceholderExpansion {
    private final MaddPrestigePlaceholderCache cache;
    private final String version;

    public MaddPrestigePlaceholderExpansion(MaddPrestigePlaceholderCache cache, String version) {
        this.cache = Objects.requireNonNull(cache, "placeholder cache");
        this.version = Objects.requireNonNull(version, "version");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "maddprestige";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MaddKraft";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        if (player == null) {
            return "";
        }
        return cache.resolve(player.getUniqueId(), parameters).orElse(null);
    }
}
