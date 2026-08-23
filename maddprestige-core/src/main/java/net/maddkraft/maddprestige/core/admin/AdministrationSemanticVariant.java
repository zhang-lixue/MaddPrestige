package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;

/**
 * Stable typed discriminator for public administration codes whose production occurrences do not all share one
 * semantic contract. The diagnostic code remains stable; this value selects the exact consequence and remediation.
 */
public enum AdministrationSemanticVariant {
    CONFIG_APPLY_PRIOR_STATE_UNCHANGED("config.apply.failed"),
    CONFIG_APPLY_PRIOR_STATE_RESTORED("config.apply.failed"),
    CONFIG_APPLY_RECONCILIATION_REQUIRED("config.apply.failed"),
    CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION("config.validation.blocked"),
    CONFIG_VALIDATION_APPLY("config.validation.blocked");

    private final String diagnosticCode;

    AdministrationSemanticVariant(String diagnosticCode) {
        this.diagnosticCode = Objects.requireNonNull(diagnosticCode, "diagnostic code");
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }
}
