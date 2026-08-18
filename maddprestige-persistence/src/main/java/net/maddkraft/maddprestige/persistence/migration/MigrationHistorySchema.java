package net.maddkraft.maddprestige.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Canonical SQLite migration-history schema shared by migration and independent validation. */
public final class MigrationHistorySchema {
    public static final String TABLE = "mp_schema_migrations";
    public static final String APPLIED_INDEX = "mp_schema_migrations_applied_version_uq";
    private static final String CREATE_HISTORY = """
            CREATE TABLE IF NOT EXISTS mp_schema_migrations (
                attempt_id VARCHAR(36) PRIMARY KEY,
                version BIGINT NOT NULL,
                checksum VARCHAR(64) NOT NULL,
                description VARCHAR(255) NOT NULL,
                applied_at VARCHAR(40) NOT NULL,
                result VARCHAR(16) NOT NULL,
                detail VARCHAR(1024) NOT NULL,
                CHECK (result IN ('APPLIED', 'FAILED'))
            )
            """;
    private static final String CREATE_APPLIED_INDEX = """
            CREATE UNIQUE INDEX mp_schema_migrations_applied_version_uq
            ON mp_schema_migrations(version)
            WHERE result = 'APPLIED'
            """;

    private MigrationHistorySchema() {
    }

    public static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_HISTORY);
            statement.execute("DROP INDEX IF EXISTS " + APPLIED_INDEX);
            statement.execute(CREATE_APPLIED_INDEX);
        }
    }
}
