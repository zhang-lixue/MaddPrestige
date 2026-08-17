package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.Objects;

public record DiagnosticFinding(
        String code,
        DiagnosticSeverity severity,
        String component,
        String path,
        String summary,
        String remediation) {
    public DiagnosticFinding {
        code = Objects.requireNonNull(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        component = Objects.requireNonNull(component, "component");
        path = Objects.requireNonNull(path, "path");
        summary = Objects.requireNonNull(summary, "summary");
        remediation = Objects.requireNonNull(remediation, "remediation");
    }
}
