package net.maddkraft.maddprestige.persistence.migration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.RevisionHasher;

public record Migration(long version, ContentHash checksum, String description, List<String> statements) {
    public Migration {
        if (version < 1) {
            throw new IllegalArgumentException("Migration version must be positive");
        }
        checksum = Objects.requireNonNull(checksum, "checksum");
        description = Objects.requireNonNull(description, "description");
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("Migration must contain at least one statement");
        }
    }

    public static Migration of(long version, String description, List<String> statements) {
        String joined = String.join("\n-- statement --\n", statements);
        ContentHash checksum = RevisionHasher.hashDocuments(Map.of(
                "version", Long.toString(version), "description", description, "statements", joined));
        return new Migration(version, checksum, description, statements);
    }
}
