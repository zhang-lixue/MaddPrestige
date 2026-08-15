package net.maddkraft.maddprestige.core.stage;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record StageConfigurationCompilation(
        Optional<StageConfiguration> configuration,
        ValidationReport validation) {
    public StageConfigurationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
