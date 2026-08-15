package gg.maddkraft.prestige.service;

import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.model.PlayerState;
import gg.maddkraft.prestige.model.Requirements;
import gg.maddkraft.prestige.model.Season;

import java.time.Duration;
import java.time.Instant;

public final class ProgressionMath {
    private ProgressionMath() {
    }

    public static Requirements prestigeRequirements(
            PluginSettings settings,
            PlayerState state,
            Season season,
            Instant now
    ) {
        double scale = 1.0 + settings.prestige().linearScale() * state.prestigeLevel();
        Requirements scaled = settings.prestige().baseRequirements().scaled(scale);
        return scaled.discounted(catchupDiscount(settings, state, season, now));
    }

    public static Requirements rankRequirements(
            PluginSettings settings,
            PlayerState state,
            Season season,
            Instant now,
            Requirements base
    ) {
        double prestigeDiscount = Math.min(
                settings.prestige().maximumRankupDiscount(),
                state.prestigeLevel() * settings.prestige().rankupDiscountPerPrestige()
        );
        double combined = 1.0 - (1.0 - prestigeDiscount) * (1.0 - catchupDiscount(settings, state, season, now));
        return base.discounted(combined);
    }

    public static int teaLeafReward(PluginSettings settings, int targetPrestige) {
        int bonus = targetPrestige / settings.prestige().bonusTeaLeafEvery();
        return settings.prestige().baseTeaLeaves() + bonus;
    }

    public static double catchupDiscount(PluginSettings settings, PlayerState state, Season season, Instant now) {
        PluginSettings.SeasonDefinition config = settings.season();
        if (!config.catchupEnabled() || now.isBefore(season.startsAt())) return 0.0;
        long joinedDay = Duration.between(season.startsAt(), state.seasonJoinedAt()).toDays();
        if (joinedDay < config.catchupStartsAfterDay()) return 0.0;
        long fullWeeks = (joinedDay - config.catchupStartsAfterDay()) / 7L + 1L;
        return Math.min(config.maximumCatchupReduction(), fullWeeks * config.catchupReductionPerWeek());
    }

    public static int legacyStars(int prestigeLevel, int every) {
        if (prestigeLevel <= 0 || every <= 0) return 0;
        return prestigeLevel / every;
    }
}
