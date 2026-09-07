package net.maddkraft.qualification.migrationrecovery;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/** Creates the sanitized populated schema-10 input used by the real Paper qualification. */
public final class MigrationFixtureDowngrader {
    private static final UUID LEGACY_PLAYER = UUID.fromString("8c000000-0000-0000-0000-000000000001");
    private static final UUID UNCERTAIN_OPERATION = UUID.fromString("8c000000-0000-0000-0000-000000000002");
    private static final UUID RECOVERY_EVENT = UUID.fromString("8c000000-0000-0000-0000-000000000003");
    private static final UUID UNCERTAIN_TARGET = UUID.fromString("8c000000-0000-0000-0000-000000000004");
    private static final Instant FIXTURE_TIME = Instant.parse("2026-08-17T20:30:00Z");

    private MigrationFixtureDowngrader() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: MigrationFixtureDowngrader <database> <migration-marker>");
        }
        Path database = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path marker = Path.of(arguments[1]).toAbsolutePath().normalize();
        Properties properties = new Properties();
        if (Files.isRegularFile(marker)) {
            try (InputStream input = Files.newInputStream(marker)) {
                properties.load(input);
            }
        }
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.setAutoCommit(false);
            require(scalarLong(connection, "SELECT MAX(version) FROM mp_schema_migrations "
                    + "WHERE result='APPLIED'") == 11, "fixture source was not schema 11");
            require(scalarLong(connection, "SELECT COUNT(*) FROM mp_schema_migrations "
                    + "WHERE version > 11 AND result='APPLIED'") == 0, "fixture source contains later schema");
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM mp_schema_migrations WHERE version=11")) {
                statement.executeUpdate();
            }
            removePriorFixture(connection);
            String revision = properties.getProperty("revision");
            if (revision == null || revision.isBlank()) {
                revision = scalarText(connection, "SELECT revision_id FROM mp_configuration_revisions_v2 "
                        + "WHERE application_status='APPLIED' ORDER BY applied_at DESC, revision_id DESC LIMIT 1");
                properties.setProperty("revision", revision);
            }
            if (properties.getProperty("player") == null) {
                properties.setProperty("player", scalarText(connection,
                        "SELECT stage.player_uuid FROM mp_player_stage_state stage "
                                + "JOIN mp_player_prestige_state prestige "
                                + "ON prestige.player_uuid=stage.player_uuid "
                                + "WHERE prestige.current_prestige=1 AND prestige.lifetime_prestige=1 "
                                + "ORDER BY stage.player_uuid LIMIT 1"));
            }
            insertStageOnlyPlayer(connection, revision);
            insertUncertaintyEvidence(connection, revision);
            require("ok".equals(scalarText(connection, "PRAGMA integrity_check")),
                    "historical fixture integrity_check failed");
            connection.commit();
        }
        properties.setProperty("migration-recovery.legacyPlayer", LEGACY_PLAYER.toString());
        properties.setProperty("migration-recovery.uncertainOperation", UNCERTAIN_OPERATION.toString());
        properties.setProperty("migration-recovery.sourceSchema", "10");
        properties.remove("migration-recovery.completed");
        try (OutputStream output = Files.newOutputStream(marker)) {
            properties.store(output, "Sanitized migration/recovery historical fixture authority");
        }
        System.out.println("MIGRATION-RECOVERY-FIXTURE PASS schema=10 legacy-player=" + LEGACY_PLAYER
                + " uncertainty=" + UNCERTAIN_OPERATION);
    }

    private static void insertStageOnlyPlayer(Connection connection, String revision) throws Exception {
        String sql = "INSERT INTO mp_player_stage_state (player_uuid, stage_id, state_revision, "
                + "config_revision_id, stage_entered_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LEGACY_PLAYER.toString());
            statement.setString(2, "novice");
            statement.setLong(3, 17);
            statement.setString(4, revision);
            statement.setString(5, FIXTURE_TIME.toString());
            statement.setString(6, FIXTURE_TIME.toString());
            statement.setString(7, FIXTURE_TIME.toString());
            require(statement.executeUpdate() == 1, "legacy stage-only player was not inserted");
        }
    }

    private static void removePriorFixture(Connection connection) throws Exception {
        executeDelete(connection, "DELETE FROM mp_recovery_events WHERE operation_id=?", UNCERTAIN_OPERATION);
        executeDelete(connection, "DELETE FROM mp_operation_actions WHERE operation_id=?", UNCERTAIN_OPERATION);
        executeDelete(connection, "DELETE FROM mp_operations WHERE operation_id=?", UNCERTAIN_OPERATION);
        executeDelete(connection, "DELETE FROM mp_player_prestige_state WHERE player_uuid=?", LEGACY_PLAYER);
        executeDelete(connection, "DELETE FROM mp_player_stage_state WHERE player_uuid=?", LEGACY_PLAYER);
    }

    private static void executeDelete(Connection connection, String sql, UUID identity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity.toString());
            statement.executeUpdate();
        }
    }

    private static void insertUncertaintyEvidence(Connection connection, String revision) throws Exception {
        String operation = "INSERT INTO mp_operations (operation_id, operation_type, target_uuid, idempotency_key, "
                + "state, expected_state_revision, config_revision_id, provider_generations, redacted_preview, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(operation)) {
            statement.setString(1, UNCERTAIN_OPERATION.toString());
            statement.setString(2, "qualification_uncertainty");
            statement.setString(3, UNCERTAIN_TARGET.toString());
            statement.setString(4, "migration-recovery-uncertainty-request");
            statement.setString(5, "NEEDS_RECONCILIATION");
            statement.setLong(6, 17);
            statement.setString(7, revision);
            statement.setString(8, "migration_recovery_harness:external=1");
            statement.setString(9, "sanitized uncertainty fixture");
            statement.setString(10, FIXTURE_TIME.toString());
            statement.setString(11, FIXTURE_TIME.toString());
            statement.executeUpdate();
        }
        String action = "INSERT INTO mp_operation_actions (operation_id, action_index, action_id, provider_id, "
                + "action_type, state, redacted_description, reversible, idempotent, updated_at) "
                + "VALUES (?, 0, ?, ?, ?, 'UNCERTAIN', ?, 0, 0, ?)";
        try (PreparedStatement statement = connection.prepareStatement(action)) {
            statement.setString(1, UNCERTAIN_OPERATION.toString());
            statement.setString(2, "external-uncertain");
            statement.setString(3, "migration_recovery_harness:external");
            statement.setString(4, "qualification");
            statement.setString(5, "sanitized uncertainty");
            statement.setString(6, FIXTURE_TIME.toString());
            statement.executeUpdate();
        }
        String recovery = "INSERT INTO mp_recovery_events (recovery_id, operation_id, previous_state, "
                + "resulting_state, decision, detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(recovery)) {
            statement.setString(1, RECOVERY_EVENT.toString());
            statement.setString(2, UNCERTAIN_OPERATION.toString());
            statement.setString(3, "EXECUTING");
            statement.setString(4, "NEEDS_RECONCILIATION");
            statement.setString(5, "PRESERVE_EXTERNAL_UNCERTAINTY");
            statement.setString(6, "sanitized qualification evidence");
            statement.setString(7, FIXTURE_TIME.toString());
            statement.executeUpdate();
        }
    }

    private static long scalarLong(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            require(row.next(), "scalar query returned no row");
            return row.getLong(1);
        }
    }

    private static String scalarText(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            require(row.next(), "scalar query returned no row");
            return row.getString(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
