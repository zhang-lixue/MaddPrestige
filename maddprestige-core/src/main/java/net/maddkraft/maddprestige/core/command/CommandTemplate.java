package net.maddkraft.maddprestige.core.command;

import java.util.Objects;
import java.util.Set;

public record CommandTemplate(String id, String command, Set<String> declaredTokens) {
    public CommandTemplate {
        id = net.maddkraft.maddprestige.api.id.IdentifierRules.requireValid(id, "command template ID");
        command = Objects.requireNonNull(command, "command");
        declaredTokens = Set.copyOf(Objects.requireNonNull(declaredTokens, "declared tokens"));
    }
}
