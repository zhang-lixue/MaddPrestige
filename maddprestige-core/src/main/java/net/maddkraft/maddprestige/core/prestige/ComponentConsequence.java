package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import net.maddkraft.maddprestige.core.config.phase4.ResetComponent;
import net.maddkraft.maddprestige.core.config.phase4.ResetDisposition;

public record ComponentConsequence(
        ResetComponent component,
        ResetDisposition disposition,
        String exactEffect) {
    public ComponentConsequence {
        component = Objects.requireNonNull(component, "component");
        disposition = Objects.requireNonNull(disposition, "disposition");
        exactEffect = Objects.requireNonNull(exactEffect, "exact effect");
    }
}
