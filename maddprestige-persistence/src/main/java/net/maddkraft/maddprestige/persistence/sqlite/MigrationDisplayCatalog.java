package net.maddkraft.maddprestige.persistence.sqlite;

import java.util.Map;

/** Operational descriptions kept outside immutable migration identity material. */
final class MigrationDisplayCatalog {
    private static final Map<Long, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(1L, "Configuration, operation, currency, and audit foundations"),
            Map.entry(2L, "Authoritative player progression state"),
            Map.entry(3L, "Requirement state and durable manual progress"),
            Map.entry(4L, "Prestige lifecycle and provider state"),
            Map.entry(5L, "Stage-history actor provenance"),
            Map.entry(6L, "Durable configuration history and exact revision documents"),
            Map.entry(7L, "Recoverable referenced-stage replacement"),
            Map.entry(8L, "Durable progression-transition fence"),
            Map.entry(9L, "Owned source-and-target progression participation"),
            Map.entry(10L, "Configuration-owned unsafe-progression reservations"),
            Map.entry(11L, "Historical player Prestige-state compatibility"),
            Map.entry(12L, "Numeric Prestige progression authority"));

    private MigrationDisplayCatalog() {
    }

    static String description(long version) {
        String description = DESCRIPTIONS.get(version);
        if (description == null) {
            throw new IllegalArgumentException("Unknown migration version: " + version);
        }
        return description;
    }
}
