package net.maddkraft.maddprestige.persistence.migration;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.maddkraft.maddprestige.persistence.PersistenceException;

/** Protocol rules that apply to the complete APPLIED/FAILED migration attempt ledger. */
public final class MigrationHistoryRules {
    private MigrationHistoryRules() {
    }

    public static void validateAttemptOrdering(
            List<MigrationRecord> records, long appliedPrefix, long knownMigrationCount) {
        long nextPending = appliedPrefix + 1;
        Map<Long, Instant> appliedAt = new HashMap<>();
        for (MigrationRecord record : records) {
            if (record.version() < 1 || record.version() > knownMigrationCount) {
                throw new PersistenceException("Database contains unknown migration history version "
                        + record.version());
            }
            if (record.result() == MigrationResult.APPLIED
                    && appliedAt.putIfAbsent(record.version(), record.appliedAt()) != null) {
                throw new PersistenceException("Migration history contains duplicate APPLIED evidence for version "
                        + record.version());
            }
        }
        for (long version = 1; version <= appliedPrefix; version++) {
            if (!appliedAt.containsKey(version)) {
                throw new PersistenceException("Migration history has no APPLIED completion evidence for version "
                        + version);
            }
        }
        for (MigrationRecord record : records) {
            if (record.result() != MigrationResult.FAILED) {
                continue;
            }
            if (record.version() > appliedPrefix
                    && record.version() != nextPending) {
                throw new PersistenceException("FAILED migration attempt at version " + record.version()
                        + " is beyond the next pending migration " + nextPending);
            }
            if (record.version() <= appliedPrefix) {
                Instant completion = appliedAt.get(record.version());
                if (record.appliedAt().isAfter(completion)) {
                    throw new PersistenceException("FAILED migration attempt at version " + record.version()
                            + " is later than its APPLIED completion evidence");
                }
                if (record.version() > 1) {
                    requireNotBeforePriorCompletion(record, appliedAt.get(record.version() - 1));
                }
            } else if (appliedPrefix > 0) {
                requireNotBeforePriorCompletion(record, appliedAt.get(appliedPrefix));
            }
        }
    }

    private static void requireNotBeforePriorCompletion(MigrationRecord failed, Instant priorCompletion) {
        if (failed.appliedAt().isBefore(priorCompletion)) {
            throw new PersistenceException("FAILED migration attempt at version " + failed.version()
                    + " is earlier than APPLIED completion evidence for version " + (failed.version() - 1));
        }
    }
}
