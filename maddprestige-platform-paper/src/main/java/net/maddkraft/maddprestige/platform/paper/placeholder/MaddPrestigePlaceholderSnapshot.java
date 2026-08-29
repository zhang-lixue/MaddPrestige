package net.maddkraft.maddprestige.platform.paper.placeholder;

import java.util.Map;
import java.util.Objects;

/** Immutable values produced by canonical runtime projections outside the placeholder render path. */
public record MaddPrestigePlaceholderSnapshot(
        String currentPrestige,
        String lifetimePrestige,
        String requirementStatus,
        Map<String, String> extras) {
    public MaddPrestigePlaceholderSnapshot {
        currentPrestige = Objects.requireNonNull(currentPrestige, "current prestige");
        lifetimePrestige = Objects.requireNonNull(lifetimePrestige, "lifetime prestige");
        requirementStatus = Objects.requireNonNull(requirementStatus, "requirement status");
        extras = Map.copyOf(Objects.requireNonNull(extras, "extras"));
    }

    public String resolve(String identifier) {
        return switch (identifier) {
            case "current_prestige" -> currentPrestige;
            case "lifetime_prestige" -> lifetimePrestige;
            case "requirement_status" -> requirementStatus;
            default -> extras.get(identifier);
        };
    }
}
