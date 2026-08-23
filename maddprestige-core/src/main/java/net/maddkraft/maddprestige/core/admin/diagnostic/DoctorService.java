package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.config.ActiveConfiguration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

public final class DoctorService {
    private final ProviderRegistry providers;
    private final Supplier<Optional<ActiveConfiguration>> active;
    private final List<DiagnosticProbe> probes;
    private final Clock clock;

    public DoctorService(
            ProviderRegistry providers,
            Supplier<Optional<ActiveConfiguration>> active,
            List<DiagnosticProbe> probes,
            Clock clock) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.active = Objects.requireNonNull(active, "active configuration");
        this.probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<DoctorReport> inspect(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.DOCTOR);
        ArrayList<DiagnosticFinding> immediate = new ArrayList<>();
        immediate.add(activeFinding());
        providers.snapshots().forEach(snapshot -> immediate.add(providerFinding(snapshot)));
        EnumSet<DiagnosticDomain> covered = EnumSet.of(DiagnosticDomain.ACTIVE_CONFIGURATION,
                DiagnosticDomain.PROVIDER_HEALTH);
        probes.forEach(probe -> covered.addAll(probe.domains()));
        CompletableFuture<?>[] futures = probes.stream().map(DiagnosticProbe::inspect)
                .map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).handle((ignored, failure) -> {
            ArrayList<DiagnosticFinding> findings = new ArrayList<>(immediate);
            for (int index = 0; index < futures.length; index++) {
                CompletableFuture<?> future = futures[index];
                try {
                    @SuppressWarnings("unchecked")
                    List<DiagnosticFinding> result = (List<DiagnosticFinding>) future.join();
                    findings.addAll(result);
                } catch (RuntimeException exception) {
                    findings.add(new DiagnosticFinding("doctor.probe.failed", DiagnosticSeverity.WARNING,
                            "diagnostics", "probe." + index, "A diagnostic probe could not complete.",
                            "Check debug logs and retry; doctor remains read-only."));
                }
            }
            for (DiagnosticDomain domain : DiagnosticDomain.values()) {
                if (!covered.contains(domain)) {
                    findings.add(notChecked(domain));
                }
            }
            findings.sort(Comparator.comparingInt((DiagnosticFinding finding) -> priority(finding.severity()))
                    .thenComparing(DiagnosticFinding::component).thenComparing(DiagnosticFinding::code));
            return new DoctorReport(overall(findings), findings, Instant.now(clock));
        });
    }

    private static DiagnosticFinding notChecked(DiagnosticDomain domain) {
        String segment = domain.pathSegment();
        return new DiagnosticFinding("doctor.not_checked." + segment, DiagnosticSeverity.DEFERRED,
                "diagnostic-coverage", "doctor.coverage." + segment,
                "The " + segment.replace('-', ' ') + " domain was not checked by this runtime composition.",
                "Connect the Phase 6-owned probe when that subsystem is applicable; do not treat this report as "
                        + "broadly healthy until then.");
    }

    private DiagnosticFinding activeFinding() {
        return active.get().map(configuration -> new DiagnosticFinding("config.active", DiagnosticSeverity.HEALTHY,
                "configuration", "active-revision", "Active configuration revision "
                        + configuration.revisionId().value() + " is loaded.", "No action required."))
                .orElseGet(() -> new DiagnosticFinding("config.inactive", DiagnosticSeverity.WARNING,
                        "configuration", "active-revision", "No V2 configuration revision is active.",
                        "Run the setup wizard or validate and apply a draft."));
    }

    private static DiagnosticFinding providerFinding(
            net.maddkraft.maddprestige.api.provider.ProviderSnapshot snapshot) {
        boolean active = snapshot.activation() == ActivationState.ACTIVE;
        boolean healthy = snapshot.health().state() == ProviderHealthState.AVAILABLE
                || snapshot.health().state() == ProviderHealthState.ACTIVE;
        DiagnosticSeverity severity = active && !healthy ? DiagnosticSeverity.BLOCKED
                : active ? DiagnosticSeverity.HEALTHY : DiagnosticSeverity.DEFERRED;
        String summary = snapshot.descriptor().id().value() + ": " + snapshot.health().state()
                + " — " + snapshot.health().reason();
        String remediation = active && !healthy
                ? "Restore the required dependency or remove its active configuration reference."
                : active ? "No action required; this active provider is healthy."
                        : "No action required for this dormant provider.";
        return new DiagnosticFinding("provider." + snapshot.health().code(), severity, "provider",
                "providers." + snapshot.descriptor().id().value(), summary, remediation);
    }

    private static DiagnosticSeverity overall(List<DiagnosticFinding> findings) {
        if (findings.stream().anyMatch(value -> value.severity() == DiagnosticSeverity.BLOCKED)) {
            return DiagnosticSeverity.BLOCKED;
        }
        if (findings.stream().anyMatch(value -> value.severity() == DiagnosticSeverity.WARNING)) {
            return DiagnosticSeverity.WARNING;
        }
        if (findings.stream().anyMatch(value -> value.severity() == DiagnosticSeverity.DEFERRED
                && !value.code().startsWith("provider."))) {
            return DiagnosticSeverity.DEFERRED;
        }
        return DiagnosticSeverity.HEALTHY;
    }

    private static int priority(DiagnosticSeverity severity) {
        return switch (severity) {
            case BLOCKED -> 0;
            case WARNING -> 1;
            case DEFERRED -> 2;
            case HEALTHY -> 3;
        };
    }
}
