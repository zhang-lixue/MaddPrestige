package net.maddkraft.maddprestige.core.command;

import java.util.Map;
import java.util.Objects;

public record CommandActionRequest(String templateId, Map<String, String> tokenValues) {
    public CommandActionRequest {
        templateId = Objects.requireNonNull(templateId, "template ID");
        tokenValues = Map.copyOf(Objects.requireNonNull(tokenValues, "token values"));
    }
}
