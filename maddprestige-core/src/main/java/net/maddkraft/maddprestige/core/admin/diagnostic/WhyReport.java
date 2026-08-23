package net.maddkraft.maddprestige.core.admin.diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

public record WhyReport(
        boolean executable,
        List<String> blockers,
        Optional<ExplanationNode> requirementExplanation,
        Optional<ConfigRevisionId> configRevision,
        List<AuthorizationBlocker> authorizationBlockers) {
    public WhyReport {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        requirementExplanation = Objects.requireNonNull(requirementExplanation, "requirement explanation");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        authorizationBlockers = List.copyOf(Objects.requireNonNull(authorizationBlockers,
                "authorization blockers"));
        if (executable && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Executable why result cannot contain blockers");
        }
        if (!authorizationBlockers.isEmpty()
                && !blockers.equals(AuthorizationBlocker.diagnostics(authorizationBlockers))) {
            throw new IllegalArgumentException("Diagnostic and structured why blockers must agree");
        }
    }

    public WhyReport(
            boolean executable,
            List<String> blockers,
            Optional<ExplanationNode> requirementExplanation,
            Optional<ConfigRevisionId> configRevision) {
        this(executable, blockers, requirementExplanation, configRevision,
                AuthorizationBlocker.unknownAll(blockers));
    }
}
