package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DoctorReport(DiagnosticSeverity status, List<DiagnosticFinding> findings, Instant generatedAt) {
    public DoctorReport {
        status = Objects.requireNonNull(status, "status");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        generatedAt = Objects.requireNonNull(generatedAt, "generated at");
    }
}
