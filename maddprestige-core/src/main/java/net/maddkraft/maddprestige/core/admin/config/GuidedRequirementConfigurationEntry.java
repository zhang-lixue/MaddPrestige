package net.maddkraft.maddprestige.core.admin.config;

import java.util.Objects;

/** One reachable requirement in the bounded Staff configuration view. */
public record GuidedRequirementConfigurationEntry(
        String requirementId,
        Kind kind,
        String displayName,
        String currentTarget,
        boolean editable) {
    public GuidedRequirementConfigurationEntry {
        requirementId = Objects.requireNonNull(requirementId, "requirement ID");
        kind = Objects.requireNonNull(kind, "requirement kind");
        displayName = Objects.requireNonNull(displayName, "display name");
        currentTarget = Objects.requireNonNull(currentTarget, "current target");
        if (requirementId.isBlank() || displayName.isBlank() || currentTarget.isBlank()) {
            throw new IllegalArgumentException("Guided requirement fields cannot be blank");
        }
    }

    public enum Kind {
        MONEY,
        TOTAL_SKILL_LEVEL,
        OTHER
    }
}
