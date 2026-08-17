package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore;

public final class ConfigurationHistoryDiagnosticProbe implements DiagnosticProbe {
    private final ConfigurationHistoryStore history;
    private final Executor worker;

    public ConfigurationHistoryDiagnosticProbe(ConfigurationHistoryStore history, Executor worker) {
        this.history = Objects.requireNonNull(history, "history");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Override
    public Set<DiagnosticDomain> domains() {
        return Set.of(DiagnosticDomain.CONFIGURATION_HISTORY);
    }

    @Override
    public CompletionStage<List<DiagnosticFinding>> inspect() {
        return CompletableFuture.supplyAsync(() -> {
            var recent = history.recent(20);
            ArrayList<DiagnosticFinding> findings = new ArrayList<>();
            recent.stream().filter(value -> value.status() == ConfigurationApplicationStatus.ATTEMPTED)
                    .forEach(value -> findings.add(new DiagnosticFinding("config.history.attempted",
                            DiagnosticSeverity.WARNING, "configuration-history", "config.history."
                                    + value.id().value(), "Configuration revision " + value.id().value()
                                    + " has no final application outcome.",
                            "Compare the active revision pointer, then reconcile this interrupted attempt.")));
            recent.stream().filter(value -> value.status() == ConfigurationApplicationStatus.FAILED).limit(1)
                    .forEach(value -> findings.add(new DiagnosticFinding("config.history.last_failed",
                            DiagnosticSeverity.WARNING, "configuration-history", "config.history."
                                    + value.id().value(), "A recent configuration apply failed safely.",
                            "Inspect revision " + value.id().value() + " and correct its validation/storage cause.")));
            return List.copyOf(findings);
        }, worker);
    }
}
