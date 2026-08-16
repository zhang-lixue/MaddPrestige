package net.maddkraft.maddprestige.integrations.vault;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.CostPreflight;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.milkbowl.vault.economy.EconomyResponse;

/** Vault withdrawal adapter. Replay and compensation are deliberately not claimed by the provider API. */
public final class VaultEconomyCostProvider implements CostProvider {
    private static final ActionCharacteristics CHARACTERISTICS =
            new ActionCharacteristics(false, false, false, true);
    private final VaultEconomyBinding binding;
    private final ProviderDescriptor descriptor;

    public VaultEconomyCostProvider(VaultEconomyBinding binding, String implementationVersion) {
        this.binding = binding;
        descriptor = VaultProviderDescriptors.descriptor(VaultProviderDescriptors.COST, "cost", implementationVersion);
    }

    @Override
    public ActionCharacteristics characteristics(CostDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(CostDefinition definition) {
        String error = validateDefinition(definition);
        return error == null ? ValidationReport.VALID : ValidationReport.of(List.of(new ValidationFinding(
                "vault.cost.invalid", ValidationSeverity.ERROR, "costs." + definition.id().value(), error,
                "The economy cost cannot be planned.", "Use a positive, exactly representable currency amount.")));
    }

    @Override
    public CompletionStage<CostPreflight> preflight(PlannedCost proposed) {
        return preflightBatch(List.of(proposed)).thenApply(List::getFirst);
    }

    @Override
    public CompletionStage<List<CostPreflight>> preflightBatch(List<PlannedCost> proposed) {
        List<PlannedCost> immutable = List.copyOf(proposed);
        if (immutable.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String batchError = validateBatch(immutable);
        if (batchError != null) {
            return CompletableFuture.completedFuture(immutable.stream()
                    .map(ignored -> CostPreflight.blocked(batchError)).toList());
        }
        return binding.scheduler().call(() -> {
            if (!binding.available()) {
                return immutable.stream().map(ignored -> CostPreflight.unavailable(
                        "Vault economy binding is unavailable")).toList();
            }
            try {
                BigDecimal total = immutable.stream().map(cost -> amount(cost.definition()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                double vaultTotal = binding.exactAmount(total);
                boolean sufficient = binding.economy().has(binding.player(immutable.getFirst().playerId()), vaultTotal);
                return immutable.stream().map(cost -> sufficient ? CostPreflight.ready(cost)
                        : CostPreflight.blocked("Vault balance is insufficient for the aggregate cost")).toList();
            } catch (IllegalArgumentException exception) {
                return immutable.stream().map(ignored -> CostPreflight.blocked(exception.getMessage())).toList();
            }
        });
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedCost plannedCost) {
        return binding.scheduler().call(() -> {
            if (!binding.available()) {
                return ActionExecutionResult.failed("Vault economy binding disappeared before execution");
            }
            double requested;
            try {
                requested = binding.exactAmount(amount(plannedCost.definition()));
            } catch (IllegalArgumentException exception) {
                return ActionExecutionResult.failed(exception.getMessage());
            }
            EconomyResponse response = binding.economy().withdrawPlayer(binding.player(plannedCost.playerId()), requested);
            if (!response.transactionSuccess()) {
                return ActionExecutionResult.failed("Vault withdrawal failed: " + response.errorMessage);
            }
            if (BigDecimal.valueOf(response.amount).compareTo(BigDecimal.valueOf(requested)) != 0) {
                return ActionExecutionResult.uncertain("Vault reported a successful withdrawal with a mismatched amount");
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

    private String validateDefinition(CostDefinition definition) {
        try {
            if (!VaultProviderDescriptors.COST.equals(definition.providerId())
                    || !"vault_economy".equals(definition.type())) {
                return "Cost must target vault_economy_cost with type vault_economy";
            }
            VaultEconomyBinding.structuralAmount(amount(definition));
            return null;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    private String validateBatch(List<PlannedCost> proposed) {
        PlannedCost first = proposed.getFirst();
        for (PlannedCost cost : proposed) {
            String definitionError = validateDefinition(cost.definition());
            if (definitionError != null) {
                return "Vault aggregate preflight contains an invalid cost: " + definitionError;
            }
            if (!first.playerId().equals(cost.playerId())) {
                return "Vault aggregate preflight requires one player UUID";
            }
            if (!first.operationId().equals(cost.operationId())) {
                return "Vault aggregate preflight requires one canonical operation ID";
            }
            if (!first.configRevision().equals(cost.configRevision())) {
                return "Vault aggregate preflight requires one configuration revision";
            }
            if (first.providerGeneration() != cost.providerGeneration()) {
                return "Vault aggregate preflight requires one sealed provider generation";
            }
        }
        return null;
    }

    private static BigDecimal amount(CostDefinition definition) {
        if (definition.amount().type() != MetricValueType.CURRENCY_AMOUNT) {
            throw new IllegalArgumentException("Vault costs require CURRENCY_AMOUNT values");
        }
        return definition.amount().asNumber();
    }
}
