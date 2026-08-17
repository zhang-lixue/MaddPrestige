package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class DatabaseDiagnosticProbe implements DiagnosticProbe {
    private final Supplier<CompletionStage<DatabaseHealth>> health;

    public DatabaseDiagnosticProbe(Supplier<CompletionStage<DatabaseHealth>> health) {
        this.health = Objects.requireNonNull(health, "health");
    }

    @Override
    public Set<DiagnosticDomain> domains() {
        return Set.of(DiagnosticDomain.DATABASE_MIGRATIONS);
    }

    @Override
    public CompletionStage<List<DiagnosticFinding>> inspect() {
        return health.get().handle((status, failure) -> {
            if (failure != null || status == null || !status.reachable()) {
                return List.of(new DiagnosticFinding("database.unreachable", DiagnosticSeverity.BLOCKED,
                        "database", "database.connection", "The configured database is not reachable.",
                        "Restore the configured backend; credentials and connection strings are never displayed."));
            }
            if (!status.migrationsCurrent()) {
                return List.of(new DiagnosticFinding("database.migrations.pending", DiagnosticSeverity.BLOCKED,
                        "database", "database.schema", status.backend() + " has pending migrations: "
                                + status.detail(), "Run the documented migration/preflight workflow before apply."));
            }
            return List.of(new DiagnosticFinding("database.healthy", DiagnosticSeverity.HEALTHY,
                    "database", "database.schema", status.backend() + " is reachable and schema-current.",
                    "No action required."));
        });
    }
}
