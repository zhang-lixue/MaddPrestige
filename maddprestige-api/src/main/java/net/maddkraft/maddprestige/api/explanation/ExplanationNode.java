package net.maddkraft.maddprestige.api.explanation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExplanationNode(
        String code,
        ExplanationStatus status,
        String summary,
        Map<String, String> facts,
        List<ExplanationNode> children) {
    public ExplanationNode {
        code = Objects.requireNonNull(code, "code");
        status = Objects.requireNonNull(status, "status");
        summary = Objects.requireNonNull(summary, "summary");
        facts = Map.copyOf(Objects.requireNonNull(facts, "facts"));
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }
}
