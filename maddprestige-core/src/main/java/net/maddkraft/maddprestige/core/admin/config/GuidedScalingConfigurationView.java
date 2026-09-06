package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.scaling.SegmentScalingMode;

/** Canonical compiled scaling projection for one selected Prestige level. */
public record GuidedScalingConfigurationView(
        ConfigRevisionId revision,
        long prestigeLevel,
        SegmentScalingMode mode,
        String base,
        String rate,
        String effectiveValue,
        Optional<String> overrideValue,
        Set<GuidedScalingParameter> editableParameters,
        boolean complex,
        boolean overrideManageable) {
    public GuidedScalingConfigurationView(
            ConfigRevisionId revision,
            long prestigeLevel,
            SegmentScalingMode mode,
            String base,
            String rate,
            String effectiveValue,
            Optional<String> overrideValue,
            Set<GuidedScalingParameter> editableParameters,
            boolean complex) {
        this(revision, prestigeLevel, mode, base, rate, effectiveValue, overrideValue,
                editableParameters, complex, false);
    }

    public GuidedScalingConfigurationView {
        revision = Objects.requireNonNull(revision, "revision");
        mode = Objects.requireNonNull(mode, "mode");
        base = Objects.requireNonNull(base, "base");
        rate = Objects.requireNonNull(rate, "rate");
        effectiveValue = Objects.requireNonNull(effectiveValue, "effective value");
        overrideValue = Objects.requireNonNull(overrideValue, "override value");
        editableParameters = Set.copyOf(Objects.requireNonNull(editableParameters, "editable parameters"));
        if (prestigeLevel < 1 || base.isEmpty() || rate.isEmpty() || effectiveValue.isEmpty()) {
            throw new IllegalArgumentException("Scaling view requires a positive level and complete values");
        }
        if (overrideManageable && complex) {
            throw new IllegalArgumentException("Manageable override requires a simple profile");
        }
    }

    public boolean editable(GuidedScalingParameter parameter) {
        return editableParameters.contains(Objects.requireNonNull(parameter, "scaling parameter"));
    }

    public String value(GuidedScalingParameter parameter) {
        return switch (Objects.requireNonNull(parameter, "scaling parameter")) {
            case LINEAR_BASE -> base;
            case LINEAR_INCREMENT -> rate;
        };
    }}
