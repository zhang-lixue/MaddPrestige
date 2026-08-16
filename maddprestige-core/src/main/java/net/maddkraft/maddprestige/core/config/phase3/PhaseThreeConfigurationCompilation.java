package net.maddkraft.maddprestige.core.config.phase3;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record PhaseThreeConfigurationCompilation(
        PhaseThreeConfiguration configuration,
        ValidationReport validation) {
    public PhaseThreeConfigurationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
