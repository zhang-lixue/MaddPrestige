package net.maddkraft.maddprestige.core.schema;

import java.util.Objects;

public record Deprecation(String alias, String sinceVersion, String replacementPath, String migrationCode) {
    public Deprecation {
        alias = Objects.requireNonNull(alias, "alias");
        sinceVersion = Objects.requireNonNull(sinceVersion, "since version");
        replacementPath = Objects.requireNonNull(replacementPath, "replacement path");
        migrationCode = Objects.requireNonNull(migrationCode, "migration code");
    }
}
