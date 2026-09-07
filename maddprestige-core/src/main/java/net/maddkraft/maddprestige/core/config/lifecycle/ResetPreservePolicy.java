package net.maddkraft.maddprestige.core.config.lifecycle;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ResetPreservePolicy(Map<ResetComponent, ResetDisposition> dispositions) {
    public ResetPreservePolicy {
        Objects.requireNonNull(dispositions, "dispositions");
        EnumMap<ResetComponent, ResetDisposition> copy = new EnumMap<>(ResetComponent.class);
        copy.putAll(dispositions);
        for (ResetComponent component : ResetComponent.values()) {
            if (!copy.containsKey(component) || copy.get(component) == null) {
                throw new IllegalArgumentException("Reset policy must classify " + component);
            }
        }
        dispositions = Map.copyOf(copy);
    }

    public ResetDisposition disposition(ResetComponent component) {
        return dispositions.get(Objects.requireNonNull(component, "component"));
    }

    public static ResetPreservePolicy safeDefaults() {
        EnumMap<ResetComponent, ResetDisposition> values = new EnumMap<>(ResetComponent.class);
        for (ResetComponent component : ResetComponent.values()) {
            values.put(component, ResetDisposition.PRESERVE);
        }
        values.put(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS, ResetDisposition.RESET);
        values.put(ResetComponent.LATCHED_COMPLETIONS, ResetDisposition.RESET);
        values.put(ResetComponent.BASELINES, ResetDisposition.RESET);
        values.put(ResetComponent.PRESTIGE_SCOPED_CURRENCY, ResetDisposition.RESET);
        return new ResetPreservePolicy(values);
    }
}
