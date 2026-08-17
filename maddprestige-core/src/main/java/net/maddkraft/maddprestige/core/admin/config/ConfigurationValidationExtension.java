package net.maddkraft.maddprestige.core.admin.config;

import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;

@FunctionalInterface
public interface ConfigurationValidationExtension {
    ValidationReport validate(CompiledConfiguration configuration);
}
