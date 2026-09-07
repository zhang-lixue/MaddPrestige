package net.maddkraft.maddprestige.integrations.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record IntegrationConfigurationCompilation(
        IntegrationConfiguration configuration,
        ValidationReport validation) {
    public IntegrationConfigurationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
