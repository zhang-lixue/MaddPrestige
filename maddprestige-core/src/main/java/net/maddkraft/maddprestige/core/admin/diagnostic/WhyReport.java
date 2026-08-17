package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record WhyReport(
        boolean executable,
        List<String> blockers,
        Optional<ExplanationNode> requirementExplanation,
        Optional<ConfigRevisionId> configRevision) {
    public WhyReport {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        requirementExplanation = Objects.requireNonNull(requirementExplanation, "requirement explanation");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        if (executable && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Executable why result cannot contain blockers");
        }
    }
}
