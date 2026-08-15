package net.maddkraft.maddprestige.api.validation;

import java.util.Objects;

public record ValidationFinding(
        String code,
        ValidationSeverity severity,
        String path,
        String explanation,
        String consequence,
        String remediation) {
    public ValidationFinding {
        code = Objects.requireNonNull(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        path = Objects.requireNonNull(path, "path");
        explanation = Objects.requireNonNull(explanation, "explanation");
        consequence = Objects.requireNonNull(consequence, "consequence");
        remediation = Objects.requireNonNull(remediation, "remediation");
    }
}
