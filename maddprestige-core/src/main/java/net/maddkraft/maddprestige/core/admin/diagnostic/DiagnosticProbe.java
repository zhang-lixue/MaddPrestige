package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface DiagnosticProbe {
    CompletionStage<List<DiagnosticFinding>> inspect();

    default Set<DiagnosticDomain> domains() {
        return Set.of();
    }
}
