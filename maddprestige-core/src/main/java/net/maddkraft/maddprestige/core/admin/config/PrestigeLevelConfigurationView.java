package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record PrestigeLevelConfigurationView(
        ConfigRevisionId revision,
        long prestigeLevel,
        boolean enabled,
        String moneyRequirement,
        String moneyCost,
        int rewards,
        String rewardAmount,
        String scaling,
        boolean moneyAvailable,
        boolean moneyEditable,
        boolean rewardAvailable,
        boolean rewardEditable) {
    public PrestigeLevelConfigurationView {
        revision = Objects.requireNonNull(revision, "revision");
        moneyRequirement = Objects.requireNonNull(moneyRequirement, "Money requirement");
        moneyCost = Objects.requireNonNull(moneyCost, "Money cost");
        rewardAmount = Objects.requireNonNull(rewardAmount, "reward amount");
        scaling = Objects.requireNonNull(scaling, "scaling");
        if (prestigeLevel < 1 || rewards < 0) {
            throw new IllegalArgumentException("Prestige level must be positive and rewards cannot be negative");
        }
    }
}
