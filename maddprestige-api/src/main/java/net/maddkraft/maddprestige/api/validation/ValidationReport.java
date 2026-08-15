package net.maddkraft.maddprestige.api.validation;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ValidationReport(List<ValidationFinding> findings) {
    public static final ValidationReport VALID = new ValidationReport(List.of());

    public ValidationReport {
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    public static ValidationReport of(Collection<ValidationFinding> findings) {
        return new ValidationReport(List.copyOf(findings));
    }

    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == ValidationSeverity.ERROR);
    }

    public boolean canApply(Set<String> acknowledgedCodes) {
        Objects.requireNonNull(acknowledgedCodes, "acknowledged codes");
        return !hasErrors() && findings.stream()
                .filter(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                .allMatch(finding -> acknowledgedCodes.contains(finding.code()));
    }

    public ValidationReport combine(ValidationReport other) {
        Objects.requireNonNull(other, "other validation report");
        ArrayList<ValidationFinding> combined = new ArrayList<>(findings);
        combined.addAll(other.findings());
        return new ValidationReport(combined);
    }
}
