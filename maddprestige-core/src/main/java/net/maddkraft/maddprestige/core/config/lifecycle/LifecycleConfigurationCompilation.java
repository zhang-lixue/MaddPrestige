package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record LifecycleConfigurationCompilation(
        LifecycleConfiguration configuration,
        ValidationReport validation) {
    public LifecycleConfigurationCompilation {
        configuration = Objects.requireNonNull(configuration, "configuration");
        validation = Objects.requireNonNull(validation, "validation");
    }
}
