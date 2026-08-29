package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.cost.CostProvider;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.reward.RewardProvider;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

/** Evaluates Phase 6 operational state supplied by the runtime/persistence composition. */
public final class PhaseSixOperationalDiagnosticProbe implements DiagnosticProbe {
    private static final Set<DiagnosticDomain> DOMAINS = Set.of(
            DiagnosticDomain.SCHEMA_VERSION,
            DiagnosticDomain.REQUIRED_METRICS,
            DiagnosticDomain.COST_PROVIDERS,
            DiagnosticDomain.REWARD_PROVIDERS,
            DiagnosticDomain.PENDING_OPERATIONS,
            DiagnosticDomain.OPERATION_RECONCILIATION,
            DiagnosticDomain.ORPHANED_PLAYER_STATE,
            DiagnosticDomain.IMMUTABLE_ID_INTEGRITY,
            DiagnosticDomain.ENTITLEMENT_INTEGRITY,
            DiagnosticDomain.PLACEHOLDER_API,
            DiagnosticDomain.SCHEDULER_CACHE,
            DiagnosticDomain.FLUSH_RECONCILIATION,
            DiagnosticDomain.INTEGRATION_CAPABILITY);

    private final ProviderRegistry providers;
    private final Supplier<CompletionStage<PhaseSixOperationalSnapshot>> source;

    public PhaseSixOperationalDiagnosticProbe(
            ProviderRegistry providers,
            Supplier<CompletionStage<PhaseSixOperationalSnapshot>> source) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Set<DiagnosticDomain> domains() {
        return DOMAINS;
    }

    @Override
    public CompletionStage<List<DiagnosticFinding>> inspect() {
        return source.get().thenApply(this::inspect);
    }

    private List<DiagnosticFinding> inspect(PhaseSixOperationalSnapshot snapshot) {
        ArrayList<DiagnosticFinding> findings = new ArrayList<>();
        if (snapshot.schemaVersion() != snapshot.expectedSchemaVersion()) {
            findings.add(blocked("config.schema_version_mismatch", "configuration", "schema-version",
                    "Active schema version " + snapshot.schemaVersion() + " does not match expected version "
                            + snapshot.expectedSchemaVersion() + ".",
                    "Migrate and recompile the canonical configuration before enabling execution."));
        } else {
            findings.add(healthy("config.schema_current", "configuration", "schema-version",
                    "Active schema version " + snapshot.schemaVersion() + " is current."));
        }
        references(findings, snapshot.requiredMetrics(), MetricProvider.class, "requirement.metric", "metric");
        references(findings, snapshot.costProviders(), CostProvider.class, "cost.provider", "cost");
        references(findings, snapshot.rewardProviders(), RewardProvider.class, "reward.provider", "reward");
        if (snapshot.requiredMetrics().isEmpty()) {
            findings.add(healthy("requirement.metrics.none", "requirement", "requirements",
                    "No active requirement metric references need provider validation."));
        }
        if (snapshot.costProviders().isEmpty()) {
            findings.add(healthy("cost.providers.none", "cost", "costs",
                    "No active cost provider references need validation."));
        }
        if (snapshot.rewardProviders().isEmpty()) {
            findings.add(healthy("reward.providers.none", "reward", "rewards",
                    "No active reward provider references need validation."));
        }
        snapshot.pendingOperations().forEach((operation, state) -> findings.add(new DiagnosticFinding(
                "operation.pending", DiagnosticSeverity.WARNING, "operation", "operations." + operation,
                "Operation " + operation + " remains incomplete in state " + state + ".",
                "Inspect durable action state and use the documented recovery workflow; do not replay blindly.")));
        if (snapshot.pendingOperations().isEmpty()) {
            findings.add(healthy("operation.pending.none", "operation", "operations",
                    "No incomplete durable operation was reported."));
        }
        snapshot.reconciliationOperations().forEach((operation, state) -> findings.add(blocked(
                "operation.reconciliation_required", "operation-reconciliation", "operations." + operation,
                "Operation " + operation + " requires reconciliation: " + state,
                "Reconcile durable internal/external evidence before allowing a conflicting operation.")));
        if (snapshot.reconciliationOperations().isEmpty()) {
            findings.add(healthy("operation.reconciliation.none", "operation-reconciliation", "operations",
                    "No operation reconciliation is pending."));
        }
        integrity(findings, snapshot.immutableIdIssues(), "config.immutable_id_duplicate", "immutable IDs");
        if (snapshot.immutableIdIssues().isEmpty()) {
            findings.add(healthy("config.immutable_ids_valid", "configuration", "immutable-ids",
                    "No duplicate immutable ID was detected by the canonical compiler/source."));
        }
        integrity(findings, snapshot.entitlementIssues(), "entitlement.integrity_invalid", "entitlements");
        if (snapshot.entitlementIssues().isEmpty()) {
            findings.add(healthy("entitlement.integrity_valid", "entitlement", "entitlements",
                    "Configured entitlement definitions and contributions passed integrity checks."));
        }
        subsystem(findings, "placeholderapi", "integrations.placeholderapi", snapshot.placeholderApi());
        subsystem(findings, "scheduler_cache", "runtime.scheduler-cache", snapshot.schedulerCache());
        subsystem(findings, "flush_reconciliation", "runtime.flush-reconciliation",
                snapshot.flushReconciliation());
        if (snapshot.integrationCapabilities().isEmpty()) {
            findings.add(healthy("integration.capabilities_valid", "integration", "integrations",
                    "No enabled integration reports an unsupported capability."));
        } else {
            snapshot.integrationCapabilities().forEach((id, state) -> subsystem(findings,
                    "integration." + id, "integrations." + id, state));
        }
        return List.copyOf(findings);
    }

