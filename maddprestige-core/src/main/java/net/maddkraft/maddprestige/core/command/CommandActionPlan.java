package net.maddkraft.maddprestige.core.command;

import java.util.Objects;

public record CommandActionPlan(
        String actionId,
        String templateId,
        String normalizedRoot,
        String command,
        String auditPreview,
        boolean externalUncertaintyPossible) {
    public CommandActionPlan {
        actionId = Objects.requireNonNull(actionId, "action ID");
        templateId = Objects.requireNonNull(templateId, "template ID");
        normalizedRoot = Objects.requireNonNull(normalizedRoot, "normalized root");
        command = Objects.requireNonNull(command, "command");
        auditPreview = Objects.requireNonNull(auditPreview, "audit preview");
    }
}
