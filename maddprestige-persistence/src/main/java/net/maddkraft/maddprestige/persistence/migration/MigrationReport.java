package net.maddkraft.maddprestige.persistence.migration;

import java.util.List;

public record MigrationReport(List<MigrationRecord> records, boolean changed) {
    public MigrationReport {
        records = List.copyOf(records);
    }
}
