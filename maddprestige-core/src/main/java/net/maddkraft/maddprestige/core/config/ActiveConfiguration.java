package net.maddkraft.maddprestige.core.config;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;

public record ActiveConfiguration(ConfigRevisionId revisionId, CompiledConfiguration compiled) {
    public ActiveConfiguration {
        revisionId = Objects.requireNonNull(revisionId, "revision ID");
        compiled = Objects.requireNonNull(compiled, "compiled configuration");
    }
}
