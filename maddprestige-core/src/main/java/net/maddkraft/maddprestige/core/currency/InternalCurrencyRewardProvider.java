package net.maddkraft.maddprestige.core.currency;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.PlannedReward;
import net.maddkraft.maddprestige.api.reward.NativeRecoverableRewardProvider;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardPreflight;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

/** Native exact-decimal credit provider backed by the idempotent internal ledger. */
public final class InternalCurrencyRewardProvider implements NativeRecoverableRewardProvider {
    private static final ActionCharacteristics CHARACTERISTICS = new ActionCharacteristics(true, false, true, false);
    private static final Actor SYSTEM = new Actor("system", Optional.empty(), "MaddPrestige");
    private final ProviderDescriptor descriptor;
    private final java.util.function.Supplier<Map<CurrencyId, CurrencyDefinition>> definitions;
    private final InternalCurrencyService service;
    private final Clock clock;

    public InternalCurrencyRewardProvider(
            ProviderId id,
            String ownerIdentity,
            java.util.function.Supplier<Map<CurrencyId, CurrencyDefinition>> definitions,
            CurrencyLedgerStore store,
            Clock clock) {
        descriptor = new ProviderDescriptor(Objects.requireNonNull(id, "provider ID"),
                Objects.requireNonNull(ownerIdentity, "owner identity"), "1", "phase4", List.of(), List.of(
                        new CapabilityDescriptor("internal-currency-reward", "reward",
                                "Exact internal currency credit", Map.of())));
        this.definitions = Objects.requireNonNull(definitions, "currency definitions");
        this.clock = Objects.requireNonNull(clock, "clock");
        service = new InternalCurrencyService(definitions, Objects.requireNonNull(store, "currency store"), clock);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth(ProviderHealthState.AVAILABLE, "internal.ready",
                "Internal currency ledger is configured", clock.instant());
    }

    @Override
    public ActionCharacteristics characteristics(RewardDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(RewardDefinition definition) {
        boolean valid;
        try {
            valid = InternalCurrencyCostProvider.TYPE.equals(definition.type())
                    && definition.value().type().isNumeric()
                    && definitions.get().containsKey(currencyId(definition.metadata()));
        } catch (RuntimeException exception) {
            valid = false;
        }
        return valid ? ValidationReport.VALID : ValidationReport.of(List.of(new ValidationFinding(
                "currency.reward.invalid", ValidationSeverity.ERROR, "currency",
                "Internal currency reward is invalid.", "The reward fails closed.",
                "Use internal-currency, a numeric value, and a configured currency-id.")));
    }

    @Override
    public CompletionStage<RewardPreflight> preflight(PlannedReward proposed) {
        return validate(proposed.definition()).hasErrors()
                ? CompletableFuture.completedFuture(RewardPreflight.invalid("Invalid internal currency reward"))
                : CompletableFuture.completedFuture(RewardPreflight.ready(proposed));
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedReward plannedReward) {
        try {
            CurrencyMutationResult result = service.earn(plannedReward.operationId(), plannedReward.actionId(),
                    plannedReward.playerId(), currencyId(plannedReward.definition().metadata()),
                    ExactDecimal.of(plannedReward.definition().value().asNumber()), SYSTEM, "reward-engine",
                    plannedReward.definition().displayName(), plannedReward.configRevision());
            return CompletableFuture.completedFuture(result.replay()
                    ? ActionExecutionResult.unchanged() : ActionExecutionResult.applied());
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(exception.getMessage()));
        }
    }

    private static CurrencyId currencyId(Map<String, String> metadata) {
        return new CurrencyId(Objects.requireNonNull(metadata.get(InternalCurrencyCostProvider.CURRENCY_ID),
                "currency-id metadata"));
    }

}
