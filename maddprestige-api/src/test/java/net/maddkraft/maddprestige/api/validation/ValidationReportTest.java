package net.maddkraft.maddprestige.api.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationReportTest {
    @Test
    @DisplayName("[A40] High-risk warnings require explicit acknowledgement")
    void enforcesSeveritySemantics() {
        ValidationFinding finding = new ValidationFinding("risk.commands", ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED,
                "safety.external-commands.enabled", "External commands can mutate unrelated systems.",
                "The action may not be reversible.", "Review and acknowledge the exact command policy.");
        ValidationReport report = new ValidationReport(List.of(finding));
        assertFalse(report.canApply(Set.of()));
        assertTrue(report.canApply(Set.of("risk.commands")));
    }
}
