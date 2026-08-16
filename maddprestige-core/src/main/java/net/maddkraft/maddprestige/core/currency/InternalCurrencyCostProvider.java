package net.maddkraft.maddprestige.core.currency;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.CostPreflight;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.cost.NativeRecoverableCostProvider;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.api.value.ExactDecimal;

/** Native exact-decimal debit provider backed by the idempotent internal ledger. */
public final class InternalCurrencyCostProvider implements NativeRecoverableCostProvider {
    public static final String TYPE = "internal-currency";
    public static final String CURRENCY_ID = "currency-id";
    private static final ActionCharacteristics CHARACTERISTICS = new ActionCharacteristics(true, true, true, false);
    private static final Actor SYSTEM = new Actor("system", Optional.empty(), "MaddPrestige");
    private final ProviderDescriptor descriptor;
    private final java.util.function.Supplier<Map<CurrencyId, CurrencyDefinition>> definitions;
    private final CurrencyLedgerStore store;
    private final InternalCurrencyService service;
    private final Clock clock;

    public InternalCurrencyCostProvider(
            ProviderId id,
            String ownerIdentity,
            java.util.function.Supplier<Map<CurrencyId, CurrencyDefinition>> definitions,
            CurrencyLedgerStore store,
            Clock clock) {
        descriptor = new ProviderDescriptor(Objects.requireNonNull(id, "provider ID"),
                Objects.requireNonNull(ownerIdentity, "owner identity"), "1", "phase4", List.of(), List.of(
                        new CapabilityDescriptor("internal-currency-cost", "cost",
                                "Exact internal currency debit", Map.of())));
        this.definitions = Objects.requireNonNull(definitions, "currency definitions");
        this.store = Objects.requireNonNull(store, "currency store");
        this.clock = Objects.requireNonNull(clock, "clock");
        service = new InternalCurrencyService(definitions, store, clock);
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
    public ActionCharacteristics characteristics(CostDefinition definition) {
        return CHARACTERISTICS;
    }

    @Override
    public ValidationReport validate(CostDefinition definition) {
        boolean valid;
        try {
            valid = TYPE.equals(definition.type()) && definition.amount().type().isNumeric()
                    && definitions.get().containsKey(currencyId(definition.metadata()));
        } catch (RuntimeException exception) {
            valid = false;
        }
        return valid ? ValidationReport.VALID : invalid();
    }

    @Override
    public CompletionStage<CostPreflight> preflight(PlannedCost proposed) {
        return preflightBatch(List.of(proposed)).thenApply(List::getFirst);
    }

    @Override
    public CompletionStage<List<CostPreflight>> preflightBatch(List<PlannedCost> proposed) {
        List<PlannedCost> immutable = List.copyOf(proposed);
        ArrayList<CostPreflight> result = new ArrayList<>();
        Map<CurrencyId, ExactDecimal> totals = new java.util.LinkedHashMap<>();
        try {
            for (PlannedCost cost : immutable) {
                CurrencyId id = currencyId(cost.definition().metadata());
                totals.merge(id, ExactDecimal.of(cost.definition().amount().asNumber()), ExactDecimal::add);
            }
            boolean affordable = immutable.isEmpty() || totals.entrySet().stream().allMatch(entry -> store.balance(
                    immutable.getFirst().playerId(), entry.getKey()).compareTo(entry.getValue()) >= 0);
            immutable.forEach(cost -> result.add(affordable ? CostPreflight.ready(cost)
                    : CostPreflight.blocked("Insufficient internal currency balance")));
        } catch (RuntimeException exception) {
            immutable.forEach(cost -> result.add(CostPreflight.unavailable(exception.getMessage())));
        }
        return CompletableFuture.completedFuture(List.copyOf(result));
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedCost plannedCost) {
        try {
            CurrencyMutationResult result = service.spend(plannedCost.operationId(), plannedCost.actionId(),
                    plannedCost.playerId(), currencyId(plannedCost.definition().metadata()),
                    ExactDecimal.of(plannedCost.definition().amount().asNumber()), SYSTEM, "cost-engine",
                    plannedCost.definition().displayName(), plannedCost.configRevision());
            return CompletableFuture.completedFuture(result.replay()
                    ? ActionExecutionResult.unchanged() : ActionExecutionResult.applied());
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(exception.getMessage()));
        }
    }

    @Override
    public CompletionStage<ActionExecutionResult> compensate(PlannedCost plannedCost) {
        try {
            CurrencyMutationResult result = service.earn(plannedCost.operationId(),
                    "compensate-" + plannedCost.actionId(), plannedCost.playerId(),
                    currencyId(plannedCost.definition().metadata()),
                    ExactDecimal.of(plannedCost.definition().amount().asNumber()), SYSTEM, "cost-compensation",
                    plannedCost.definition().displayName(), plannedCost.configRevision());
            return CompletableFuture.completedFuture(result.replay()
                    ? ActionExecutionResult.unchanged() : ActionExecutionResult.applied());
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(exception.getMessage()));
        }
    }

    private static CurrencyId currencyId(Map<String, String> metadata) {
        return new CurrencyId(Objects.requireNonNull(metadata.get(CURRENCY_ID), "currency-id metadata"));
    }

    private static ValidationReport invalid() {
        return ValidationReport.of(List.of(new ValidationFinding("currency.cost.invalid", ValidationSeverity.ERROR,
                "currency", "Internal currency cost is invalid.", "The cost fails closed.",
                "Use internal-currency, a numeric amount, and a configured currency-id.")));
    }
}
