package net.maddkraft.maddprestige.core.config;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.schema.ReloadBehavior;
import net.maddkraft.maddprestige.core.schema.RiskLevel;

public record SemanticDiffEntry(
        String path,
        DiffKind kind,
        Optional<String> redactedOldValue,
        Optional<String> redactedNewValue,
        RiskLevel risk,
        ReloadBehavior reloadBehavior) {
    public SemanticDiffEntry {
        path = Objects.requireNonNull(path, "path");
        kind = Objects.requireNonNull(kind, "kind");
        redactedOldValue = Objects.requireNonNull(redactedOldValue, "old value");
        redactedNewValue = Objects.requireNonNull(redactedNewValue, "new value");
        risk = Objects.requireNonNull(risk, "risk");
        reloadBehavior = Objects.requireNonNull(reloadBehavior, "reload behavior");
    }
}
