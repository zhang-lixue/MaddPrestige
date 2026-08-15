package net.maddkraft.maddprestige.core.config;

import java.util.Objects;

public final class ConfigCompiler {
    public CompiledConfiguration compile(ConfigDraft draft) {
        Objects.requireNonNull(draft, "draft");
        return new CompiledConfiguration(RevisionHasher.hashDocuments(draft.documents()), draft.documents());
    }
}
