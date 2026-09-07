package net.maddkraft.maddprestige.core.config.progression;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record ProgressionConfigurationCompilation(
        ProgressionConfiguration configuration,
        ValidationReport validation) {
    public ProgressionConfigurationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
