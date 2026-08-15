package net.maddkraft.maddprestige.core.config;

import java.util.Map;
import java.util.Objects;

public record CompiledConfiguration(ContentHash contentHash, Map<String, String> documents) {
    public CompiledConfiguration {
        contentHash = Objects.requireNonNull(contentHash, "content hash");
        documents = Map.copyOf(Objects.requireNonNull(documents, "documents"));
        if (!RevisionHasher.hashDocuments(documents).equals(contentHash)) {
            throw new IllegalArgumentException("Compiled configuration hash does not match its documents");
        }
    }
}
