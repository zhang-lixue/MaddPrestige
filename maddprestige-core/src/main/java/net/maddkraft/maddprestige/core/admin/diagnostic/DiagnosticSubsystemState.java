package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.Objects;

public record DiagnosticSubsystemState(DiagnosticSeverity severity, String detail) {
    public DiagnosticSubsystemState {
        severity = Objects.requireNonNull(severity, "severity");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static DiagnosticSubsystemState healthy(String detail) {
        return new DiagnosticSubsystemState(DiagnosticSeverity.HEALTHY, detail);
    }
}
