package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.operation.OperationState;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionState;
import net.maddkraft.maddprestige.core.stage.StageTransitionLease;

public record PhaseSixOperationalSnapshot(
        int schemaVersion,
        int expectedSchemaVersion,
        List<DiagnosticProviderReference> requiredMetrics,
        List<DiagnosticProviderReference> costProviders,
        List<DiagnosticProviderReference> rewardProviders,
        Map<String, String> pendingOperations,
        Map<String, String> reconciliationOperations,
        Map<String, DiagnosticSubsystemState> stageTransitionLeases,
        Map<String, DiagnosticSubsystemState> configurationStageTransitions,
        Map<UUID, String> playerStages,
        Set<String> configuredStages,
        List<DiagnosticIntegrityIssue> immutableIdIssues,
        List<DiagnosticIntegrityIssue> entitlementIssues,
        DiagnosticSubsystemState placeholderApi,
        DiagnosticSubsystemState schedulerCache,
        DiagnosticSubsystemState flushReconciliation,
        Map<String, DiagnosticSubsystemState> integrationCapabilities) {
    public PhaseSixOperationalSnapshot {
        requiredMetrics = List.copyOf(Objects.requireNonNull(requiredMetrics, "required metrics"));
        costProviders = List.copyOf(Objects.requireNonNull(costProviders, "cost providers"));
        rewardProviders = List.copyOf(Objects.requireNonNull(rewardProviders, "reward providers"));
        pendingOperations = Map.copyOf(Objects.requireNonNull(pendingOperations, "pending operations"));
        reconciliationOperations = Map.copyOf(Objects.requireNonNull(reconciliationOperations,
                "reconciliation operations"));
        stageTransitionLeases = Map.copyOf(Objects.requireNonNull(stageTransitionLeases,
                "stage transition leases"));
        configurationStageTransitions = Map.copyOf(Objects.requireNonNull(configurationStageTransitions,
                "configuration stage transitions"));
        playerStages = Map.copyOf(Objects.requireNonNull(playerStages, "player stages"));
        configuredStages = Set.copyOf(Objects.requireNonNull(configuredStages, "configured stages"));
        immutableIdIssues = List.copyOf(Objects.requireNonNull(immutableIdIssues, "immutable ID issues"));
        entitlementIssues = List.copyOf(Objects.requireNonNull(entitlementIssues, "entitlement issues"));
        placeholderApi = Objects.requireNonNull(placeholderApi, "PlaceholderAPI state");
        schedulerCache = Objects.requireNonNull(schedulerCache, "scheduler/cache state");
        flushReconciliation = Objects.requireNonNull(flushReconciliation, "flush/reconciliation state");
        integrationCapabilities = Map.copyOf(Objects.requireNonNull(integrationCapabilities,
                "integration capabilities"));
        if (schemaVersion < 1 || expectedSchemaVersion < 1) {
            throw new IllegalArgumentException("Schema versions must be positive");
        }
    }

    public static Map<String, DiagnosticSubsystemState> diagnoseLeases(List<StageTransitionLease> leases) {
        LinkedHashMap<String, DiagnosticSubsystemState> states = new LinkedHashMap<>();
        for (StageTransitionLease lease : List.copyOf(leases)) {
            boolean unresolved = lease.ownerState().filter(OperationState.NEEDS_RECONCILIATION::equals).isPresent();
            DiagnosticSeverity severity = lease.abnormal() || unresolved
                    ? DiagnosticSeverity.BLOCKED : DiagnosticSeverity.WARNING;
            String detail = "Operation " + lease.operationId() + " participates in stage transition "
                    + lease.sourceStage().map(value -> value.value() + " → ").orElse("unknown source → ")
                    + lease.targetStage().value() + " with owner state "
                    + lease.ownerState().map(OperationState::name).orElse("MISSING") + ".";
            states.put(lease.operationId().toString(), new DiagnosticSubsystemState(severity, detail));
        }
        return Map.copyOf(states);
    }

    public static Map<String, DiagnosticSubsystemState> diagnoseTransitions(
            List<ConfigurationStageTransitionState> transitions) {
        LinkedHashMap<String, DiagnosticSubsystemState> states = new LinkedHashMap<>();
        for (ConfigurationStageTransitionState transition : List.copyOf(transitions)) {
            DiagnosticSeverity severity = transition.abnormal()
                    ? DiagnosticSeverity.BLOCKED : DiagnosticSeverity.WARNING;
            String stages = transition.reservedStages().entrySet().stream()
                    .map(entry -> entry.getKey().value() + "(" + entry.getValue() + ")")
                    .sorted().reduce((left, right) -> left + ", " + right).orElse("UNKNOWN");
            String detail = "Configuration revision " + transition.configurationRevision().value()
                    + " owns unsafe-stage reservation [" + stages + "] in state " + transition.status()
                    + " with history owner " + transition.ownerStatus().map(Enum::name).orElse("MISSING")
                    + " and complete-scope=" + transition.scopeComplete() + ".";
            states.put(transition.configurationRevision().value(), new DiagnosticSubsystemState(severity, detail));
        }
        return Map.copyOf(states);
    }
}
