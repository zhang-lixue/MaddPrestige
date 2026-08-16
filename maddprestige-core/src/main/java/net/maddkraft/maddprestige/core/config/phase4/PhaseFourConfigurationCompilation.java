package net.maddkraft.maddprestige.core.config.phase4;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record PhaseFourConfigurationCompilation(
        PhaseFourConfiguration configuration,
        ValidationReport validation) {
    public PhaseFourConfigurationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
