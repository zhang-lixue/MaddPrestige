package net.maddkraft.maddprestige.integrations.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record PhaseFiveIntegrationCompilation(
        PhaseFiveIntegrationConfiguration configuration,
        ValidationReport validation) {
    public PhaseFiveIntegrationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
