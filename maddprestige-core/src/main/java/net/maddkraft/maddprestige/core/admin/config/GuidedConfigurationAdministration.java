package net.maddkraft.maddprestige.core.admin.config;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;

/** Narrow visual-editor authority backed by the canonical configuration administration workflow. */
public interface GuidedConfigurationAdministration {
    PrestigeLevelPage prestigeLevels(PermissionSubject subject, int pageIndex, int pageSize);

    PrestigeLevelConfigurationView prestigeLevel(PermissionSubject subject, long prestigeLevel);

    MoneyAmountPage moneyAmounts(PermissionSubject subject, long prestigeLevel, int pageIndex, int pageSize);

    GuidedNumericConfigurationInput moneyInput(PermissionSubject subject, long prestigeLevel);

    String validateMoneyInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedMoneyConfigurationReview> reviewMoney(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount);

    CompletionStage<GuidedMoneyConfigurationReview> reviewMoney(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedMoneyConfigurationResult> confirmMoney(PermissionSubject subject, UUID reviewId);

    RewardAmountPage rewardAmounts(PermissionSubject subject, long prestigeLevel, int pageIndex, int pageSize);

    GuidedNumericConfigurationInput rewardInput(PermissionSubject subject, long prestigeLevel);

    String validateRewardInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedRewardConfigurationReview> reviewReward(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount);

    CompletionStage<GuidedRewardConfigurationReview> reviewReward(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedRewardConfigurationResult> confirmReward(PermissionSubject subject, UUID reviewId);

    GuidedRequirementConfigurationView requirements(PermissionSubject subject, long prestigeLevel);

    GuidedNumericConfigurationInput totalSkillLevelInput(PermissionSubject subject, long prestigeLevel);

    String validateTotalSkillLevelInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newTarget,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedTotalSkillLevelReview> reviewTotalSkillLevel(
            PermissionSubject subject,
            long prestigeLevel,
            String newTarget,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedTotalSkillLevelResult> confirmTotalSkillLevel(
            PermissionSubject subject,
            UUID reviewId);

    GuidedScalingConfigurationView scaling(PermissionSubject subject, long prestigeLevel);

    GuidedNumericConfigurationInput scalingInput(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter);

    String validateScalingInput(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter,
            String newValue,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedScalingConfigurationReview> reviewScaling(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter,
            String newValue,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedScalingConfigurationResult> confirmScaling(PermissionSubject subject, UUID reviewId);

    GuidedNumericConfigurationInput scalingOverrideInput(PermissionSubject subject, long prestigeLevel);

    String validateScalingOverrideInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newValue,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedScalingOverrideReview> reviewScalingOverride(
            PermissionSubject subject,
            long prestigeLevel,
            String newValue,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedScalingOverrideReview> reviewScalingOverrideRemoval(
            PermissionSubject subject,
            long prestigeLevel,
            ConfigRevisionId expectedRevision);

    CompletionStage<GuidedScalingOverrideResult> confirmScalingOverride(
            PermissionSubject subject,
            UUID reviewId);
}
