package gg.maddkraft.prestige.placeholder;

import gg.maddkraft.prestige.MaddPrestigePlugin;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.service.ContestService;
import gg.maddkraft.prestige.service.HatterService;
import gg.maddkraft.prestige.service.ProfileService;
import gg.maddkraft.prestige.service.SeasonService;
import gg.maddkraft.prestige.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;

public final class MaddPrestigeExpansion extends PlaceholderExpansion {
    private final MaddPrestigePlugin plugin;
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final ContestService contests;
    private final HatterService hatter;

    public MaddPrestigeExpansion(MaddPrestigePlugin plugin, ProfileService profiles, SeasonService seasons,
                                 ContestService contests, HatterService hatter) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.seasons = seasons;
        this.contests = contests;
        this.hatter = hatter;
    }

    @Override public @NotNull String getIdentifier() { return "maddprestige"; }
    @Override public @NotNull String getAuthor() { return "MaddKraft"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        return resolve(player, identifier);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
        return resolve(player, identifier);
    }

    private String resolve(OfflinePlayer player, String identifier) {
        String key = identifier.toLowerCase(Locale.ROOT);
        if (key.equals("season_name")) return seasons.current().displayName();
        if (key.equals("season_id")) return seasons.current().id();
        if (key.equals("season_status")) return seasons.current().status().name();
        if (key.equals("season_day")) return Long.toString(seasons.dayNumber(Instant.now()));
        if (key.equals("hatter")) return hatter.holder().map(holder -> {
            String name = Bukkit.getOfflinePlayer(holder.playerId()).getName();
            return name == null ? holder.playerId().toString().substring(0, 8) : name;
        }).orElse("Vacant");
        if (key.equals("contest_metric")) return contests.activeContest().map(contest -> contest.metric().name()).orElse("NONE");
        if (player == null) return "";
        PlayerState state = profiles.get(player.getUniqueId()).orElse(null);
        if (state == null) return "loading";
        return switch (key) {
            case "rank" -> state.rank().name();
            case "rank_display" -> display(state.rank().name());
            case "next_rank" -> state.rank().next().map(rank -> display(rank.name())).orElse("Prestige");
            case "prestige" -> Integer.toString(state.prestigeLevel());
            case "lifetime_prestiges" -> Integer.toString(state.lifetimePrestiges());
            case "tea_leaves" -> Integer.toString(state.teaLeaves());
            case "legacy_stars" -> Integer.toString(state.legacyStars());
            case "server_earnings" -> Text.number(state.ledger().serverEarnings());
            case "mcmmo_xp" -> Long.toString(state.ledger().mcMmoXp());
            case "rabbit_holes" -> Integer.toString(state.ledger().rabbitHoles());
            case "decree_objectives" -> Integer.toString(state.ledger().decreeObjectives());
            case "bosses" -> Integer.toString(state.ledger().bosses());
            case "contest_score" -> Text.number(contests.score(player.getUniqueId()).orElse(0.0));
            case "patron" -> patronDisplay(player);
            case "title" -> hatter.holder().filter(holder -> holder.playerId().equals(player.getUniqueId()))
                    .map(ignored -> "MaddHatter").orElseGet(() -> {
                        String patron = patronDisplay(player);
                        return patron.isBlank() ? display(state.rank().name()) : patron;
                    });
            case "is_hatter" -> Boolean.toString(hatter.holder()
                    .map(holder -> holder.playerId().equals(player.getUniqueId())).orElse(false));
            default -> null;
        };
    }

    private String patronDisplay(OfflinePlayer player) {
        if (!(player instanceof Player online)) return "";
        return plugin.settings().patronTiers().values().stream()
                .filter(tier -> online.hasPermission(tier.entitlementPermission()))
                .max(java.util.Comparator.comparingInt(gg.maddkraft.prestige.config.PluginSettings.PatronTierDefinition::priority))
                .map(gg.maddkraft.prestige.config.PluginSettings.PatronTierDefinition::displayName)
                .orElse("");
    }

    private String display(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
