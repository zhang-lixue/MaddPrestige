package net.maddkraft.maddprestige.persistence.sqlite;

import java.util.List;
import net.maddkraft.maddprestige.persistence.migration.Migration;

/**
 * Purpose-based access to the immutable SQLite migration history.
 *
 * <p>The returned migrations retain their original descriptors and checksums. Current display text
 * is deliberately kept in a separate catalog so presentation changes cannot alter migration
 * identity.</p>
 */
public final class SqliteMigrations {
    private SqliteMigrations() {
    }

    public static List<Migration> throughVersionOne() {
        return HistoricalMigrationCatalog.throughVersionOne();
    }

    public static List<Migration> throughVersionTwo() {
        return HistoricalMigrationCatalog.throughVersionTwo();
    }

    public static List<Migration> throughVersionThree() {
        return HistoricalMigrationCatalog.throughVersionThree();
    }

    public static List<Migration> throughVersionFive() {
        return HistoricalMigrationCatalog.throughVersionFive();
    }

    public static List<Migration> throughVersionTen() {
        return HistoricalMigrationCatalog.throughVersionTen();
    }

    public static List<Migration> throughVersionEleven() {
        return HistoricalMigrationCatalog.throughVersionEleven();
    }

    public static List<Migration> current() {
        return HistoricalMigrationCatalog.current();
    }

    /** Returns changeable operational text that is excluded from the migration checksum. */
    public static String displayDescription(long version) {
        return MigrationDisplayCatalog.description(version);
    }
}
