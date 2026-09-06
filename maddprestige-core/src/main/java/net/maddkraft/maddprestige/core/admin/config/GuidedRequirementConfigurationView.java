package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

/** Revision-bound, read-mostly view of the selected Prestige level's reachable requirements. */
public record GuidedRequirementConfigurationView(
        ConfigRevisionId revision,
        long prestigeLevel,
        List<GuidedRequirementConfigurationEntry> requirements,
        boolean complex) {
    public GuidedRequirementConfigurationView {
        revision = Objects.requireNonNull(revision, "revision");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        if (prestigeLevel < 1) {
            throw new IllegalArgumentException("Prestige level must be positive");
        }
    }
}
