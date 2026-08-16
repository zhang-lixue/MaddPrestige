package net.maddkraft.maddprestige.integrations.vault;

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
import net.milkbowl.vault.economy.EconomyResponse;

/** Vault deposit adapter whose exactly-once authority remains the canonical MaddPrestige operation journal. */
public final class VaultEconomyRewardProvider implements RewardProvider {
    private static final ActionCharacteristics CHARACTERISTICS =
            new ActionCharacteristics(false, false, false, true);
    private final VaultEconomyBinding binding;
    private final ProviderDescriptor descriptor;

    public VaultEconomyRewardProvider(VaultEconomyBinding binding, String implementationVersion) {
        this.binding = binding;
        descriptor = VaultProviderDescriptors.descriptor(VaultProviderDescriptors.REWARD, "reward",
                implementationVersion);
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        try {
            if (!VaultProviderDescriptors.REWARD.equals(definition.providerId())
                    || !"vault_economy".equals(definition.type())) {
                throw new IllegalArgumentException(
                        "Reward must target vault_economy_reward with type vault_economy");
            }
            VaultEconomyBinding.structuralAmount(amount(definition));
            return ValidationReport.VALID;
        } catch (IllegalArgumentException exception) {
            return ValidationReport.of(List.of(new ValidationFinding("vault.reward.invalid", ValidationSeverity.ERROR,
                    "rewards." + definition.id().value(), exception.getMessage(),
                    "The Vault reward cannot be planned.", "Use a positive, exactly representable currency amount.")));
        }
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        return binding.scheduler().call(() -> {
            if (!binding.available()) {
                return RewardPreflight.unavailable("Vault economy binding is unavailable");
            }
            try {
                binding.exactAmount(amount(proposed.definition()));
                return RewardPreflight.ready(proposed);
            } catch (IllegalArgumentException exception) {
                return RewardPreflight.invalid(exception.getMessage());
            }
        });
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        return binding.scheduler().call(() -> {
            if (!binding.available()) {
                return ActionExecutionResult.failed("Vault economy binding disappeared before execution");
            }
            double requested;
            try {
                requested = binding.exactAmount(amount(plannedReward.definition()));
            } catch (IllegalArgumentException exception) {
                return ActionExecutionResult.failed(exception.getMessage());
            }
            EconomyResponse response = binding.economy().depositPlayer(binding.player(plannedReward.playerId()),
                    requested);
            if (!response.transactionSuccess()) {
                return ActionExecutionResult.failed("Vault deposit failed: " + response.errorMessage);
            }
            if (BigDecimal.valueOf(response.amount).compareTo(BigDecimal.valueOf(requested)) != 0) {
                return ActionExecutionResult.uncertain("Vault reported a successful deposit with a mismatched amount");
            }
            return ActionExecutionResult.applied();
        });
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return binding.health().get();
    }

    private static BigDecimal amount(RewardDefinition definition) {
        if (definition.value().type() != MetricValueType.CURRENCY_AMOUNT) {
            throw new IllegalArgumentException("Vault rewards require CURRENCY_AMOUNT values");
        }
        return definition.value().asNumber();
    }
}
