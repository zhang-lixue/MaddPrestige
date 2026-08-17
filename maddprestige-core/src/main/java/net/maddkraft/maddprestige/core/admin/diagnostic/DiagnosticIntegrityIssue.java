package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.Objects;

public record DiagnosticIntegrityIssue(String path, String detail) {
    public DiagnosticIntegrityIssue {
        path = Objects.requireNonNull(path, "path");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
