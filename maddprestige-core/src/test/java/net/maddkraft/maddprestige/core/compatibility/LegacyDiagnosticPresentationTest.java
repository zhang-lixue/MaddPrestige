package net.maddkraft.maddprestige.core.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyDiagnosticPresentationTest {
    @Test
    @DisplayName("Archived exact diagnostic aliases render through canonical report presentation")
    void archivedDiagnosticIsCanonicalizedByTheCompatibilityReader() {
        ValidationFinding archived = new ValidationFinding(
                "phase4.scaling.gap", ValidationSeverity.ERROR, "scaling.profile",
                "Historical report", "Historical consequence", "Historical remediation");

        var detail = SemanticPresentation.validationFinding(archived).getFirst();
        var summary = SemanticPresentation.validationFindingSummary(archived);

        assertEquals("scaling.gap", detail.arguments().get("code"));
        assertEquals("scaling.gap", summary.arguments().get("code"));
        assertEquals("command.validation.finding.scaling", summary.key());
    }

    @Test
    @DisplayName("Unsupported phase-like diagnostics are not transformed by report presentation")
    void arbitraryDiagnosticIsNotCanonicalized() {
        ValidationFinding unknown = new ValidationFinding(
                "phase4.scaling.future_condition", ValidationSeverity.ERROR, "scaling.profile",
                "Unknown report", "Unknown consequence", "Unknown remediation");

        assertEquals("phase4.scaling.future_condition",
                SemanticPresentation.validationFindingSummary(unknown).arguments().get("code"));
    }
}
