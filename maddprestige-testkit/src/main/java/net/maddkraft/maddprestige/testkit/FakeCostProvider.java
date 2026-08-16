package net.maddkraft.maddprestige.testkit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.action.ActionCharacteristics;
import net.maddkraft.maddprestige.api.action.ActionExecutionResult;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.cost.CostPreflight;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.cost.PlannedCost;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public final class FakeCostProvider extends FakeProvider implements CostProvider {
    private final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Set<String> applied = ConcurrentHashMap.newKeySet();
    private volatile ActionExecutionResult nextExecution;
    private volatile Runnable nextExecutionHook;
    private volatile Supplier<CompletionStage<List<CostPreflight>>> nextPreflight;
    private final ConcurrentLinkedQueue<Supplier<CompletionStage<ActionExecutionResult>>> compensations =
            new ConcurrentLinkedQueue<>();

    public FakeCostProvider(ProviderId providerId) {
        super(providerId, "cost", List.of(new CapabilityDescriptor("exact-cost", "cost",
                "Fake exact cost", Map.of())));
    }

    public void balance(UUID playerId, String amount) {
        balances.put(playerId, new BigDecimal(amount));
    }

    public BigDecimal balance(UUID playerId) {
        return balances.getOrDefault(playerId, BigDecimal.ZERO);
    }

    public int executionCount() {
        return applied.size();
    }

    public void nextExecution(ActionExecutionResult result) {
        nextExecution = result;
    }

    public void onNextExecution(Runnable hook) {
        nextExecutionHook = java.util.Objects.requireNonNull(hook, "execution hook");
    }

    public void nextCompensation(ActionExecutionResult result) {
        compensations.add(() -> CompletableFuture.completedFuture(result));
    }

    public void throwNextPreflight(RuntimeException failure) {
        nextPreflight = () -> {
            throw failure;
        };
    }

    public void failNextPreflight(RuntimeException failure) {
        nextPreflight = () -> CompletableFuture.failedFuture(failure);
    }

    public void failNextCompensation(RuntimeException failure) {
        compensations.add(() -> CompletableFuture.failedFuture(failure));
    }

    public void throwNextCompensation(RuntimeException failure) {
        compensations.add(() -> {
            throw failure;
        });
    }

    @Override
    public ActionCharacteristics characteristics(CostDefinition definition) {
        return new ActionCharacteristics(true, true, true, true);
    }

    @Override
    public ValidationReport validate(CostDefinition definition) {
        return ValidationReport.VALID;
    }

    @Override
    public CompletionStage<CostPreflight> preflight(PlannedCost proposed) {
        if (health().state() != ProviderHealthState.AVAILABLE && health().state() != ProviderHealthState.ACTIVE) {
            return CompletableFuture.completedFuture(CostPreflight.unavailable("Fake cost provider unavailable"));
        }
        return CompletableFuture.completedFuture(balance(proposed.playerId()).compareTo(
                proposed.definition().amount().asNumber()) >= 0
                ? CostPreflight.ready(proposed) : CostPreflight.blocked("Insufficient fake balance"));
    }

    @Override
    public CompletionStage<List<CostPreflight>> preflightBatch(List<PlannedCost> proposed) {
        Supplier<CompletionStage<List<CostPreflight>>> forced = nextPreflight;
        nextPreflight = null;
        if (forced != null) {
            return forced.get();
        }
        List<PlannedCost> immutable = List.copyOf(proposed);
        if (immutable.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        UUID playerId = immutable.getFirst().playerId();
        if (immutable.stream().anyMatch(cost -> !cost.playerId().equals(playerId))) {
            return CompletableFuture.completedFuture(immutable.stream()
                    .map(ignored -> CostPreflight.blocked("Fake batch contains multiple players")).toList());
        }
        if (health().state() != ProviderHealthState.AVAILABLE && health().state() != ProviderHealthState.ACTIVE) {
            return CompletableFuture.completedFuture(immutable.stream()
                    .map(ignored -> CostPreflight.unavailable("Fake cost provider unavailable")).toList());
        }
        BigDecimal total = immutable.stream().map(cost -> cost.definition().amount().asNumber())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean affordable = balance(playerId).compareTo(total) >= 0;
        return CompletableFuture.completedFuture(immutable.stream().map(cost -> affordable
                ? CostPreflight.ready(cost) : CostPreflight.blocked("Insufficient aggregate fake balance"))
                .toList());
    }

    @Override
    public CompletionStage<ActionExecutionResult> execute(PlannedCost plannedCost) {
        Runnable hook = nextExecutionHook;
        nextExecutionHook = null;
        ActionExecutionResult forced = nextExecution;
        nextExecution = null;
        if (forced != null) {
            if (forced.status() == net.maddkraft.maddprestige.api.action.ActionExecutionStatus.APPLIED) {
                apply(plannedCost);
            }
            if (hook != null) {
                hook.run();
            }
            return CompletableFuture.completedFuture(forced);
        }
        String key = key(plannedCost);
        if (!applied.add(key)) {
            if (hook != null) {
                hook.run();
            }
            return CompletableFuture.completedFuture(ActionExecutionResult.unchanged());
        }
        balances.compute(plannedCost.playerId(), (ignored, current) ->
                (current == null ? BigDecimal.ZERO : current).subtract(plannedCost.definition().amount().asNumber()));
        if (hook != null) {
            hook.run();
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.applied());
    }

    @Override
    public CompletionStage<ActionExecutionResult> compensate(PlannedCost plannedCost) {
        Supplier<CompletionStage<ActionExecutionResult>> forced = compensations.poll();
        if (forced != null) {
            return forced.get();
        }
        if (applied.remove(key(plannedCost))) {
            balances.merge(plannedCost.playerId(), plannedCost.definition().amount().asNumber(), BigDecimal::add);
            return CompletableFuture.completedFuture(ActionExecutionResult.applied());
        }
        return CompletableFuture.completedFuture(ActionExecutionResult.unchanged());
    }

    private void apply(PlannedCost cost) {
        if (applied.add(key(cost))) {
            balances.merge(cost.playerId(), cost.definition().amount().asNumber().negate(), BigDecimal::add);
        }
    }

    private static String key(PlannedCost cost) {
        return cost.operationId() + ":" + cost.actionId();
    }
}
