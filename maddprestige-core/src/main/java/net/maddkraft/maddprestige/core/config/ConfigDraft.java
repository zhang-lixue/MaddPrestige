package net.maddkraft.maddprestige.core.config;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.operation.Actor;

public record ConfigDraft(
        UUID draftId,
        Optional<ConfigRevisionId> baseRevision,
        Map<String, String> documents,
        Actor actor,
        Instant createdAt) {
    public ConfigDraft {
        draftId = Objects.requireNonNull(draftId, "draft ID");
        baseRevision = Objects.requireNonNull(baseRevision, "base revision");
        documents = Map.copyOf(Objects.requireNonNull(documents, "documents"));
        actor = Objects.requireNonNull(actor, "actor");
        createdAt = Objects.requireNonNull(createdAt, "created at");
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("A configuration draft must contain at least one document");
        }
        documents.keySet().forEach(ConfigDraft::validateDocumentName);
    }

    private static void validateDocumentName(String name) {
        if (!name.matches("[a-z0-9][a-z0-9_/-]*\\.yml") || name.contains("..") || name.startsWith("/")) {
            throw new IllegalArgumentException("Unsafe configuration document name: " + name);
        }
    }
}
