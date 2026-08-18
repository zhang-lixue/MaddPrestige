package net.maddkraft.maddprestige.persistence.sqlite;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SqliteValidationResult(
        long schemaVersion,
        Optional<String> activeConfigurationRevision,
        String journalMode,
        Map<String, Long> representativeRows) {
    public SqliteValidationResult {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("Schema version cannot be negative");
        }
        activeConfigurationRevision = Objects.requireNonNull(
                activeConfigurationRevision, "active configuration revision");
        journalMode = Objects.requireNonNull(journalMode, "journal mode");
        representativeRows = Map.copyOf(Objects.requireNonNull(representativeRows, "representative rows"));
    }
}
