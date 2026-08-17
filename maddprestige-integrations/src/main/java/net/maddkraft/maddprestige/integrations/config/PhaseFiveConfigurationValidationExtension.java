package net.maddkraft.maddprestige.integrations.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationValidationExtension;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;

/** Adds the strict Phase 5 integration compiler to the canonical Phase 6 apply path. */
public final class PhaseFiveConfigurationValidationExtension implements ConfigurationValidationExtension {
    private final PhaseFiveIntegrationCompiler compiler;

    public PhaseFiveConfigurationValidationExtension(PhaseFiveIntegrationCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    @Override
    public ValidationReport validate(CompiledConfiguration configuration) {
        String yaml = configuration.documents().get("integrations.yml");
        if (yaml == null) {
            return ValidationReport.of(java.util.List.of(new ValidationFinding(
                    "phase5.integrations.missing", ValidationSeverity.ERROR, "integrations.yml",
                    "The canonical integrations document is missing.",
                    "Integration configuration cannot be activated.",
                    "Restore integrations.yml, even when every optional integration is disabled.")));
        }
        return compiler.compile(yaml).validation();
    }
}
