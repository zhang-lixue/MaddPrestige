package net.maddkraft.maddprestige.integrations.griefprevention;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.MutableProviderHealth;

/** Synchronous, post-verified bonus claim-block reward. */
public final class GriefPreventionClaimBlockRewardProvider implements RewardProvider {
    public static final String TYPE = "bonus_claim_blocks";
    private static final ActionCharacteristics CHARACTERISTICS =
            new ActionCharacteristics(false, false, false, true);

    private final GriefPreventionAccess access;
    private final IntegrationTaskScheduler scheduler;
    private final MutableProviderHealth health;
    private final ProviderDescriptor descriptor;

    public GriefPreventionClaimBlockRewardProvider(GriefPreventionAccess access,
            IntegrationTaskScheduler scheduler, MutableProviderHealth health, String detectedVersion) {
        this.access = java.util.Objects.requireNonNull(access, "access");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.health = java.util.Objects.requireNonNull(health, "health");
        descriptor = GriefPreventionProviderDescriptors.descriptor(
                GriefPreventionProviderDescriptors.REWARD, "reward", detectedVersion);
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        try {
            amount(definition);
            return ValidationReport.VALID;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return ValidationReport.of(List.of(new ValidationFinding("griefprevention.reward.invalid",
                    ValidationSeverity.ERROR, "rewards." + definition.id().value(), exception.getMessage(),
                    "The GriefPrevention reward cannot be planned.",
                    "Use a positive integral COUNT bonus within the signed 32-bit range.")));
        }
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        return scheduler.call(() -> {
            if (!health.isUsable()) {
                return RewardPreflight.unavailable("GriefPrevention binding is unavailable");
            }
            try {
                int amount = amount(proposed.definition());
                GriefPreventionAccess.ClaimBlockSnapshot snapshot = access.read(proposed.playerId());
                Math.addExact(snapshot.bonus(), amount);
                return RewardPreflight.ready(proposed);
            } catch (ArithmeticException exception) {
                return RewardPreflight.invalid("Bonus claim-block reward would overflow");
            } catch (RuntimeException exception) {
                return RewardPreflight.unavailable("GriefPrevention preflight failed");
            }
        });
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        return scheduler.call(() -> {
            if (!health.isUsable()) {
                return ActionExecutionResult.failed("GriefPrevention binding disappeared before execution");
            }
            int requested;
            try {
                requested = amount(plannedReward.definition());
                GriefPreventionAccess.ClaimBlockSnapshot before = access.read(plannedReward.playerId());
                Math.addExact(before.bonus(), requested);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                return ActionExecutionResult.failed(exception.getMessage());
            } catch (RuntimeException exception) {
                return ActionExecutionResult.failed("GriefPrevention failed before reward mutation");
            }
            try {
                access.addBonus(plannedReward.playerId(), requested);
                return ActionExecutionResult.applied();
            } catch (RuntimeException exception) {
                return ActionExecutionResult.uncertain(
                        "GriefPrevention may have applied the non-idempotent reward; automatic replay is forbidden");
            }
        });
    }

    private static int amount(RewardDefinition definition) {
        if (!GriefPreventionProviderDescriptors.REWARD.equals(definition.providerId())
                || !TYPE.equals(definition.type()) || definition.value().type() != MetricValueType.COUNT) {
            throw new IllegalArgumentException(
                    "Reward must target griefprevention_claim_blocks_reward/bonus_claim_blocks with COUNT value");
        }
        BigDecimal amount = definition.value().asNumber();
        int exact = amount.intValueExact();
        if (exact <= 0) {
            throw new IllegalArgumentException("Bonus claim-block reward must be positive");
        }
        return exact;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return health.get();
    }
}