    private void references(
            List<DiagnosticFinding> findings,
            List<DiagnosticProviderReference> references,
            Class<?> capability,
            String code,
            String label) {
        references.forEach(reference -> {
            var provider = providers.provider(reference.providerId());
            var snapshot = providers.snapshots().stream()
                    .filter(candidate -> candidate.descriptor().id().equals(reference.providerId())).findFirst();
            boolean activeHealthy = snapshot.filter(candidate -> candidate.activation() == ActivationState.ACTIVE)
                    .filter(candidate -> candidate.health().state() == ProviderHealthState.AVAILABLE
                            || candidate.health().state() == ProviderHealthState.ACTIVE).isPresent();
            boolean supported = provider.filter(capability::isInstance).isPresent();
            boolean metricPresent = reference.metricId().isEmpty() || provider.filter(MetricProvider.class::isInstance)
                    .map(MetricProvider.class::cast).map(metricProvider -> {
                        try {
                            return metricProvider.metrics().stream().anyMatch(metric ->
                                    metric.metricId().equals(reference.metricId().orElseThrow()));
                        } catch (RuntimeException exception) {
                            return false;
                        }
                    }).orElse(false);
            if (!activeHealthy || !supported || !metricPresent) {
                findings.add(blocked(code + ".unavailable", label + "-provider", reference.path(),
                        "Configured " + label + " provider/metric is unavailable: "
                                + reference.providerId().value() + reference.metricId()
                                        .map(metric -> "/" + metric.value()).orElse(""),
                        "Restore and activate the exact provider capability or edit this canonical path."));
            } else {
                findings.add(healthy(code + ".healthy", label + "-provider", reference.path(),
                        "Configured " + label + " provider capability is available."));
            }
        });
    }

    private static void integrity(
            List<DiagnosticFinding> findings,
            List<DiagnosticIntegrityIssue> issues,
            String code,
            String component) {
        issues.forEach(issue -> findings.add(blocked(code, component, issue.path(), issue.detail(),
                "Correct the duplicate or inconsistent canonical definition and apply a new revision.")));
    }

    private static void subsystem(
            List<DiagnosticFinding> findings,
            String code,
            String path,
            DiagnosticSubsystemState state) {
        String normalized = code.replace('.', '_');
        findings.add(new DiagnosticFinding(normalized + "." + state.severity().name().toLowerCase(
                java.util.Locale.ROOT), state.severity(), code, path, state.detail(), switch (state.severity()) {
                    case HEALTHY -> "No action required.";
                    case DEFERRED -> "The capability is unsupported or intentionally not checked; keep it disabled.";
                    case WARNING -> "Restore the subsystem and rerun Doctor before relying on cached output.";
                    case BLOCKED -> "Restore the subsystem before executing dependent operations.";
                }));
    }

    private static DiagnosticFinding blocked(
            String code,
            String component,
            String path,
            String summary,
            String remediation) {
        return new DiagnosticFinding(code, DiagnosticSeverity.BLOCKED, component, path, summary, remediation);
    }

    private static DiagnosticFinding healthy(String code, String component, String path, String summary) {
        return new DiagnosticFinding(code, DiagnosticSeverity.HEALTHY, component, path, summary,
                "No action required.");
    }
}
