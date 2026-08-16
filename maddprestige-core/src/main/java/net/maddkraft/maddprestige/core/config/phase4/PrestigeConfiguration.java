package net.maddkraft.maddprestige.core.config.phase4;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.StageId;

public record PrestigeConfiguration(
        boolean enabled,
        Set<StageId> requiredStages,
        StageId resetStage,
        long currentCountIncrement,
        long lifetimeCountIncrement,
        PrestigeLimit limit,
        Duration cooldown,
        Optional<RequirementId> requirementTreeId,
        List<CostId> costIds,
        List<RewardId> rewardIds,
        Optional<String> scalingProfileId,
        Optional<String> catchUpProfileId,
        ResetPreservePolicy resetPolicy,
        boolean externalResetsEnabled) {
    public PrestigeConfiguration {
        requiredStages = Set.copyOf(Objects.requireNonNull(requiredStages, "required stages"));
        resetStage = Objects.requireNonNull(resetStage, "reset stage");
        if (currentCountIncrement < 1 || lifetimeCountIncrement < 1) {
            throw new IllegalArgumentException("Prestige increments must be positive");
        }
        limit = Objects.requireNonNull(limit, "limit");
        cooldown = Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("Prestige cooldown cannot be negative");
        }
        requirementTreeId = Objects.requireNonNull(requirementTreeId, "requirement tree ID");
        costIds = List.copyOf(Objects.requireNonNull(costIds, "cost IDs"));
        rewardIds = List.copyOf(Objects.requireNonNull(rewardIds, "reward IDs"));
        scalingProfileId = Objects.requireNonNull(scalingProfileId, "scaling profile ID");
        catchUpProfileId = Objects.requireNonNull(catchUpProfileId, "catch-up profile ID");
        resetPolicy = Objects.requireNonNull(resetPolicy, "reset policy");
        if (enabled && requiredStages.isEmpty()) {
            throw new IllegalArgumentException("Enabled Prestige requires at least one source stage");
        }
        if (costIds.stream().distinct().count() != costIds.size()
                || rewardIds.stream().distinct().count() != rewardIds.size()) {
            throw new IllegalArgumentException("Prestige cost and reward references must be unique");
        }
        if (resetPolicy.disposition(ResetComponent.PROGRESSION_STAGE) != ResetDisposition.RESET) {
            throw new IllegalArgumentException("Prestige progression stage must use RESET");
        }
        if (resetPolicy.disposition(ResetComponent.HISTORICAL_STATISTICS) != ResetDisposition.PRESERVE) {
            throw new IllegalArgumentException("Historical/lifetime statistics cannot be reset");
        }
    }

    public static PrestigeConfiguration disabled() {
        return new PrestigeConfiguration(false, Set.of(), new StageId("disabled"), 1, 1,
                PrestigeLimit.unlimited(), Duration.ZERO, Optional.empty(), List.of(), List.of(), Optional.empty(),
                Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
    }
}
